---
title: "Phase 5: Trang chủ và luồng chia hóa đơn"
status: pending
phase: 5
priority: P1
effort: "6d"
dependencies: [4]
---

# Phase 5: Trang chủ và luồng chia hóa đơn

## Overview

Trang chủ mới hiện tiền bạn bè đang nợ, tiền mình đang nợ, chi tiêu tháng này và nhóm vừa cập nhật gần nhất. Luồng chia hóa đơn, từ thêm hóa đơn tới đòi tiền, mang bố cục của bản thiết kế. Phần "Show what was sent to Claude" biến mất, và ngoài phần xin đồng ý, app gọi AI bằng giọng của sản phẩm.

## Context

- Cấu trúc hiện tại của trang chủ, màn kết quả, trình sửa hóa đơn và dữ liệu sẵn có: [rà màn hình](./reports/scout-screens-and-state.md), mục 1.1 đến 1.4, 2, 3, 6 và 7.
- Bằng chứng kèm tệp và dòng cho các điểm dưới đây: [phản biện về giả định](./reports/red-team-assumptions.md), [về kiểu hỏng](./reports/red-team-failure-modes.md), [về bảo mật](./reports/red-team-security.md).
- Bảng tham chiếu trong [`boards/`](./boards/README.md): [`Main`](./boards/Main.dc.html), [`HomeEmpty`](./boards/HomeEmpty.dc.html), [`NewSplit`](./boards/NewSplit.dc.html), [`NewSplitEmpty`](./boards/NewSplitEmpty.dc.html), [`Working`](./boards/Working.dc.html), [`Consent`](./boards/Consent.dc.html), [`Result`](./boards/Result.dc.html), [`ResultProposal`](./boards/ResultProposal.dc.html), [`SplitByPercent`](./boards/SplitByPercent.dc.html), [`SheetAddPerson`](./boards/SheetAddPerson.dc.html), [`SheetItemSplit`](./boards/SheetItemSplit.dc.html), [`SheetWhoPaid`](./boards/SheetWhoPaid.dc.html), [`EditBill`](./boards/EditBill.dc.html), [`AskForMoney`](./boards/AskForMoney.dc.html), và bốn bảng tối `DarkHome`, `DarkNewSplit`, `DarkResult`, `DarkAskForMoney`.
- Gói do giai đoạn này sở hữu: `ui/home/`, `ui/split/`, `ui/editor/`, `share/`.
- Thứ dùng lại từ các giai đoạn trước: `SvDialog`, `SvQuietButton`, `SvTextField`, `SvCheckbox`, `SvHeroAmount`, `CategoryStyle` và phép kiểm thanh che nội dung của giai đoạn 2; `RequestTotals` cùng bộ giữ theo Activity của giai đoạn 3; ba phần của số tiền và các hàm ngày của giai đoạn 4.
- Chữ của giai đoạn này theo quyết định 3 đến 6 trong [plan](./plan.md).

## Key Insights

- Trang chủ hôm nay chỉ nhận ba hóa đơn gần nhất và không có view model. Số liệu mới đến từ ba nơi: tổng yêu cầu của giai đoạn 3, các hóa đơn đã chốt cùng tiền tệ của máy (như `insightsOf`), và trạng thái nhóm trong `ProxyCache`.
- `RequestRepository.requests` là `null` cho tới khi được tải, và lại là `null` sau mỗi lần đăng nhập, đăng xuất và xóa tài khoản (`RequestRepository.kt:53-60`). `null` vì thế không có nghĩa là "không có yêu cầu". Từ giai đoạn 3, bộ giữ theo Activity tải lại danh sách khi app mở và sau mỗi lần đăng nhập; giai đoạn này chỉ đọc, không thêm lời gọi tải nào.
- Danh sách nhóm không mang số dư; số dư chỉ có trong trạng thái đầy đủ của nhóm đã từng được tải về máy. `ProxyCacheDao` hôm nay chỉ đọc theo đúng khóa (`data/bills/SplitVibeDatabase.kt:183-198`), và `savedAt` (`:180`) là lúc ghi cuối, không phải lúc người dùng mở nhóm.
- `SavedBill.ocrDocument` còn là cờ "hóa đơn đã được đọc" (`SplitBillViewModel.kt:606`, `:791`). Chỉ phần giao diện bị gỡ; trường và các chỗ ghi nó ở lại, và thứ gửi cho AI không đổi.
- App chưa từng có nhãn "Photo stays on this phone": cụm đó chỉ nằm trên một bảng canvas và đã bị bỏ ở đó. Trong mã, câu "The photo stays on this phone." chỉ có trong chuỗi xin đồng ý `ai_consent_bill_read`, và chuỗi đó không được đụng tới.
- Chip người và các hàng "Each person pays" đọc cùng `plan.participants`. Gộp lại vẫn phải giữ nút bật tắt phần của "You", bảng của từng người, và ô nhập phần, phần trăm, số tiền ngay trong hàng.
- Hôm nay mọi món trong trình sửa luôn mở, và các hàng được định danh bằng id của món và của phụ phí (`BillEditorContent.kt:144-145`). Trạng thái "hàng đang mở" vì thế giữ theo id; giữ theo vị trí sẽ nhảy sang hàng khác khi một món bị xóa hay được thêm.
- Bảng `HomeEmpty` có một nút nhìn thấy được mang chữ "Split a bill", còn nút giữa của thanh điều hướng chỉ mang mô tả "Split a bill" từ giai đoạn 3. Mười hai chỗ trong kiểm thử và hành trình ghi hồ sơ khởi động tìm nút giữa bằng mô tả, nên nút mời không được mang cùng mô tả đó.
- Kiểm thử và hành trình còn bám vào các nhãn "Use the sample bill", "Read and split the bill", "New split", "Recent bills", "Ask for the money", "Done", "Type it in by hand", "Add someone", "Bill total", "Paid by", "Add an item", "Where", "Item", "Amount" và mô tả "Bill photo". Giữ nguyên các nhãn này.
- Hai tên phần tử dùng chung `SPLIT_SURFACE` và `SEND_SURFACE` (`SplitScreen.kt:31`, `:134`, `ResultContent.kt:744`, `RequestMoneyContent.kt:123`) giữ nguyên, nếu không hiệu ứng nở mất mà không báo lỗi.
- Các ô nhập ở đây dùng cả những phần giai đoạn 2 đã kể ra cho `SvTextField` ngoài hậu tố, tiền tố, nhiều dòng và biểu tượng đầu ô: ô chỉ dẫn của màn thêm hóa đơn có lúc bị tắt; ô của trợ lý có hai nút ở cuối ô và hành động gửi của bàn phím.

## Requirements

Trang chủ:
- Trang chủ đọc tổng yêu cầu từ bộ giữ theo Activity của giai đoạn 3; nó không tự tải và không tự cộng. Hàm thuần `HomeSummary` chọn khối chính từ ba thứ: trạng thái yêu cầu, danh sách hóa đơn và chi tiêu tháng này.
- `Loaded` và còn yêu cầu mở: khối "Friends owe you" như bảng `Main`, gồm tổng bạn bè nợ mình bằng tiền tệ của máy, tên người nợ, số yêu cầu, và một dòng "You owe" dẫn sang tab Requests. Tổng bằng tiền tệ khác hiện thành nhãn nhỏ bên dưới, không quy đổi.
- `SignedOut`, hoặc `Loaded` mà không còn yêu cầu mở, khi máy đã có hóa đơn: khối chính hiện chi tiêu của mình tháng này.
- Cũng hai trạng thái đó khi máy chưa có hóa đơn nào: lời mời chia hóa đơn đầu tiên như bảng `HomeEmpty`.
- `Unknown`, tức đã đăng nhập nhưng danh sách chưa về: hiện như khi không có yêu cầu, và khối tự đổi khi danh sách về.
- Ba điểm sau là mặc định của người lập plan cho trạng thái chưa có bảng, chủ sản phẩm duyệt qua ảnh thật: yêu cầu mở thắng lời mời khi chưa có hóa đơn nào; khi chỉ có khoản mình nợ, khối chính lấy "You owe" làm số chính; ô nhóm xử lý số dư bằng 0 và số dư `null` như nêu bên dưới.
- Tên người nợ, số yêu cầu và tên người mình nợ lấy từ cùng danh sách mà bộ giữ đang cầm. Nếu `RequestTotals` chưa mang các trường này, thêm vào `ui/requests/RequestTotals.kt` và `RequestTotalsTest`; trang chủ không cộng lại lần hai.
- Số tiền chính dùng `SvHeroAmount` với ba phần của giai đoạn 4.
- Ô tháng này dẫn sang Insights. Ô nhóm hiện nhóm vừa được làm mới hoặc thay đổi gần nhất trên máy này, kèm số dư của mình với nhãn "You get back" hoặc "You owe". Số dư bằng 0 dùng chữ "settled up" đang có; ô không hiện khi bộ nhớ đệm không có nhóm nào hoặc số dư là `null`.
- Nguồn của ô nhóm là một truy vấn mới của `ProxyCacheDao`, lọc các khóa nhóm và xếp theo `savedAt`. Lược đồ cơ sở dữ liệu không đổi, và không con trỏ nào được giữ ngoài `ProxyCache`, nên đăng xuất làm ô trống theo.
- Sau khi đăng xuất hoặc xóa tài khoản, khối chính, ô nhóm và số đếm trên tab Requests không còn gì của tài khoản cũ.
- "Recent bills" giữ ba hóa đơn và "See all"; mỗi hàng có ô danh mục theo `CategoryStyle`. Ngày trên hàng là dạng ngắn của giai đoạn 4 khi ngày của hóa đơn đọc được, và là chữ của hóa đơn khi không đọc được. Trang chủ hiện "See all" khi máy có hóa đơn hoặc có hóa đơn định kỳ, để màn Bills và thẻ Recurring luôn mở được; đây là mặc định của plan đã nêu ở giai đoạn 3, chủ sản phẩm đổi được.
- Nút tài khoản giữ mô tả "Settings" (`AppStartupTest.kt:40`, `:49`, `:71` và `AppLaunchTest.kt:41` tìm nó như vậy).
- Hai trạng thái chưa có bảng, chưa đăng nhập và chưa tải xong yêu cầu, dựng từ thành phần của bảng `Main` và `HomeEmpty`; ảnh thật của chúng được gửi chủ sản phẩm duyệt.

Luồng chia hóa đơn:
- Màn thêm hóa đơn, màn đang đọc và màn xin đồng ý theo các bảng. Nội dung màn xin đồng ý không đổi một chữ. Theo bảng `Consent`, hai câu trả lời đứng cuối màn và không nằm trong thanh: "Agree" là `SvPrimaryButton`, câu còn lại là `SvQuietButton` thay cho `OutlinedButton` gọi thẳng trong `SvBottomBar` hôm nay (`ui/split/AiConsent.kt:82`), thứ mà giai đoạn 2 đã chuyển trước vì không đủ tương phản trên thanh. Hai nút vẫn nằm ngoài phần cuộn như hôm nay, để luôn trong tầm nhìn ở chữ lớn.
- Màn kết quả theo thứ tự: tổng tiền, thẻ "What Split Vibe understood", lời nhắc, "Each person pays" gộp với người, ô "Change the split", kiểu chia thành một hàng cuộn ngang, món, phụ phí, "Paid by", thanh nổi với nút yêu cầu. Ngày dưới tổng tiền là dạng có năm của giai đoạn 4 khi đọc được.
- Gỡ phần xem chữ đã gửi cho AI: các dòng `ResultContent.kt:373-393`, biến `showOcrText` (`:140`), hai chuỗi `split_show_sent` và `split_hide_sent`. Cái giá về minh bạch đã được chủ sản phẩm chấp nhận và ghi ở quyết định 3 của plan.
- Trình sửa hóa đơn: món và phụ phí là hàng, mỗi lúc một hàng mở để sửa. Hàng đang mở được giữ bằng id của món hoặc phụ phí trong `rememberSaveable`, và hàng vừa thêm tự mở. Phần "Total" giữ nguyên hành vi.
- Màn đòi tiền: dấu thành công, thẻ vé để chia sẻ, lời nhắc thêm cách nhận tiền, hàng từng người với nút "Send". Ảnh chia sẻ dùng thẻ vé mới.
- Màn kết quả và trình sửa đặt `SvBottomBar` trong ô `bottomBar` của `Scaffold`; hàng cuối và ô đang gõ không nằm dưới thanh, kể cả khi bàn phím mở.

Chữ, theo bảng của quyết định 6:

| Chuỗi hôm nay | Chữ mới |
|---|---|
| `split_asking_claude` "Claude is working out the bill…" | "Reading your bill…" |
| (nhãn mới) tiêu đề thẻ tóm tắt trên màn kết quả | "What Split Vibe understood" |
| `home_intro_text` "… worked out by Claude, …" | "… worked out by AI, …" |
| `assistant_ask_claude` "Ask Claude" | "Ask the AI" |
| `assistant_working` "Claude is working out the changes…" | "Working out the changes…" |
| `assistant_nothing_to_change` "Claude found nothing to change." | "The AI found nothing to change." |
| `assistant_answer_hint` "… and Claude works the change out again." | "… and the AI works the change out again." |
| `assistant_unreadable` "%d more change(s) Claude proposed …" | "%d more change(s) the AI proposed …" |

- Hai khóa có chữ `claude` trong tên, `split_asking_claude` và `assistant_ask_claude`, đổi tên theo chữ mới; nơi dùng là `ui/split/NewSplitContent.kt:275-279` và `ui/split/AssistantContent.kt`.
- Mọi chuỗi `ai_consent_*` và chuỗi `settings_ai_consent_given` giữ nguyên từng ký tự; một bài kiểm thử so chúng với bản chép đã ghi.
- Nhãn mới: "Friends owe you", "You owe", "Your share of N bills", "You get back", "Paid the bill", "Tell the AI what to change", "What Split Vibe understood". Nhãn có số đếm đọc qua `UiText`, như `NumbersInTextsTest` đòi.

Lời gọi thẳng thành phần Material được chuyển trong giai đoạn này (số dòng theo mã ngày 20/11/2026):
- `ui/home/HomeScreen.kt`: `FilledTonalButton` `:147`. Nó nằm trong hàm `PlaceButton` mà giai đoạn 3 bỏ cùng bốn nút dẫn; giai đoạn này xác nhận trang chủ không còn lời gọi đó, và xóa hàm nếu nó còn sót.
- `ui/split/AssistantContent.kt`: `OutlinedTextField` `:104`, `FilledTonalButton` `:219`, `AlertDialog` `:241`, `Checkbox` `:272`.
- `ui/split/NewSplitContent.kt`: `OutlinedTextField` `:117`, `FilledTonalButton` `:207`.
- `ui/split/RequestMoneyContent.kt`: `OutlinedTextField` `:157`, `AlertDialog` `:195`, `FilledTonalButton` `:282`.
- `ui/split/SplitScreen.kt`: `AlertDialog` `:204`.
- `ui/split/SplitSheets.kt`: `OutlinedTextField` `:92`, `:148`.
- Ô nhập thành `SvTextField`, giữ tùy chọn bàn phím và giới hạn độ dài đang có (`SplitSheets.kt:94`, `:150`, `RequestMoneyContent.kt:159`); nút thành `SvQuietButton`; hộp thoại thành `SvDialog`; ô chọn thành `SvCheckbox`. Tham số nào `SvTextField` còn thiếu thì thêm vào thành phần và trang mẫu, không dựng ô riêng trong màn.

## Files to Create / Modify

Đường dẫn ngắn tính từ `app/src/main/java/com/dantech/splitvibe/` và, với kiểm thử, từ `app/src/test/java/com/dantech/splitvibe/`.

- Create: `ui/home/HomeSummary.kt` (hàm thuần chọn khối chính), `ui/home/HomeViewModel.kt` (chi tiêu tháng này qua `insightsOf`, nhóm gần nhất).
- Create tests: `ui/home/HomeSummaryTest.kt`; một bài sau đăng xuất (ví dụ `ui/home/HomeAfterSignOutTest.kt`); một bài so chuỗi xin đồng ý (ví dụ `text/ConsentTextsTest.kt`).
- Modify: `ui/home/HomeScreen.kt`, `di/AppModule.kt`. Bốn nơi gọi `HomeScreen` đổi theo chữ ký mới: `entry<HomeKey>` trong `ui/AppNavHost.kt`, `ui/ScreenGalleryTest.kt:386`, `ui/PseudoLocaleGalleryTest.kt:87`, `text/NumbersInTextsTest.kt:154`.
- Modify: `data/bills/SplitVibeDatabase.kt` (`ProxyCacheDao`), `data/proxy/ProxyCache.kt`, `data/groups/GroupRepository.kt`; kiểm thử trong `data/groups/GroupRepositoryTest.kt`.
- Modify: `ui/split/NewSplitContent.kt`, `ResultContent.kt`, `SplitSheets.kt`, `DivisionInputs.kt`, `AssistantContent.kt`, `AiConsent.kt`, `RequestMoneyContent.kt`, `SplitScreen.kt`.
- Modify: `ui/editor/BillEditorContent.kt`; `share/ShareCardRenderer.kt` nếu khung thẻ vé cần.
- Modify: `app/src/main/res/values/strings.xml` (các chuỗi `home_*`, `split_*`, `assistant_*`, `requests_ask_*`, `editor_*`).
- Modify tests: `ui/ScreenGalleryTest.kt` (thứ tự màn kết quả và các hằng số cuộn ở `:248-282`, bỏ bước `09-sent-to-claude` ở `:269-273`, mô tả "Ask Claude" thành "Ask the AI"); `ui/SpendingGalleryTest.kt` (bài "the category of a bill in the editor"); `ui/AiConsentGalleryTest.kt`; `ui/split/SplitFlowTest.kt`, `ui/split/BillLifecycleTest.kt:95-100`, `ui/split/BillSafetyTest.kt:130-132`, `ui/split/AiConsentFlowTest.kt`; `ui/ReducedMotionTest.kt`; `ui/DesignSystemBoundaryTest.kt`; `AppStartupTest.kt`.
- Modify device tests, dưới `app/src/androidTest/java/com/dantech/splitvibe/`: `SplitFlowOnDeviceTest.kt`, `e2e/JourneySteps.kt:79-87` (`fillInTheBill`), `e2e/ManualEntryJourneyTest.kt`; ba hành trình còn lại `e2e/SampleBillJourneyTest.kt`, `e2e/DeclinedAiJourneyTest.kt`, `e2e/PaymentRequestJourneyTest.kt` được chạy lại và sửa ở chỗ bố cục mới đổi cách tìm.

## Implementation Steps

1. Viết `HomeSummary` và `HomeSummaryTest` trước: `SignedOut`, `Unknown` và `Loaded` với danh sách rỗng là ba ca riêng; còn yêu cầu mở; chỉ có khoản mình nợ; chưa có hóa đơn nào; nhiều tiền tệ.
2. Thêm truy vấn "nhóm ghi gần nhất" vào `ProxyCacheDao`, đưa nó qua `ProxyCache` và `GroupRepository`, kèm kiểm thử rằng nó trống sau `ProxyCache.clear()`.
3. Viết `HomeViewModel` và dựng `HomeScreen` theo bảng `Main` và `HomeEmpty`; sửa bốn nơi gọi. Viết bài sau đăng xuất: khối chính, ô nhóm và số đếm trên tab Requests đều trống. Thêm vào `AppStartupTest` khẳng định rằng trang chủ lần đầu có đúng một phần tử mang mô tả "Split a bill".
4. Đổi chữ theo bảng, đổi tên hai khóa, thêm nhãn mới; viết bài so chuỗi xin đồng ý trước khi sửa `strings.xml`, để nó canh cả lượt sửa.
5. Dựng lại màn thêm hóa đơn, màn đang đọc và màn xin đồng ý; giữ mô tả "Bill photo" và ba dòng trạng thái.
6. Dựng lại màn kết quả theo thứ tự mới. Hàng của "You" mang nút bật tắt phần của mình; hàng người khác mở `PersonSheet`; nút "Add someone" nằm cuối danh sách người.
7. Gỡ phần xem chữ đã gửi và hai chuỗi của nó; giữ `ocrDocument`. Thêm vào `DesignSystemBoundaryTest` luật cấm màn hình tự đặt họ phông, vì chỗ dùng duy nhất (`ResultContent.kt:387`) vừa mất.
8. Dựng lại ba bảng trượt thêm người, chia một món, ai đã trả.
9. Trình sửa hóa đơn: hàng đóng hiện tên, số lượng nhân đơn giá và số tiền; hàng mở hiện bốn ô nhập và nút xóa. Sửa `BillLifecycleTest` và `fillInTheBill` theo cách mới: thêm một món thì điền ngay hàng vừa mở. Thêm vào `BillLifecycleTest` một ca xóa món khi hàng khác đang mở.
10. Dựng lại màn đòi tiền và thẻ chia sẻ; xem ảnh xuất thật có đủ thẻ vé.
11. Chuyển mười ba lời gọi thẳng thành phần Material trong danh sách.
12. Thêm phép kiểm thanh che nội dung của giai đoạn 2 vào `BillSafetyTest`, nơi hôm nay chỉ cuộn rồi đếm; thêm vào `SplitFlowOnDeviceTest` một bài gõ vào ô trong hàng khi bàn phím mở.
13. Sửa các bài kiểm thử còn lại trong danh sách; chạy lại `SplitFlowTest`, `AiConsentFlowTest`, `ReducedMotionTest`.
14. Xem ảnh trang chủ ở cỡ chữ 200% (`01-home-large-text`) cùng ba ảnh còn lại của bài đó, vì cả bốn màn thuộc giai đoạn này.
15. Chạy cổng kiểm thử của plan, nhờ một lượt review độc lập, sửa các phát hiện, rồi ghép ảnh sáng và tối gửi chủ sản phẩm so với bảng.

## Todo

- [ ] `HomeSummary` và `HomeSummaryTest`, có ca `null` so với rỗng
- [ ] Truy vấn nhóm ghi gần nhất trong `ProxyCacheDao`, `ProxyCache`, `GroupRepository`
- [ ] `HomeViewModel`, trang chủ theo hai bảng, bốn nơi gọi, bài sau đăng xuất
- [ ] Chữ theo bảng của quyết định 6, nhãn mới, bài so chuỗi xin đồng ý
- [ ] Màn thêm hóa đơn, đang đọc, xin đồng ý
- [ ] Màn kết quả theo thứ tự mới
- [ ] Gỡ phần xem chữ đã gửi; luật cấm tự đặt họ phông
- [ ] Ba bảng trượt
- [ ] Trình sửa hóa đơn mỗi lúc một hàng mở, giữ theo id
- [ ] Màn đòi tiền và thẻ chia sẻ
- [ ] Mười ba lời gọi thẳng thành phần Material
- [ ] Phép kiểm thanh che trong `BillSafetyTest`; bài gõ với bàn phím mở trên máy
- [ ] Các bài kiểm thử còn lại của luồng
- [ ] Bốn ảnh cỡ chữ 200% được xem
- [ ] Cổng kiểm thử, review, ảnh gửi chủ sản phẩm

## Success Criteria

- Cổng kiểm thử của plan qua (với `-Pscreenshots=compare`), gồm `HomeSummaryTest`, bài sau đăng xuất, bài so chuỗi xin đồng ý, `SplitFlowTest`, `BillLifecycleTest`, `BillSafetyTest`, `AiConsentFlowTest`, `ReducedMotionTest` và `DesignSystemBoundaryTest`.
- Lượt `device-tests.yml` trên commit cuối của giai đoạn: `SplitFlowOnDeviceTest` và bốn hành trình `e2e` qua trên máy ảo API 34; kết quả được đọc và ghi lại.
- Hóa đơn mẫu chia ra đúng các số của bảng `Result`, và tổng các phần bằng tổng hóa đơn.
- Một hóa đơn đã đọc bằng AI vẫn được coi là "đã đọc" sau khi mở lại từ lịch sử.
- Trong `strings.xml`, chữ "Claude" chỉ còn ở `ai_consent_where`.
- Trong `ui/home/`, `ui/split/` và `ui/editor/` không còn lời gọi thẳng `OutlinedTextField`, `FilledTonalButton`, `AlertDialog` hay `Checkbox`.
- Ở cỡ chữ 200%, không chữ nào bị cắt trên trang chủ, màn thêm hóa đơn, màn kết quả và màn đòi tiền.
- `git status --porcelain app/src/test/screenshots` rỗng; ảnh gửi chủ sản phẩm lấy từ `app/build/outputs/roborazzi/*_actual.png`.

## Risk Assessment

- Tổng trên trang chủ lệch với màn Requests: cả hai đọc cùng `RequestTotals` từ cùng một bộ giữ; giai đoạn 6 không viết phép cộng thứ hai.
- Trang chủ đổi từ khối chi tiêu sang khối "Friends owe you" khi danh sách về: chỉ xảy ra ở lần mở đầu sau khi đăng nhập, vì các lần sau bộ nhớ đệm đã có danh sách. Ảnh của trạng thái chưa tải được gửi chủ sản phẩm.
- Gộp người vào "Each person pays" làm mất đường bật tắt phần của "You": `SplitFlowTest` và hành trình nhập tay kiểm tra việc này.
- Thanh nổi hoặc bàn phím che ô đang gõ: phép kiểm trong `BillSafetyTest` và bài gõ trên máy ở bước 12 bắt lỗi này.
- Hàng đang mở của trình sửa nhảy sang hàng khác khi một món bị xóa: trạng thái giữ theo id, và `BillLifecycleTest` có một ca xóa món khi hàng khác đang mở.
- Một chuỗi xin đồng ý bị sửa nhầm trong lúc đổi chữ: bài so với bản chép chặn lại.
- Hai phần tử cùng mang "Split a bill" trên trang chủ lần đầu làm hỏng kiểm thử: nút mời chỉ có chữ, nút giữa chỉ có mô tả, và `AppStartupTest` khẳng định đúng một phần tử mang mô tả đó.
- Ảnh chia sẻ bị cắt khuyết của thẻ vé: bước 10 xem ảnh xuất thật.

## Rollback

Hoàn lại các commit của giai đoạn chỉ dùng được khi giai đoạn 6 chưa bắt đầu; sau đó lỗi của giai đoạn này được sửa bằng commit mới, vì các giai đoạn sau đã dựng trên nó. `ocrDocument` và mọi dữ liệu lưu không đổi dạng, và truy vấn mới của `ProxyCacheDao` không đổi lược đồ, nên hóa đơn cũ vẫn mở được ở cả hai phía.
