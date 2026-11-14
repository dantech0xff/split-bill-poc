---
title: "Phase 7: Chỉnh bằng lời với AI, thống kê, xuất file, hóa đơn định kỳ"
status: todo
phase: 7
priority: P2
effort: "9d"
dependencies: [4]
---

# Phase 7: Chỉnh bằng lời với AI, thống kê, xuất file, hóa đơn định kỳ

## Overview

Sau khi có kết quả, người dùng gõ hoặc nói một câu như "Lan không uống bia" hay "thêm 10% tip" và app cập nhật cách chia. Người dùng xem được chi tiêu theo tháng, theo người, theo loại, xuất ra CSV hoặc PDF, và đặt hóa đơn lặp lại như tiền nhà.

Tính năng v1.0 thuộc giai đoạn này: chỉnh bằng lời với AI; thống kê, xuất file và hóa đơn định kỳ.

## Context

- Hiện tại câu lệnh chỉ dùng được một lần, lúc đọc hóa đơn (`app/src/main/java/com/dantech/splitvibe/ui/split/SplitBillViewModel.kt:102`). Muốn đổi ý phải chạm tay từng món.
- Cổng AI của Phase 3 và `BillEditor` của Phase 4 là hai nền để giai đoạn này xây lên.
- Chưa có thống kê, xuất file hay lịch lặp nào.

## Key Insights

- Nguyên tắc của POC được giữ nguyên: **AI đề xuất, máy tính tiền**. Model không trả số tiền của từng người; nó trả một danh sách thao tác có cấu trúc, còn `:core:domain` áp thao tác và tính lại.
- Lệnh chỉnh sửa ngắn và đầu vào đã có cấu trúc, nên có thể dùng model rẻ hơn lần đọc hóa đơn. Bộ đánh giá quyết định.
- Nhận giọng nói dùng bộ nhận dạng của Android trên máy; app chỉ nhận về văn bản rồi đi cùng đường với câu gõ tay.
- Thống kê tính trên máy từ dữ liệu đã đồng bộ; không cần server.

## Requirements

Chức năng:
- **Chỉnh bằng lời:** ô nhập và nút micro ở màn hình kết quả. App hiện các thay đổi AI đề xuất dưới dạng danh sách trước và sau; người dùng chấp nhận tất cả, bỏ từng mục, hoặc hoàn tác sau khi áp.
- **Thao tác được hỗ trợ:** đổi người chia một món, đổi cách chia, thêm hoặc bỏ người, thêm phụ phí hoặc tip theo phần trăm hay số tiền, đổi người trả, sửa giá hoặc số lượng một món.
- **Giọng nói:** nhận dạng trên máy khi có; xin quyền micro đúng lúc bấm; không có quyền thì vẫn gõ được.
- **Thống kê:** tổng chi theo tháng, theo người, theo nhóm, theo loại; loại do AI gợi ý lúc đọc hóa đơn và sửa được.
- **Xuất file:** CSV (hóa đơn, món, phần mỗi người) và PDF tóm tắt, qua bảng chia sẻ hoặc lưu vào tệp.
- **Hóa đơn định kỳ:** mẫu hóa đơn kèm lịch tuần hoặc tháng; tới kỳ thì app tạo bản nháp và nhắc.

Phi chức năng:
- Mỗi lần chỉnh bằng lời tính vào hạn mức AI theo trọng số riêng, cấu hình trên server.
- Mọi thao tác AI đề xuất đều phải qua kiểm tra của `BillEditor`; thao tác không hợp lệ bị loại và người dùng được báo.

## Architecture

Server: callable `reviseSplit`.
- Yêu cầu: hóa đơn hiện tại và cách chia hiện tại ở dạng có cấu trúc (không gửi lại văn bản OCR), câu lệnh, ngôn ngữ.
- Trả lời: danh sách thao tác theo schema cố định, mỗi thao tác có loại và tham số, kèm một câu giải thích ngắn.
- Dùng chung phần kiểm đăng nhập, App Check, `requestId` và cách giữ chỗ rồi chốt lượt với `analyzeBill` (Phase 3).

`:core:domain`:
- `SplitEdit` (kiểu đóng các thao tác) và `applyEdits(bill, plan, edits)` trả về trạng thái mới cùng danh sách thao tác bị loại.
- `Insights`: các phép tổng hợp thuần túy trên danh sách hóa đơn.
- `Recurrence`: tính kỳ kế tiếp từ lịch và ngày bắt đầu.

App:
- `VoiceInput` bọc `SpeechRecognizer`.
- `Exporter` ghi CSV và PDF (`android.graphics.pdf.PdfDocument`).
- `RecurringBillWorker` dùng WorkManager để tạo bản nháp và thông báo khi tới kỳ. <!-- Red Team: nơi ghi theo lịch không lũy đẳng — 2026-11-14 --> Bản nháp có mã cố định `{templateId}_{periodKey}` và chỉ được tạo khi chưa tồn tại, nên hai máy cùng tài khoản không tạo trùng.
- Loại chi tiêu: thêm trường `category` vào schema trả lời của `analyzeBill` (kiểu liệt kê cố định); `BillAnalysisParser` đọc trường mới, vắng thì coi là "khác".

Vết mở rộng (ranh giới cổng AI): `reviseSplit` là biến thể thứ hai của cùng một cổng. Phần dùng chung là xác thực, hạn mức, log và cách gọi model; phần riêng là prompt, schema và trọng số hạn mức. Không tách dịch vụ mới.

## Files

Tạo mới:
- `functions/src/revise-split.ts`, `functions/src/revise-prompt.ts`, `functions/eval/revise/` (bộ câu lệnh đánh giá)
- Trong `core/domain/`: `SplitEdit.kt`, `Insights.kt`, `Recurrence.kt` cùng test
- `app/src/main/java/com/dantech/splitvibe/assistant/SplitAssistant.kt`, `VoiceInput.kt`
- `app/src/main/java/com/dantech/splitvibe/ui/insights/`, `ui/recurring/`
- `app/src/main/java/com/dantech/splitvibe/export/Exporter.kt`
- `app/src/main/java/com/dantech/splitvibe/recurring/RecurringBillWorker.kt`

Sửa:
- `app/src/main/java/com/dantech/splitvibe/ui/split/ResultContent.kt` (ô chỉnh bằng lời, danh sách đề xuất)
- `functions/src/schema.ts`, `functions/src/prompt.ts` (trường `category`)
- `BillAnalysisParser.kt` trong `:core:domain` và test của nó
- `functions/src/delete-account.ts` (xóa mẫu hóa đơn định kỳ)
- `app/src/main/AndroidManifest.xml` (quyền `RECORD_AUDIO`)

## Implementation Steps

1. Viết `SplitEdit`, `applyEdits` và test: mỗi thao tác giữ nguyên các bất biến về tiền của Phase 4.
2. Soạn bộ đánh giá ít nhất 40 câu lệnh (tiếng Anh, tiếng Việt và vài ngôn ngữ khác) kèm thao tác mong đợi; viết `reviseSplit`; chọn model theo số liệu.
3. Giao diện đề xuất trước và sau, chấp nhận, bỏ, hoàn tác; nối `VoiceInput`.
4. Thêm `category` vào schema, bộ đọc và fixture; `Insights` và màn hình thống kê.
5. `Exporter` cho CSV và PDF với test so tệp mẫu.
6. `Recurrence`, màn hình hóa đơn định kỳ, `RecurringBillWorker`, test với đồng hồ giả.
7. Ảnh chuẩn cho màn hình mới.

## Todo

- [ ] `SplitEdit` và `applyEdits` với test bất biến
- [ ] Bộ đánh giá câu lệnh chỉnh sửa và `reviseSplit`
- [ ] Giao diện đề xuất, chấp nhận, bỏ, hoàn tác
- [ ] Nhập bằng giọng nói
- [ ] Trường `category`, `Insights`, màn hình thống kê
- [ ] Xuất CSV và PDF
- [ ] Hóa đơn định kỳ với WorkManager
- [ ] Ảnh chuẩn cho màn hình mới

## Success Criteria

- Test bất biến: áp bất kỳ dãy thao tác hợp lệ nào, tổng các phần vẫn bằng tổng hóa đơn; thao tác trỏ tới món hoặc người không tồn tại bị loại và được báo.
- Bộ đánh giá: model được chọn cho ra đúng dãy thao tác ở ít nhất 90% câu lệnh; báo cáo ghi độ chính xác, độ trễ và chi phí từng model.
- Câu "Lan không uống bia" trên hóa đơn mẫu bỏ Lan khỏi món bia và phần của ba người cập nhật đúng; hoàn tác trả lại trạng thái cũ.
- Câu lệnh chứa chỉ dẫn lạ (ví dụ đòi đặt số tiền âm cho một người) không tạo ra trạng thái vi phạm bất biến.
- Từ chối quyền micro: ô gõ vẫn dùng được, không có lỗi.
- Thống kê tháng trên fixture 20 hóa đơn khớp bảng tính tay.
- Tệp CSV và PDF xuất từ fixture khớp tệp mẫu; CSV mở được bằng phần mềm bảng tính với đúng dấu thập phân.
- Với đồng hồ giả, hóa đơn định kỳ hằng tháng tạo đúng một bản nháp mỗi kỳ, kể cả tháng thiếu ngày và kể cả khi hai máy cùng tài khoản cùng chạy.

## Risks

| Rủi ro | Dấu hiệu nhận biết | Phản ứng đã định |
|---|---|---|
| Model hiểu sai câu lệnh mơ hồ | Bộ đánh giá dưới 90% | Model trả câu hỏi lại thay vì đoán; người dùng luôn duyệt trước khi áp |
| Nhận dạng giọng nói kém với tên riêng | Người thử phải sửa nhiều | Hiện văn bản nhận được để sửa trước khi gửi |
| WorkManager bị hệ thống trì hoãn | Bản nháp tới trễ | Khi mở app, kiểm tra và tạo bù các kỳ đã lỡ |
| Chi phí AI tăng vì chỉnh sửa nhiều lần | Số lượt `reviseSplit` mỗi hóa đơn cao | Trọng số hạn mức riêng, model rẻ, gộp nhiều thay đổi trong một câu |

## Security

- Câu lệnh và tên người là dữ liệu không tin cậy với model: đầu ra bị ràng bằng schema và mọi thao tác đi qua `applyEdits`.
- Âm thanh không rời máy khi nhận dạng trên máy có sẵn; nếu máy chỉ có nhận dạng qua mạng, app báo trước cho người dùng.
- Tệp xuất chỉ ghi vào nơi người dùng chọn hoặc đi qua bảng chia sẻ.

## Next

Phase 8 đặt hạn mức khác nhau cho bản miễn phí và Pro trên cả `analyzeBill` lẫn `reviseSplit`.
