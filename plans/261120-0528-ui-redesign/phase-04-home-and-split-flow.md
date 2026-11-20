---
title: "Phase 4: Trang chủ và luồng chia hóa đơn"
status: pending
phase: 4
priority: P1
effort: "5d"
dependencies: [3]
---

# Phase 4: Trang chủ và luồng chia hóa đơn

## Overview

Trang chủ mới hiện tiền bạn bè đang nợ, tiền mình đang nợ, chi tiêu tháng này và nhóm gần nhất. Luồng chia hóa đơn, từ thêm hóa đơn tới đòi tiền, mang bố cục của bản thiết kế, và phần "Show what was sent to Claude" biến mất.

## Context

- Cấu trúc hiện tại của trang chủ, màn kết quả, trình sửa hóa đơn và dữ liệu sẵn có: [rà màn hình](./reports/scout-screens-and-state.md), mục 1.1 đến 1.4, 2, 3, 6 và 7.
- Bảng tham chiếu trên canvas: `Main`, `HomeEmpty`, `NewSplit`, `NewSplitEmpty`, `Working`, `Consent`, `Result`, `ResultProposal`, `SplitByPercent`, `SheetAddPerson`, `SheetItemSplit`, `SheetWhoPaid`, `EditBill`, `AskForMoney`.
- Gói do giai đoạn này sở hữu: `ui/home/`, `ui/split/`, `ui/editor/`, `share/`.

## Key Insights

- Trang chủ hôm nay chỉ nhận ba hóa đơn gần nhất và không có view model. Số liệu mới cần ba nguồn: `RequestRepository.requests`, các hóa đơn đã chốt cùng tiền tệ của máy (như `insightsOf`), và trạng thái nhóm đã lưu.
- Yêu cầu trả tiền bị xóa khỏi máy mỗi lần đăng nhập, đăng xuất và xóa tài khoản. Khi chưa đăng nhập không có gì để cộng, nên khối chính phải mời đăng nhập thay vì hiện số 0.
- Danh sách nhóm không mang số dư; số dư chỉ có cho nhóm đã từng mở trên máy.
- `SavedBill.ocrDocument` còn là cờ "hóa đơn đã được đọc" (`SplitBillViewModel.kt:606`, `:791`). Chỉ phần giao diện bị gỡ; trường và các chỗ ghi nó phải ở lại.
- Chip người và các hàng "Each person pays" đọc cùng `plan.participants`. Gộp lại vẫn phải giữ nút bật tắt phần của "You", bảng của từng người, và ô nhập phần, phần trăm, số tiền ngay trong hàng.
- Hôm nay mọi món trong trình sửa luôn mở; "mỗi lúc mở một hàng" chỉ cần trạng thái giao diện cục bộ.
- Nhiều bài kiểm thử và hành trình ghi hồ sơ khởi động bám vào các nhãn "Split a bill", "Use the sample bill", "Read and split the bill", "New split", "Recent bills", "Ask for the money", "Done", "Type it in by hand", "Add someone", "Bill total", "Paid by" và mô tả "Bill photo". Giữ nguyên các nhãn này.

## Requirements

Trang chủ:
- Khối chính, khi đã đăng nhập và có yêu cầu còn mở: tổng tiền người khác nợ mình theo tiền tệ của máy, tên những người nợ, số yêu cầu; một dòng "You owe" dẫn sang tab Requests. Yêu cầu còn mở của mình là yêu cầu chưa được mình xác nhận; khoản mình nợ là yêu cầu mình chưa báo đã trả. Tiền tệ khác hiện thành các nhãn nhỏ bên dưới, không quy đổi.
- Khi chưa đăng nhập hoặc chưa có yêu cầu nào: khối chính hiện chi tiêu của mình tháng này; khi chưa có hóa đơn nào, nó là lời mời chia hóa đơn đầu tiên như bảng `HomeEmpty`.
- Ô tháng này dẫn sang Insights. Ô nhóm hiện nhóm mở gần nhất có trạng thái đã lưu, kèm số dư của mình; không có nhóm như vậy thì ô không hiện.
- "Recent bills" giữ ba hóa đơn và "See all".

Luồng chia hóa đơn:
- Màn thêm hóa đơn, màn đang đọc và màn xin đồng ý theo các bảng; nội dung màn xin đồng ý không đổi một chữ.
- Màn kết quả theo thứ tự: tổng tiền, tóm tắt của AI, lời nhắc, "Each person pays" gộp với người, ô "Change the split", kiểu chia thành một hàng cuộn ngang, món, phụ phí, "Paid by", thanh nổi với nút yêu cầu.
- Gỡ phần "sent to Claude": các dòng `ResultContent.kt:373-393`, biến `showOcrText`, hai chuỗi `split_show_sent` và `split_hide_sent`.
- Trình sửa hóa đơn: món và phụ phí là hàng, mỗi lúc một hàng mở để sửa; phần "Total" giữ nguyên hành vi.
- Màn đòi tiền: dấu thành công, thẻ vé để chia sẻ, lời nhắc thêm cách nhận tiền, hàng từng người với nút "Send". Ảnh chia sẻ dùng thẻ vé mới.
- Nhãn mới: "Friends owe you", "You owe", "Your share of N bills", "You get back", "Paid the bill", "Tell the AI what to change"; ngày ngắn kiểu "Nov 13".

## Files to Create / Modify

- Create: `ui/home/HomeViewModel.kt`, `ui/home/HomeSummary.kt` (hàm thuần cộng yêu cầu theo tiền tệ và chọn trạng thái của khối chính).
- Create: `app/src/test/java/com/dantech/splitvibe/ui/home/HomeSummaryTest.kt`.
- Modify: `ui/home/HomeScreen.kt`, mục Home trong `ui/AppNavHost.kt` (dòng 205-222), `di/AppModule.kt`.
- Modify: `ui/split/NewSplitContent.kt`, `ResultContent.kt`, `SplitSheets.kt`, `DivisionInputs.kt`, `AssistantContent.kt`, `AiConsent.kt`, `RequestMoneyContent.kt`, `SplitScreen.kt`.
- Modify: `ui/editor/BillEditorContent.kt`.
- Modify: `share/ShareCardRenderer.kt` nếu khung thẻ vé cần.
- Modify: `app/src/main/res/values/strings.xml` (các chuỗi `home_*`, `split_*`, `requests_ask_*`, `editor_*`).
- Modify tests: `ui/ScreenGalleryTest.kt` (thứ tự màn kết quả, các hằng số cuộn, bỏ `09-sent-to-claude`), `ui/split/SplitFlowTest.kt`, `BillLifecycleTest.kt`, `BillSafetyTest.kt`, `AiConsentFlowTest.kt`, `ui/ReducedMotionTest.kt`, `ui/PseudoLocaleGalleryTest.kt`, `ui/DesignSystemBoundaryTest.kt`; `app/src/androidTest/.../SplitFlowOnDeviceTest.kt`, `e2e/*JourneyTest.kt`, `e2e/JourneySteps.kt`.

## Implementation Steps

1. Viết `HomeSummary` và `HomeSummaryTest` trước: cộng theo tiền tệ; bỏ yêu cầu đã xác nhận; khoản mình nợ bỏ yêu cầu đã báo trả; trạng thái chưa đăng nhập; không có hóa đơn; nhiều tiền tệ.
2. Viết `HomeViewModel` gộp hóa đơn gần đây, tóm tắt yêu cầu, chi tiêu tháng (dùng lại `insightsOf` với kỳ "tháng này") và nhóm gần nhất đã lưu. Dựng `HomeScreen` theo bảng `Main` và `HomeEmpty`.
3. Dựng lại màn thêm hóa đơn và màn đang đọc; giữ mô tả "Bill photo" và ba dòng trạng thái hiện có.
4. Dựng lại màn kết quả theo thứ tự mới. Hàng của "You" mang nút bật tắt phần của mình; hàng người khác mở `PersonSheet`; nút "Add someone" nằm cuối danh sách người.
5. Gỡ phần "sent to Claude" và hai chuỗi của nó; giữ `ocrDocument`. Thêm vào `DesignSystemBoundaryTest` luật cấm màn hình tự đặt họ phông, vì chỗ dùng duy nhất vừa mất.
6. Dựng lại các bảng trượt thêm người, chia một món, ai đã trả.
7. Trình sửa hóa đơn: hàng đóng hiện tên, số lượng nhân đơn giá và số tiền; hàng mở hiện bốn ô nhập và nút xóa; chỉ số hàng đang mở giữ bằng `rememberSaveable`.
8. Dựng lại màn đòi tiền và thẻ chia sẻ; kiểm tra ảnh xuất ra có đủ thẻ vé.
9. Sửa các bài kiểm thử trong danh sách: thứ tự mới của màn kết quả, điểm cuộn, chỗ thêm người. Chạy lại `SplitFlowTest`, `BillLifecycleTest`, `ReducedMotionTest`.
10. Dựng ảnh các màn của giai đoạn ra thư mục tạm, ghép bảng xem nhanh sáng và tối.

## Todo

- [ ] `HomeSummary`, `HomeViewModel` và kiểm thử
- [ ] Trang chủ ở ba trạng thái
- [ ] Màn thêm hóa đơn, đang đọc, xin đồng ý
- [ ] Màn kết quả, gỡ phần "sent to Claude"
- [ ] Ba bảng trượt
- [ ] Trình sửa hóa đơn
- [ ] Màn đòi tiền và thẻ chia sẻ
- [ ] Kiểm thử JVM và trên máy ảo của luồng
- [ ] Bảng ảnh gửi chủ sản phẩm

## Success Criteria

- `./gradlew :app:testProdDebugUnitTest` qua khi bỏ các bài so ảnh.
- `SplitFlowOnDeviceTest` và bốn hành trình `e2e` qua trên máy ảo API 34.
- Hóa đơn mẫu chia ra đúng các số của bảng `Result`, và tổng các phần bằng tổng hóa đơn.
- Một hóa đơn đã đọc bằng AI vẫn được coi là "đã đọc" sau khi mở lại từ lịch sử.

## Risk Assessment

- Tổng trên trang chủ sai lệch với màn Requests: cả hai dùng chung `HomeSummary`; giai đoạn 5 gọi lại hàm này.
- Gộp người vào "Each person pays" làm mất đường bật tắt phần của "You": `SplitFlowTest` và hành trình nhập tay kiểm tra việc này.
- Thanh nổi che hàng cuối: các hằng số cuộn của `ScreenGalleryTest` và bài `BillSafetyTest` cuộn tới "Paid by" bắt lỗi này.
- Ảnh chia sẻ bị cắt khuyết của thẻ vé: bước 8 xem ảnh xuất thật.

## Rollback

Hoàn lại các commit của giai đoạn. `ocrDocument` và mọi dữ liệu lưu không đổi, nên hóa đơn cũ vẫn mở được ở cả hai phía.
