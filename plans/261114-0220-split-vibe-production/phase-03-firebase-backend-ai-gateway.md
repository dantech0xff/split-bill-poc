---
title: "Phase 3: Backend Firebase và cổng AI"
status: todo
phase: 3
priority: P1
effort: "10d"
dependencies: [1]
---

# Phase 3: Backend Firebase và cổng AI

## Overview

App chạy được ở chế độ production mà không có API key nào trên máy: người dùng đăng nhập ẩn danh ngay lần mở đầu, hóa đơn được phân tích qua một cổng AI trên server có hạn mức theo người dùng, dữ liệu nằm trên Firestore có rules, và mọi lần gọi AI đều đo được chi phí.

## Context

- Hiện tại key Anthropic nằm trên máy: `app/build.gradle.kts:30` và `:35` nhúng `DEFAULT_ANTHROPIC_API_KEY` vào bản debug, `app/src/main/java/com/dantech/splitvibe/data/settings/SettingsStore.kt:34-36` lưu key người dùng nhập, và `app/src/main/java/com/dantech/splitvibe/llm/ClaudeBillAnalyzer.kt` gọi thẳng API từ điện thoại. Cách này chỉ chấp nhận được cho POC.
- Giao kèo `BillAnalyzer` (`app/src/main/java/com/dantech/splitvibe/llm/BillAnalyzer.kt`) đã tách sẵn phần gọi model khỏi giao diện, nên chỉ cần thêm một bản cài đặt mới.
- Dữ kiện nghiên cứu: [báo cáo backend](../reports/researcher-261114-0209-product-backend-and-payment-links.md), mục 4.
- Việc còn treo từ POC: yêu cầu thật của app chưa từng được API chấp nhận (mới chứng minh được đường "key bị từ chối"). Giai đoạn này đóng khoảng trống đó bằng một lần gọi thật qua cổng.

## Key Insights

- Dùng **callable function thế hệ 2** thay vì Cloud Run trần: nó tự kiểm token đăng nhập và App Check, hỗ trợ stream bằng `sendChunk`. Thời gian chờ mặc định phía client là 70 giây, phải nâng lên.
- Bắt buộc gói **Blaze** (Functions và Secret Manager cần). Cảnh báo ngân sách chỉ báo chứ không chặn, nên trần chi phí thật là tài liệu hạn mức và `maxInstances`.
- **Vị trí Firestore không đổi được** sau khi tạo. Đây là quyết định một lần (xem Validation Log trong `plan.md`).
- Transaction của Firestore thất bại khi mất mạng, còn ghi thường và ghi theo lô thì xếp hàng chờ. Vì vậy client chỉ ghi thường hoặc theo lô; transaction chỉ dùng trên server.
- Ước lượng chi phí mỗi lần đọc hóa đơn (khoảng 5 nghìn token vào, 2 nghìn token ra kể cả phần suy nghĩ, theo bảng giá hiện hành): Opus 5.5 khoảng 0,06 USD, Sonnet 5.5 khoảng 0,03 USD, Haiku 5.5 khoảng 0,002 USD. Đây là ước lượng `[UNVERIFIED]`, bộ đánh giá bên dưới sẽ đo số thật.

## Requirements

Chức năng:
- Đăng nhập ẩn danh tự động, nâng cấp lên tài khoản Google qua Credential Manager mà giữ nguyên dữ liệu, đăng xuất, xóa tài khoản.
- Hàm `analyzeBill` nhận văn bản OCR và câu lệnh, trả đúng JSON mà `BillAnalysisParser` đang đọc.
- Hạn mức theo người dùng do server giữ; lần gọi thất bại không bị trừ lượt.
- Bộ đánh giá chất lượng để chọn model bằng số liệu.

Phi chức năng:
- Bản release không chứa key Anthropic và không chứa lớp nào của Anthropic SDK.
- Log của server không chứa nội dung hóa đơn hay câu lệnh của người dùng.
- Mọi lần gọi ghi lại model, số token, độ trễ và kết quả để tính chi phí.

## Architecture

```
Điện thoại                                   Firebase / Google Cloud
 ML Kit OCR (trên máy)
 GatewayBillAnalyzer ── callable + stream ─▶ analyzeBill (Functions gen 2, TypeScript)
   kèm token đăng nhập + App Check             1. kiểm đăng nhập và App Check (enforce)
                                               2. transaction hạn mức: usage/{uid}/periods/{yyyymm}
                                               3. gọi Anthropic (key trong Secret Manager,
                                                  structured output theo schema hiện có)
                                               4. log: model, token, độ trễ (không có nội dung)
 Firestore SDK (bộ đệm offline) ───────────▶ Firestore: users, bills, usage + rules
 Crashlytics, Analytics, Remote Config, App Check
```

Giao kèo `analyzeBill` phiên bản 1:
- Yêu cầu: `schemaVersion`, `ocrDocument` (tối đa 20.000 ký tự), `instruction` (tối đa 1.000 ký tự), `localCurrency`, `locale`.
- Trả lời: `schemaVersion`, `answer` (đúng JSON của `BillSchema` hiện nay), `usage` (số lượt còn lại và kỳ hạn mức).
- Lỗi: ánh xạ vào `BillAnalyzerException.Kind`, thêm hai loại mới là hết hạn mức và chưa xác thực.

Prompt (`app/src/main/java/com/dantech/splitvibe/llm/BillPrompt.kt`) và schema (`app/src/main/java/com/dantech/splitvibe/llm/BillSchema.kt`) chuyển lên server để sửa được mà không cần phát hành app. `BillAnalysisParser` ở lại app và không đổi, nên 14 test của nó tiếp tục bảo vệ giao kèo.

Mô hình dữ liệu khởi đầu (các giai đoạn sau mở rộng):
- `users/{uid}`: tên hiển thị, tiền tệ mặc định, ngôn ngữ, `plan` (chỉ server ghi).
- `usage/{uid}/periods/{yyyymm}`: số lượt AI đã dùng (chỉ server ghi).
- `bills/{billId}`: chủ sở hữu, `memberUids`, hóa đơn (món, phụ phí, tổng theo đơn vị nhỏ nhất dạng số nguyên, mã tiền tệ, số chữ số thập phân), người tham gia, cách chia, phần mỗi người, trạng thái, `schemaVersion`.

Vết mở rộng (ranh giới `BillAnalyzer`): biến thể kế tiếp có thật là thao tác "chỉnh bằng lời" ở Phase 7 và việc đổi model phía server. Phần giữ ổn định là JSON trả lời và chữ ký `analyze(ocr, instruction, localCurrency)`. Phần phải đổi chỉ là lớp cài đặt và các loại lỗi. Bằng chứng: `SplitFlowTest` chạy nguyên vẹn với một `BillAnalyzer` giả, và test giao kèo của `GatewayBillAnalyzer` chạy với bộ fixture đã ghi.

## Files

Tạo mới:
- `functions/` (`package.json`, `tsconfig.json`, `src/index.ts`, `src/analyze-bill.ts`, `src/prompt.ts`, `src/schema.ts`, `src/quota.ts`, `src/delete-account.ts`, `test/`)
- `functions/eval/` (bộ chạy đánh giá và thư mục hóa đơn mẫu)
- `firebase.json`, `.firebaserc`, `firestore.rules`, `firestore.indexes.json`, `firestore-tests/`
- `app/src/main/java/com/dantech/splitvibe/llm/GatewayBillAnalyzer.kt`
- `app/src/main/java/com/dantech/splitvibe/data/auth/AuthRepository.kt`
- `app/src/main/java/com/dantech/splitvibe/data/bills/BillRepository.kt`

Sửa:
- `app/build.gradle.kts`, `gradle/libs.versions.toml` (Firebase BoM, plugin google-services và crashlytics, bỏ `DEFAULT_ANTHROPIC_API_KEY`)
- `app/src/main/java/com/dantech/splitvibe/AppContainer.kt` hoặc module DI thay thế nó sau Phase 1
- `app/src/main/java/com/dantech/splitvibe/data/settings/SettingsStore.kt`, `app/src/main/java/com/dantech/splitvibe/ui/settings/SettingsScreen.kt` (bỏ ô nhập key ở bản release)
- `.gitignore` (thêm `google-services.json`, vì repo đang công khai)

Chuyển chỗ:
- `app/src/main/java/com/dantech/splitvibe/llm/ClaudeBillAnalyzer.kt` và test của nó sang source set `debug`, chỉ để phát triển.

## Implementation Steps

1. Tạo hai project Firebase (dev và prod), bật Blaze, đặt cảnh báo ngân sách, chọn vị trí Firestore theo quyết định đã chốt, đăng ký app `com.dantech.splitvibe` cùng dấu vân tay SHA.
2. Dựng `functions/` bằng TypeScript, viết `analyzeBill`: kiểm đầu vào, transaction hạn mức, gọi Anthropic qua SDK chính thức với structured output, hoàn lượt khi lỗi, log có cấu trúc.
3. Chuyển prompt và schema từ Kotlin sang TypeScript; dùng chung thư mục fixture `app/src/sharedTest/resources/fixtures/` cho test của cả hai phía.
4. Viết `GatewayBillAnalyzer` (callable có stream, thời gian chờ 120 giây), ánh xạ lỗi, chuyển `ClaudeBillAnalyzer` sang `debug`.
5. Đăng nhập ẩn danh lúc khởi động, liên kết Google bằng `linkWithCredential`, xử lý trùng tài khoản, hàm `deleteAccount` xóa dữ liệu rồi xóa người dùng.
6. Viết `firestore.rules` và test rules trên emulator; `BillRepository` chỉ ghi thường hoặc theo lô.
7. Bật App Check (Play Integrity cho release, debug provider cho máy phát triển và CI); theo dõi tỷ lệ hợp lệ rồi mới bật chế độ bắt buộc.
8. Thêm Crashlytics, Analytics (mặc định từ chối lưu trữ ở EEA, Anh, Thụy Sĩ cho tới khi có đồng ý), Remote Config (model, hạn mức, công tắc tắt AI).
9. Dựng bộ đánh giá: ít nhất 30 hóa đơn mẫu, chạy từng model ứng viên, xuất báo cáo độ chính xác, độ trễ và chi phí vào `plans/reports/`.
10. Triển khai lên dev, chạy một lần gọi thật từ app, rồi triển khai prod.

## Todo

- [ ] Hai project Firebase, Blaze, cảnh báo ngân sách, vị trí Firestore đã chốt
- [ ] `analyzeBill` có kiểm đầu vào, hạn mức, structured output, hoàn lượt khi lỗi
- [ ] Prompt và schema chạy trên server, fixture dùng chung
- [ ] `GatewayBillAnalyzer` thay `ClaudeBillAnalyzer` ở bản release
- [ ] Đăng nhập ẩn danh, liên kết Google, xóa tài khoản
- [ ] `firestore.rules` cùng test trên emulator
- [ ] App Check ở chế độ bắt buộc trên prod
- [ ] Crashlytics, Analytics có chế độ đồng ý, Remote Config
- [ ] Bộ đánh giá và báo cáo chọn model
- [ ] Lần gọi thật từ app qua cổng thành công trên dev và prod

## Success Criteria

- Bản release không có key và không có lớp `com.anthropic`: kiểm bằng cách quét các tệp dex trong AAB, kết quả phải là 0.
- Test giao kèo của `analyzeBill` trên emulator qua hết với bộ fixture; app nhận lại đúng hóa đơn mẫu (tổng 902.664 ₫, 7 món).
- Một lần gọi thật từ app qua cổng trên project dev trả về hóa đơn đúng. Mục "chưa kiểm chứng" của POC được đóng lại và README được cập nhật.
- Lượt gọi thứ N+1 trong kỳ trả `resource-exhausted` và app hiện thông báo hết lượt; một lần gọi lỗi không làm giảm số lượt.
- Test rules: người lạ không đọc được `bills` của người khác, client không ghi được `usage` và `users.plan`, chưa đăng nhập thì bị từ chối.
- Yêu cầu không có App Check hợp lệ bị từ chối trên prod.
- Báo cáo đánh giá nêu model được chọn, độ chính xác trên bộ mẫu, độ trễ p50 và p95, chi phí trung bình mỗi lần đọc.

## Risks

| Rủi ro | Dấu hiệu nhận biết | Phản ứng đã định |
|---|---|---|
| Model rẻ đọc sai hóa đơn khó | Bộ đánh giá: tỷ lệ khớp tổng dưới ngưỡng | Dùng model cao hơn làm mặc định hoặc leo thang khi phép đối chiếu trên máy báo lệch |
| Chi phí AI vượt dự tính | Bảng chi phí mỗi ngày vượt ngân sách | Hạ hạn mức miễn phí qua Remote Config, bật công tắc tắt AI, app vẫn nhập tay được |
| App Check chặn nhầm người dùng thật | Tỷ lệ yêu cầu không hợp lệ cao khi còn ở chế độ theo dõi | Chưa bật bắt buộc, điều tra theo phiên bản app và loại máy |
| Chọn sai vị trí Firestore | Độ trễ ghi cao ở thị trường chính | Không đổi được: phải tạo database mới và di chuyển dữ liệu, nên chốt kỹ trước bước 1 |
| Trùng tài khoản khi liên kết Google | `FirebaseAuthUserCollisionException` | Đăng nhập vào tài khoản cũ và gộp dữ liệu ẩn danh trên server |

## Security

- Key Anthropic chỉ nằm trong Secret Manager, gắn bằng `defineSecret`, đọc lúc chạy.
- Văn bản hóa đơn là dữ liệu không tin cậy: prompt đã coi nó là dữ liệu chứ không phải lệnh, đầu ra bị ràng bằng schema, và mọi phép tính tiền vẫn chạy trên máy.
- Giới hạn kích thước đầu vào, `maxInstances`, hạn mức theo người dùng và tài liệu ngân sách toàn cục chống việc đốt tiền API.
- `google-services.json` không đưa vào repo công khai; CI lấy từ secret.
- Không log nội dung hóa đơn, câu lệnh hay tên người.

## Next

Phase 4 dùng `BillRepository` và đăng nhập ẩn danh để lưu lịch sử và bản nháp. Phase 8 đọc `users.plan` để đổi hạn mức.
