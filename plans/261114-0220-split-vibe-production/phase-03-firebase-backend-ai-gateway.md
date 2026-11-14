---
title: "Phase 3: Backend Firebase và cổng AI"
status: todo
phase: 3
priority: P1
effort: "13d"
dependencies: [1]
---

# Phase 3: Backend Firebase và cổng AI

## Overview

App chạy được ở chế độ production mà không có API key nào trên máy: người dùng đăng nhập ẩn danh ngay lần mở đầu, hóa đơn được phân tích qua một cổng AI trên server có hạn mức không lách được bằng cách cài lại app, dữ liệu nằm trên Firestore có rules, và mọi lần gọi AI đều đo được chi phí.

## Context

- Hiện tại key Anthropic nằm trên máy: `app/build.gradle.kts:30` và `:35` nhúng `DEFAULT_ANTHROPIC_API_KEY` vào bản debug, `app/src/main/java/com/dantech/splitvibe/data/settings/SettingsStore.kt` mã hóa và lưu key người dùng nhập, và `app/src/main/java/com/dantech/splitvibe/llm/ClaudeBillAnalyzer.kt` gọi thẳng API từ điện thoại. Cách này chỉ chấp nhận được cho POC.
- Luồng phân tích hiện bị chặn khi không có key ở 4 nơi: `ui/split/SplitBillViewModel.kt:107`, `ui/split/NewSplitContent.kt:110`, `ui/split/SplitScreen.kt:17` và `ui/AppNavHost.kt:43` (đều dưới `app/src/main/java/com/dantech/splitvibe/`). Bộ phân tích được lấy qua `analyzerFor(AppSettings)` và được lưu đệm theo key và model (hiện ở `AppContainer.kt:24-38`; sau Phase 1 phần này nằm trong module Koin).
- Dữ kiện nghiên cứu: [báo cáo backend](../reports/researcher-261114-0209-product-backend-and-payment-links.md), mục 4.
- Việc còn treo từ POC: yêu cầu thật của app chưa từng được API chấp nhận. Giai đoạn này đóng khoảng trống đó bằng một lần gọi thật qua cổng.

## Key Insights

- Dùng **callable function thế hệ 2** thay vì Cloud Run trần: nó tự kiểm token đăng nhập và App Check, hỗ trợ stream bằng `sendChunk`.
- Bắt buộc gói **Blaze**. Cảnh báo ngân sách chỉ báo chứ không chặn, nên trần chi phí thật phải nằm **trong hàm**: hạn mức theo người dùng cộng một cầu dao chi tiêu toàn cục.
- <!-- Updated: Validation Session 1 - vị trí Firestore --> **Vị trí Firestore là `nam5`** (Mỹ, đa vùng), các hàm đặt ở `us-central1`. Quyết định này không đổi được sau khi tạo database.
- Transaction của Firestore thất bại khi mất mạng, còn ghi thường thì xếp hàng chờ. Client chỉ ghi thường hoặc theo lô; transaction chỉ dùng trên server.
- Tài khoản ẩn danh là miễn phí và tạo lại được bằng cách xóa dữ liệu app, nên hạn mức gắn vào `uid` ẩn danh không phải là trần. Vì vậy tài khoản ẩn danh chỉ có một số lượt dùng thử nhỏ; hạn mức hằng tháng chỉ dành cho tài khoản đã liên kết Google.
- Ước lượng chi phí mỗi lần đọc hóa đơn (khoảng 5 nghìn token vào, 2 nghìn token ra kể cả phần suy nghĩ, theo bảng giá hiện hành): Opus 5.5 khoảng 0,06 USD, Sonnet 5.5 khoảng 0,03 USD, Haiku 5.5 khoảng 0,002 USD. Đây là ước lượng `[UNVERIFIED]`, bộ đánh giá bên dưới sẽ đo số thật.

## Requirements

Chức năng:
- Đăng nhập ẩn danh tự động, nâng cấp lên tài khoản Google qua Credential Manager mà giữ nguyên dữ liệu, gộp an toàn khi tài khoản Google đã tồn tại, đăng xuất, xóa tài khoản.
- Hàm `analyzeBill` nhận văn bản OCR và câu lệnh, trả đúng JSON mà `BillAnalysisParser` đang đọc.
- Bộ đánh giá chất lượng để chọn model bằng số liệu: dùng model rẻ nhất đạt ngưỡng, leo thang khi phép đối chiếu trên máy báo lệch.

<!-- Updated: Validation Session 1 - ranh giới miễn phí và Pro -->
Hạn mức lượt AI (giá trị mặc định, đổi được trong cấu hình trên server):

| Loại tài khoản | Hạn mức | Chu kỳ |
|---|---|---|
| Ẩn danh | 3 lượt dùng thử | Một lần cho cả đời tài khoản ẩn danh |
| Đã liên kết Google | 10 lượt | Mỗi tháng, tính theo giờ UTC |
| Pro (Phase 8) | 300 lượt, theo nguyên tắc dùng hợp lý | Mỗi tháng, tính theo giờ UTC |

<!-- Red Team: hạn mức lách được và trừ, hoàn lượt không an toàn — 2026-11-14 -->
Quy tắc tính lượt:
- Mỗi lần phân tích mang một `requestId` do client sinh. Server **giữ chỗ** một lượt theo `requestId` trước khi gọi model và **chốt** khi model đã trả về kết quả.
- Một lượt bị tính khi model đã sinh đầu ra, kể cả khi câu trả lời là "không phải hóa đơn" hoặc client đã ngắt kết nối. Lượt chỉ được nhả khi lỗi xảy ra trước lúc model tính tiền (lỗi mạng, lỗi 5xx hoặc 429 không kèm số token).
- Gọi lại với cùng `requestId` không bị tính thêm lượt; mỗi `requestId` được chạy lại tối đa 2 lần.
- Chỗ giữ quá 5 phút chưa chốt thì tự hết hạn và coi như đã nhả, để một hàm bị hủy giữa chừng không làm mất lượt của người dùng.

Phi chức năng:
- App không chứa key Anthropic và không chứa lớp nào của Anthropic SDK, ở mọi biến thể build.
- Log của server không chứa nội dung hóa đơn hay câu lệnh của người dùng.
- Mọi lần gọi ghi lại model, số token, độ trễ và kết quả để tính chi phí.

## Architecture

```
Điện thoại                                   Firebase / Google Cloud (us-central1, Firestore nam5)
 ML Kit OCR (trên máy)
 GatewayBillAnalyzer ── callable + stream ─▶ analyzeBill (Functions gen 2, TypeScript, timeout 180 giây)
   requestId, token đăng nhập,                 1. kiểm đăng nhập; App Check bắt buộc, token dùng một lần,
   token App Check dùng một lần                   chỉ nhận app Android trong danh sách cho phép
   (hạn chờ phía client 150 giây)              2. cầu dao chi tiêu toàn cục, rồi giữ chỗ lượt theo requestId
                                               3. gọi Anthropic (key trong Secret Manager, structured output,
                                                  có giới hạn max_tokens, hủy khi client ngắt)
                                               4. chốt hoặc nhả lượt; log model, token, độ trễ (không có nội dung)
 Firestore SDK (bộ đệm offline) ───────────▶ Firestore: users, bills, usage, entitlements + rules
 Crashlytics, Analytics, Remote Config, App Check
```

Giao kèo `analyzeBill` phiên bản 1:
- Yêu cầu: `schemaVersion`, `requestId`, `ocrDocument` (tối đa 20.000 ký tự), `instruction` (tối đa 1.000 ký tự), `localCurrency`, `locale`.
- Trả lời: `schemaVersion`, `answer` (đúng JSON của `BillSchema` hiện nay), `usage` (số lượt còn lại và kỳ hạn mức).
- Lỗi: ánh xạ vào `BillAnalyzerException.Kind`, thêm hai loại mới là hết hạn mức và chưa xác thực.

Prompt (`app/src/main/java/com/dantech/splitvibe/llm/BillPrompt.kt`) và schema (`app/src/main/java/com/dantech/splitvibe/llm/BillSchema.kt`) chuyển lên server để sửa được mà không cần phát hành app. `BillAnalysisParser` nằm trong `:core:domain` từ Phase 1 và không đổi, nên 14 test của nó tiếp tục bảo vệ giao kèo.

<!-- Red Team: bản release không phân tích được sau khi bỏ key — 2026-11-14 -->
Thay cổng chặn `hasApiKey` bằng trạng thái "AI khả dụng": đã đăng nhập (ẩn danh cũng được), công tắc tắt AI đang mở, và còn lượt. `AppSettings` chỉ còn tiền tệ mặc định; model do server chọn. `ClaudeBillAnalyzer`, các test của nó và thư viện Anthropic Java bị **xóa khỏi app**. Phương án giữ chúng trong source set `debug` đã bị loại, vì chính nó gây ra lỗi "bản debug chạy được còn bản release thì không".

Mô hình dữ liệu khởi đầu (các giai đoạn sau mở rộng):
- `users/{uid}`: tên hiển thị, `defaultCurrency`, ngôn ngữ. Đây là nơi duy nhất giữ tiền tệ mặc định; giá trị `local_currency` trong SharedPreferences được chuyển sang một lần rồi bỏ.
- `entitlements/{uid}`: gói và hạn dùng, chỉ server ghi, chủ tài khoản đọc được. Tách khỏi `users` để rules không phải bảo vệ từng trường.
- `usage/{uid}`: `trialUsed`; `usage/{uid}/periods/{yyyymm}`: số lượt đã chốt trong tháng (UTC); `usage/{uid}/requests/{requestId}`: chỗ giữ, trạng thái, hạn. Tất cả chỉ server ghi.
- `budget/{yyyymmdd}/shards/{n}`: bộ đếm chi tiêu toàn cục chia mảnh, để không dồn mọi lần gọi vào một tài liệu.
- `bills/{billId}`: xem Phase 4 cho mô hình đầy đủ; Phase 3 chỉ lưu hóa đơn của luồng hiện có.

<!-- Red Team: gộp tài khoản khi trùng chưa có chủ và không an toàn — 2026-11-14 -->
Gộp tài khoản khi liên kết Google gặp tài khoản đã tồn tại: callable `mergeAnonymousAccount`.
- Client gọi **khi còn đăng nhập ẩn danh**, kèm ID token mới của tài khoản đích. Hàm không nhận `uid` nguồn làm tham số.
- Server kiểm cả hai token: token người gọi phải là tài khoản ẩn danh, token đích phải có thời điểm đăng nhập gần đây.
- Chuyển quyền sở hữu dữ liệu sang tài khoản đích, số lượt đã dùng lấy giá trị lớn hơn, gói thuê bao giữ của tài khoản đích, rồi xóa tài khoản ẩn danh.

Vết mở rộng (ranh giới `BillAnalyzer`): biến thể kế tiếp có thật là hóa đơn nhiều trang ở Phase 4 và thao tác "chỉnh bằng lời" ở Phase 7. Phần giữ ổn định là JSON trả lời. Phía yêu cầu đổi **một lần** ở Phase 4, khi `analyze` nhận danh sách trang; Phase 4 liệt kê đủ các nơi phải sửa. Bằng chứng: `SplitFlowTest` được nối lại với một `BillAnalyzer` ghi sẵn đọc fixture bằng `BillAnalysisParser` (hiện nó dùng `ClaudeBillAnalyzer` thật ở `app/src/test/java/com/dantech/splitvibe/ui/split/SplitFlowTest.kt:81`), và test giao kèo của `GatewayBillAnalyzer` chạy với emulator.

## Files

Tạo mới:
- `functions/` (`package.json`, `tsconfig.json`, `src/index.ts`, `src/analyze-bill.ts`, `src/prompt.ts`, `src/schema.ts`, `src/quota.ts`, `src/budget.ts`, `src/merge-anonymous-account.ts`, `src/delete-account.ts`, `test/`)
- `functions/eval/` (bộ chạy đánh giá và thư mục hóa đơn mẫu)
- `firebase.json`, `.firebaserc`, `firestore.rules`, `firestore.indexes.json`, `firestore-tests/`
- `app/src/main/java/com/dantech/splitvibe/llm/GatewayBillAnalyzer.kt`
- `app/src/main/java/com/dantech/splitvibe/data/auth/AuthRepository.kt`
- `app/src/main/java/com/dantech/splitvibe/data/bills/BillRepository.kt`
- `app/src/test/java/com/dantech/splitvibe/RecordedBillAnalyzer.kt` (bộ phân tích ghi sẵn cho test)

Sửa:
- `app/build.gradle.kts`, `gradle/libs.versions.toml` (Firebase BoM, plugin google-services và crashlytics; bỏ `DEFAULT_ANTHROPIC_API_KEY` ở dòng 30 và 35; bỏ `implementation(libs.anthropic.java)` ở dòng 104)
- `app/src/main/java/com/dantech/splitvibe/data/settings/SettingsStore.kt` (bỏ `apiKey`, `model`, `hasApiKey` và tham chiếu `ClaudeBillAnalyzer.DEFAULT_MODEL` ở dòng 7, 44, 58), `data/settings/KeystoreCipher.kt` (xóa nếu không còn nơi dùng)
- `app/src/main/java/com/dantech/splitvibe/ui/settings/SettingsScreen.kt` (bỏ ô key và ô model)
- Bốn nơi đọc `hasApiKey`: `ui/split/SplitBillViewModel.kt:107` (cùng tham số `analyzerFor` ở dòng 74 và 121), `ui/split/NewSplitContent.kt:59` và `:110`, `ui/split/SplitScreen.kt:17` và `:54`, `ui/AppNavHost.kt:43`
- Module Koin của Phase 1 (`di/AppModule.kt`): thêm Firebase, `AuthRepository`, `BillRepository`, `GatewayBillAnalyzer`
- `app/src/test/java/com/dantech/splitvibe/ui/split/SplitFlowTest.kt` và `app/src/test/java/com/dantech/splitvibe/ui/ScreenGalleryTest.kt` (cả hai chuyển sang `RecordedBillAnalyzer`)
- `.gitignore` (thêm `google-services.json`, vì repo công khai)

Xóa:
- `app/src/main/java/com/dantech/splitvibe/llm/ClaudeBillAnalyzer.kt`, `llm/BillPrompt.kt`, `llm/BillSchema.kt`
- `app/src/test/java/com/dantech/splitvibe/llm/ClaudeBillAnalyzerTest.kt` (10 test) và `LiveClaudeBillAnalyzerTest.kt` (1 test); các kiểm tra tương đương được viết lại trong `functions/test/`
- `app/src/androidTest/java/com/dantech/splitvibe/llm/ClaudeBillAnalyzerOnDeviceTest.kt`, thay bằng một test trên thiết bị cho `GatewayBillAnalyzer` chạy với emulator của Functions

## Implementation Steps

1. Tạo hai project Firebase (dev và prod), bật Blaze, đặt cảnh báo ngân sách, tạo Firestore ở `nam5`, đăng ký app `com.dantech.splitvibe` và `com.dantech.splitvibe.dev` cùng dấu vân tay SHA.
2. Dựng `functions/` bằng TypeScript. Viết `quota.ts` (giữ chỗ, chốt, nhả, hết hạn) và `budget.ts` (cầu dao toàn cục) cùng test trên emulator **trước**, rồi mới viết `analyzeBill`.
3. Chuyển prompt và schema từ Kotlin sang TypeScript; dùng chung thư mục fixture `app/src/sharedTest/resources/fixtures/` cho test của cả hai phía.
4. Viết `GatewayBillAnalyzer` (callable có stream, `requestId`, hạn chờ 150 giây), ánh xạ lỗi; thay `hasApiKey` bằng trạng thái "AI khả dụng" ở đủ bốn nơi; xóa `ClaudeBillAnalyzer` và thư viện Anthropic Java.
5. Đăng nhập ẩn danh lúc khởi động, liên kết Google bằng `linkWithCredential`, `mergeAnonymousAccount` cho trường hợp trùng, hàm `deleteAccount` xóa dữ liệu (kể cả collection con) rồi xóa người dùng.
6. Viết `firestore.rules` và test rules trên emulator; `BillRepository` chỉ ghi thường hoặc theo lô.
7. Bật App Check: Play Integrity cho release, token dùng một lần cho các hàm AI, danh sách app được phép gọi hàm AI. Debug provider chỉ đăng ký trên project dev. Theo dõi tỷ lệ hợp lệ rồi mới bật chế độ bắt buộc trên prod.
8. Thêm Crashlytics, Analytics (mặc định từ chối lưu trữ ở EEA, Anh, Thụy Sĩ cho tới khi có đồng ý), Remote Config (công tắc tắt AI, phiên bản tối thiểu).
9. Dựng bộ đánh giá: ít nhất 30 hóa đơn mẫu chữ Latin, chạy từng model ứng viên, xuất báo cáo độ chính xác, độ trễ và chi phí vào `plans/reports/`.
10. Triển khai lên dev; chạy một lần gọi thật từ **bản `prodRelease`** trỏ vào project dev; rồi triển khai prod.

## Todo

- [ ] Hai project Firebase, Blaze, cảnh báo ngân sách, Firestore ở `nam5`
- [ ] `quota.ts` và `budget.ts` có test: giữ chỗ, chốt, nhả, hết hạn, gọi lại cùng `requestId`
- [ ] `analyzeBill` có kiểm đầu vào, structured output, `max_tokens`, timeout 180 giây
- [ ] Prompt và schema chạy trên server, fixture dùng chung
- [ ] `GatewayBillAnalyzer`; trạng thái "AI khả dụng" thay `hasApiKey` ở bốn nơi
- [ ] Xóa `ClaudeBillAnalyzer`, test của nó và thư viện Anthropic Java
- [ ] Đăng nhập ẩn danh, liên kết Google, `mergeAnonymousAccount`, xóa tài khoản
- [ ] `firestore.rules` cùng test trên emulator
- [ ] App Check bắt buộc trên prod, token dùng một lần, danh sách app được phép
- [ ] Crashlytics, Analytics có chế độ đồng ý, Remote Config
- [ ] Bộ đánh giá và báo cáo chọn model
- [ ] Lần gọi thật từ bản `prodRelease` thành công

## Success Criteria

- Mọi biến thể build không có key và không có lớp `com.anthropic`: quét các tệp dex trong AAB `prodRelease` và APK `devDebug`, kết quả phải là 0.
- Một lần gọi thật từ **bản `prodRelease`** qua cổng trả về đúng hóa đơn mẫu (tổng 902.664 ₫, 7 món). Mục "chưa kiểm chứng" của POC được đóng lại và README được cập nhật.
- Bản `prodRelease` không còn đường nào đòi API key: tìm chuỗi "API key" trong mã giao diện cho kết quả 0.
- Test hạn mức trên emulator:
  - tài khoản ẩn danh bị từ chối ở lượt thứ 4; tài khoản đã liên kết bị từ chối ở lượt thứ 11 trong tháng;
  - gọi lại cùng `requestId` không trừ thêm lượt;
  - client ngắt kết nối giữa chừng vẫn bị tính lượt; lỗi 5xx của model thì không;
  - chỗ giữ quá 5 phút được nhả;
  - khi bộ đếm toàn cục vượt trần, mọi yêu cầu bị từ chối.
- Yêu cầu mang token App Check của một app không nằm trong danh sách, hoặc dùng lại một token đã tiêu, bị từ chối.
- Test `mergeAnonymousAccount`: dữ liệu ẩn danh xuất hiện ở tài khoản đích; yêu cầu thiếu token đích hợp lệ, hoặc người gọi không phải tài khoản ẩn danh, bị từ chối.
- Test rules: người lạ không đọc được `bills` của người khác; client không ghi được `usage`, `entitlements`, `budget`; chưa đăng nhập thì bị từ chối.
- Bản release không chứa debug provider của App Check.
- Báo cáo đánh giá nêu model được chọn, độ chính xác trên bộ mẫu, độ trễ p50 và p95, chi phí trung bình mỗi lần đọc.

## Risks

| Rủi ro | Dấu hiệu nhận biết | Phản ứng đã định |
|---|---|---|
| Model rẻ đọc sai hóa đơn khó | Bộ đánh giá: tỷ lệ khớp tổng dưới ngưỡng | Dùng model cao hơn làm mặc định hoặc leo thang khi phép đối chiếu trên máy báo lệch |
| Chi phí AI vượt dự tính | Cầu dao toàn cục chạm trần | Hạ hạn mức trong cấu hình server; app vẫn nhập tay được |
| Lượt dùng thử ẩn danh bị vét bằng cách xóa dữ liệu app | Số tài khoản ẩn danh mới mỗi ngày tăng bất thường | Thêm tín hiệu theo thiết bị cho lượt dùng thử (device recall của Play Integrity nếu khả dụng `[UNVERIFIED]`), hoặc bỏ lượt dùng thử ẩn danh |
| App Check chặn nhầm người dùng thật | Tỷ lệ yêu cầu không hợp lệ cao khi còn ở chế độ theo dõi | Chưa bật bắt buộc, điều tra theo phiên bản app và loại máy |
| Hạn mức Play Integrity (10.000 lần mỗi ngày ở mức chuẩn) không đủ | Lỗi cấp token tăng | Xin nâng hạn mức; token dùng một lần chỉ áp cho hàm AI |

## Security

- Key Anthropic chỉ nằm trong Secret Manager, gắn bằng `defineSecret`, đọc lúc chạy.
- Repo công khai, nên prompt, schema và rules đều đọc được: an toàn của hệ thống không dựa vào việc giấu chúng.
- Văn bản hóa đơn là dữ liệu không tin cậy: prompt coi nó là dữ liệu chứ không phải lệnh, đầu ra bị ràng bằng schema, và mọi phép tính tiền vẫn chạy trên máy.
- Token App Check của trang web (Phase 6) không gọi được các hàm AI.
- `google-services.json` không đưa vào repo; luồng phát hành lấy từ secret.
- Không log nội dung hóa đơn, câu lệnh hay tên người.

## Next

Phase 4 dùng `BillRepository` và đăng nhập ẩn danh để lưu lịch sử và bản nháp. Phase 8 ghi `entitlements` để đổi hạn mức. Mỗi giai đoạn thêm collection mới phải thêm phần xóa tương ứng vào `delete-account.ts`.
