---
title: "Phase 7: Hóa đơn, thống kê, cài đặt và Pro"
status: completed
phase: 7
priority: P1
effort: "3d"
dependencies: [6]
---

# Phase 7: Hóa đơn, thống kê, cài đặt và Pro

## Overview

Màn Bills với hai thẻ, bảng lặp lại hóa đơn, thống kê, bảng xuất file, cài đặt, hộp thoại xóa tài khoản và hai màn Pro mang bố cục của bản thiết kế. Đây là giai đoạn cuối chuyển các lời gọi thẳng thành phần Material, nên sau nó `DesignSystemBoundaryTest` cấm hẳn các lời gọi đó trong màn hình.

## Context

- Cấu trúc hiện tại: [rà màn hình](./reports/scout-screens-and-state.md), mục 1.5 đến 1.7, 1.13, 1.14, 4 và 5.
- Bằng chứng kèm tệp và dòng cho các điểm dưới đây: [phản biện về phạm vi](./reports/red-team-scope.md), [về giả định](./reports/red-team-assumptions.md), [về kiểu hỏng](./reports/red-team-failure-modes.md).
- Bảng tham chiếu trong [`boards/`](./boards/README.md): [`Bills`](./boards/Bills.dc.html), [`Recurring`](./boards/Recurring.dc.html), [`SheetRepeat`](./boards/SheetRepeat.dc.html), [`Insights`](./boards/Insights.dc.html), [`SheetExport`](./boards/SheetExport.dc.html), [`Settings`](./boards/Settings.dc.html), [`SettingsStates`](./boards/SettingsStates.dc.html), [`DeleteAccount`](./boards/DeleteAccount.dc.html), [`Pro`](./boards/Pro.dc.html), [`ProMember`](./boards/ProMember.dc.html).
- Gói do giai đoạn này sở hữu: `ui/history/`, `ui/recurring/`, `ui/insights/`, `ui/settings/`, `ui/paywall/`.
- Thứ dùng lại từ các giai đoạn trước: `SvDialog`, `SvQuietButton`, `SvTextField`, `SvSwitch`, `SvCheckbox`, `SvRadio`, `SvMeter`, `SvStatBar`, `SvBarChart`, `CategoryStyle` và phép kiểm thanh che nội dung của giai đoạn 2; màn Bills hai thẻ và thanh điều hướng của giai đoạn 3; cách viết tiền và ngày của giai đoạn 4.

## Key Insights

- Từ giai đoạn 3, Bills và Recurring là hai thẻ của một màn: `HistoryKey` vẫn là `data object`, thẻ đang chọn là trạng thái của màn giữ bằng `rememberSaveable`, "Repeat" đổi thẻ tại chỗ, và `RecurringKey` không còn. `HistoryViewModel` sống theo màn, còn `RecurringViewModel` sống theo Activity để việc lặp lại không mất khi rời màn. Giai đoạn này chỉ đổi diện mạo và không đụng tới cấu trúc đó.
- Tìm kiếm chỉ có cho danh sách hóa đơn. Thẻ "Recurring" vì thế không có ô tìm.
- `ProScreenTest` có 55 lời tìm phần tử và `SettingsScreenTest` có 34, phần lớn theo chữ. Giữ nguyên chữ của nút và tiêu đề để hai bài này chỉ phải sửa ở chỗ bố cục đổi.
- Khối đồng ý dùng AI trong Cài đặt chỉ hiện khi người dùng đã đồng ý, và nút của nó là "Take back". Chuỗi `settings_ai_consent_given` giữ nguyên từng ký tự; bài so chuỗi của giai đoạn 5 canh việc này.
- Bảng `Settings` không có hàng nào về giấy phép phông, và giai đoạn này không thêm màn, hàng, khóa điều hướng hay chuỗi nào cho việc đó: hai tệp giấy phép đã đi kèm app từ giai đoạn 1, đủ theo điều khoản của giấy phép.
- Insights là màn gốc có thanh điều hướng. Nút "Export these bills" hôm nay nằm trong một `SvBottomBar` (`InsightsScreen.kt:123`), và từ giai đoạn 3 thanh đó xếp chồng phía trên thanh điều hướng trong cùng ô `bottomBar`. Giai đoạn này đưa nút vào nội dung để màn chỉ còn một thanh. `SpendingGalleryTest` mở bảng xuất file bằng cách bấm đúng chữ đó, nên nhãn giữ nguyên.
- Thống kê bỏ qua hóa đơn không có tỉ giá và nói rõ điều đó bằng hai lời nhắc; các lời nhắc phải ở lại.
- Hai câu có ngày trong Cài đặt và Pro, "They come back on …" và "Renews on …", theo quy tắc ngày của giai đoạn 4. Trên máy đặt vùng Mỹ chúng viết "December 1" và "December 17, 2026", khác chữ "1 December" trên bốn bảng `Settings`, `SettingsStates`, `Pro` và `ProMember`; đó là quy tắc, không phải lỗi dựng màn.
- `ProScreen.kt:140` và `:161` đang dùng kiểu chữ Emphasized. Khi dựng lại, chúng lấy kiểu chữ của bảng `Pro`, và không kiểu nào rơi về phông mặc định.

## Requirements

- `Bills`: thanh trên có nút lùi; ô tìm; hai thẻ "All bills" và "Recurring" có từ giai đoạn 3; hàng hóa đơn có ô danh mục theo `CategoryStyle`, ngày ngắn của giai đoạn 4, người, số tiền, nhãn "Draft" và menu. Thẻ "Recurring" hiện câu giải thích và các hóa đơn định kỳ như bảng `Recurring`.
- Hai thẻ "All bills" và "Recurring" được trình đọc màn hình đọc là thẻ (vai `Tab`, đang chọn hay không), như các mục của thanh điều hướng. Ở giai đoạn 3 chúng là một `SvChoiceGroup`, đọc là nút chọn một. Thẻ vẫn nằm trong cùng một danh sách với khóa cố định, để tiêu điểm của trình đọc và bàn phím ở lại thẻ vừa chọn.
<!-- Updated: Review giai đoạn 3 - hai thẻ của màn Bills đọc là nút chọn một thay vì thẻ -->
- Bảng lặp lại hóa đơn theo `SheetRepeat`; lặp lại một hóa đơn vẫn đổi sang thẻ "Recurring" ngay tại chỗ, không mở thêm màn nào.
- `Insights`: màn gốc của tab; lựa chọn kỳ; khối tổng; hai lời nhắc dạng thẻ trắng; biểu đồ cột cho các tháng trong kỳ, có mô tả bằng chữ; danh mục, người và nhóm có thanh tỉ lệ.
- Nút "Export these bills" là một nút nằm ngay trong nội dung, ở cuối danh sách như bảng `Insights`, không còn là `SvBottomBar`. Sau khi cuộn tới, đáy của nút nằm trên đỉnh thanh điều hướng.
- `Settings`: hàng hồ sơ; thẻ gói với nhãn gói, nút "Get Pro" hoặc "Manage Pro", câu số lượt còn lại và thanh đo; ô tiền tệ của máy và tên; công tắc dịch vụ giọng nói; khối đồng ý dùng AI; hàng "Ways to get paid"; hai liên kết pháp lý; "Save"; "Sign out"; "Delete account". Bốn trạng thái tài khoản theo `SettingsStates`.
- Hộp thoại xóa tài khoản dùng biến thể của `SvDialog` cho việc không hoàn lại được, theo bảng `DeleteAccount`: nội dung giữ nguyên, ô chọn xóa dữ liệu trên máy mặc định không chọn, nút xác nhận màu `error`.
- `Pro` và `ProMember` theo bảng; hai gói là hai lựa chọn có vai nút chọn một. `SvBottomBar` của màn (`ProScreen.kt:342`) ở lại trong ô `bottomBar` của `Scaffold`.
- Mọi ô nhập được chuyển giữ tùy chọn bàn phím và giới hạn độ dài đang có; ô tiền tệ của máy vẫn tắt tự sửa chính tả (`SettingsScreen.kt:140-154`).

Lời gọi thẳng thành phần Material được chuyển trong giai đoạn này (số dòng theo mã ngày 20/11/2026; giai đoạn 3 đã sửa hai tệp đầu nên dòng có thể lệch):
- `ui/history/HistoryScreen.kt`: `OutlinedTextField` `:92`, `AlertDialog` `:155`.
- `ui/recurring/RecurringScreen.kt`: `AlertDialog` `:110`.
- `ui/recurring/RepeatBillSheet.kt`: `OutlinedTextField` `:89`.
- `ui/settings/SettingsScreen.kt`: `OutlinedTextField` `:138`, `:152`; `AlertDialog` `:290`; `Checkbox` `:305`; `FilledTonalButton` `:340`; `Switch` `:366`.
- `ui/paywall/ProScreen.kt`: `RadioButton` trong hàng chọn gói. Bản phản biện về phạm vi đếm một `RadioButton` nhưng không nêu chỗ; đọc lại mã thì nó ở đây, và luật mới bên dưới sẽ hỏng nếu nó còn.
- Ô nhập thành `SvTextField` (ô tìm dùng biểu tượng đầu ô mà giai đoạn 2 đã cho), hộp thoại thành `SvDialog`, ô chọn thành `SvCheckbox`, công tắc thành `SvSwitch`, nút chọn một thành `SvRadio`, `FilledTonalButton` thành `SvQuietButton`.
- Sau lời gọi cuối cùng, `DesignSystemBoundaryTest` cấm `OutlinedTextField`, `Checkbox`, `RadioButton`, `Switch`, `AlertDialog` và `FilledTonalButton` trong màn hình. Luật khớp theo ranh giới từ, để `SvSwitch` hay `SpeechServiceSwitch` không bị bắt nhầm.

## Files to Create / Modify

Đường dẫn ngắn tính từ `app/src/main/java/com/dantech/splitvibe/` và, với kiểm thử, từ `app/src/test/java/com/dantech/splitvibe/`.

- Modify: `ui/history/HistoryScreen.kt`; `ui/recurring/RecurringScreen.kt`, `RepeatBillSheet.kt`. `HistoryKey`, `HistoryViewModel` và `RecurringViewModel` không đổi.
- Modify: `ui/insights/InsightsScreen.kt`, `ExportSheet.kt`.
- Modify: `ui/settings/SettingsScreen.kt`; `ui/paywall/ProScreen.kt`, `ProTexts.kt`.
- Modify: `app/src/main/res/values/strings.xml` (các chuỗi `history_*`, `recurring_*`, `insights_*`, `settings_*`, `pro_*`).
- Màn nào đổi chữ ký thì sửa luôn nơi gọi của nó. `ProScreen`: `entry<ProKey>`, `text/NumbersInTextsTest.kt:136`, `ui/ProGalleryTest.kt`, `ui/PseudoLocaleGalleryTest.kt`, `ui/paywall/ProScreenTest.kt`. `SettingsScreen`: `entry<SettingsKey>`, `text/NumbersInTextsTest.kt:142`, `ui/ScreenGalleryTest.kt`, `ui/ProGalleryTest.kt`, `ui/PseudoLocaleGalleryTest.kt`, `ui/settings/SettingsScreenTest.kt`. `HistoryScreen`: `entry<HistoryKey>`, `text/NumbersInTextsTest.kt:155`, `ui/ScreenGalleryTest.kt`, `ui/SpendingGalleryTest.kt`. `InsightsScreen`: `entry<InsightsKey>`, `ui/SpendingGalleryTest.kt`.
- Modify tests: `ui/SpendingGalleryTest.kt` (cuộn tới nút "Export these bills" trước khi bấm; bài chụp hóa đơn định kỳ vẽ màn Bills với thẻ "Recurring" đang mở, là ảnh mà giai đoạn 8 cần), `ui/ProGalleryTest.kt`, `ui/paywall/ProScreenTest.kt`, `ui/settings/SettingsScreenTest.kt`, `ui/recurring/RepeatBillSheetTest.kt`, `ui/PseudoLocaleGalleryTest.kt`, phần lịch sử và cài đặt trong `ui/ScreenGalleryTest.kt`, và `ui/DesignSystemBoundaryTest.kt` (luật mới cùng các mẫu thử của chính luật đó).

## Implementation Steps

1. Dựng lại màn Bills với hai thẻ theo bảng `Bills` và `Recurring`; nội dung mỗi thẻ dùng lại danh sách hiện có. Không đổi cách giữ thẻ đang chọn của giai đoạn 3.
2. Dựng lại `RepeatBillSheet`; giữ nhãn "Change the day of the first draft" và "Repeat" mà `RepeatBillSheetTest` tìm.
3. Dựng lại `InsightsScreen`: biểu đồ `SvBarChart` lấy dữ liệu từ `Insights.byMonth`; các hàng danh mục, người, nhóm dùng `SvStatBar`; nút "Export these bills" vào cuối nội dung. Thêm phép kiểm thanh che nội dung của giai đoạn 2 cho nút đó.
4. Dựng lại `ExportSheet`.
5. Dựng lại `SettingsScreen` và hộp thoại xóa tài khoản; thanh đo lấy số lượt còn lại và tổng số lượt của gói.
6. Dựng lại `ProScreen` ở hai trạng thái; giữ mọi chữ mà `ProScreenTest` tìm.
7. Chuyển mười một lời gọi thẳng thành phần Material trong danh sách, rồi thêm luật cấm vào `DesignSystemBoundaryTest` cùng mẫu bị cấm và mẫu được phép trong bài tự thử của nó.
8. Sửa các bài kiểm thử trong danh sách và các nơi gọi của màn đổi chữ ký.
9. Chạy cổng kiểm thử của plan, nhờ một lượt review độc lập, sửa các phát hiện, rồi ghép ảnh sáng và tối gửi chủ sản phẩm so với bảng.

## Todo

- [x] Màn Bills hai thẻ theo bảng
- [x] Bảng lặp lại hóa đơn
- [x] `Insights` với biểu đồ, thanh tỉ lệ và nút xuất nằm trong nội dung; phép kiểm thanh che
- [x] Bảng xuất file
- [x] `Settings`, bốn trạng thái tài khoản, hộp thoại xóa tài khoản
- [x] Hai màn Pro
- [x] Mười một lời gọi thẳng thành phần Material; luật cấm trong `DesignSystemBoundaryTest`
- [x] Kiểm thử của các màn này và nơi gọi của màn đổi chữ ký
- [x] Cổng kiểm thử, review, ảnh gửi chủ sản phẩm

## Success Criteria

- Cổng kiểm thử của plan qua (với `-Pscreenshots=compare`), gồm `ProScreenTest`, `SettingsScreenTest`, `RepeatBillSheetTest`, `NumbersInTextsTest` và `DesignSystemBoundaryTest` với luật mới.
- Lượt `device-tests.yml` trên commit cuối của giai đoạn qua trên máy ảo API 34; kết quả được đọc và ghi lại.
- Trong `app/src/main` và `app/src/debug` không còn lời gọi thẳng nào tới sáu thành phần bị cấm.
- Lặp lại một hóa đơn từ thẻ "All bills" đổi sang thẻ "Recurring" với hóa đơn mới; một lần lùi đưa về màn trước, và hóa đơn đó còn sau khi rời rồi quay lại màn.
- Tổng của biểu đồ theo tháng bằng tổng của khối đầu trang với cùng kỳ, và `InsightsViewModelTest` có một phép khẳng định cho điều đó.
- `git status --porcelain app/src/test/screenshots` rỗng; ảnh gửi chủ sản phẩm lấy từ `app/build/outputs/roborazzi/*_actual.png`.

## Risk Assessment

- Nút "Export these bills" nằm dưới thanh điều hướng hoặc không cuộn tới được: phép kiểm ở bước 3 bắt lỗi này.
- Biểu đồ cột khó đọc khi một tháng lớn gấp nhiều lần các tháng khác: cột có chiều cao tối thiểu và danh sách tháng bên dưới vẫn nêu số.
- Thanh đo số lượt sai khi số lượt là số lẻ (0,2 lượt cho một lần sửa bằng câu): đoạn đang dùng dở được tô một phần như các bảng vẽ, câu chữ vẫn nêu số chính xác. <!-- Updated: SvMeter theo bảng tô một phần đoạn, không làm tròn xuống -->
- Một hóa đơn có ngày sai (gõ nhầm năm 2925, hay năm Phật lịch 2568 trên hóa đơn Thái) làm biểu đồ "All time" dài hàng nghìn tháng: quy tắc ngày dùng được và biểu đồ theo năm của "All time" trong phần kết quả chặn việc này. <!-- Updated: thêm theo review -->
- Lời báo kết quả nằm xa nút đã bấm khi nút ở cuối trang dài (xuất file, đăng xuất, xóa tài khoản): lời báo hiện ngay trên nút, có vùng đọc tự động. <!-- Updated: thêm theo review -->
- Luật cấm mới bắt nhầm một tên có đuôi giống tên bị cấm, hoặc bỏ sót một lời gọi viết khác kiểu: bài tự thử của `DesignSystemBoundaryTest` có mẫu cho cả hai trường hợp.
- Một chuỗi mà `ProScreenTest` hoặc `SettingsScreenTest` tìm bị đổi khi dựng lại: giữ nguyên chuỗi; hai bài chạy trong cổng kiểm thử.

## Rollback

Hoàn lại các commit của giai đoạn chỉ dùng được khi giai đoạn 8 chưa bắt đầu; sau đó lỗi của giai đoạn này được sửa bằng commit mới, vì các giai đoạn sau đã dựng trên nó. Hóa đơn định kỳ và mọi dữ liệu lưu không đổi dạng.

## Kết quả thực hiện

Làm ngày 21/11/2026, trong các commit `565e8ec` (hóa đơn, thống kê, cài đặt và Pro), `2201c71` (sửa theo review) và `7031bb7` (thẻ kết quả xuất file hiện trọn, ảnh "All time").

- Cổng kiểm thử của plan qua với `-Pscreenshots=compare` trên `7031bb7`: 699 bài kiểm thử đơn vị của `:app`, cùng các bài của `:core:domain` và `:core:designsystem`, ktlint, lint, các gói, bản dựng kiểm thử trên máy và kiểm tra gói phát hành. `git status --porcelain app/src/test/screenshots` rỗng.
- Trong `app/src/main` và `app/src/debug` không còn lời gọi thẳng nào tới sáu thành phần bị cấm. `DesignSystemBoundaryTest` cấm chúng theo ranh giới từ, có mẫu ở cả hai phía của ranh giới, và đòi có tệp trong từng cây mà nó quét.
- Tổng của biểu đồ bằng tổng của khối đầu trang ở cả ba kỳ, kể cả "All time" theo năm, và `InsightsViewModelTest` khẳng định điều đó.
- Kết quả trên máy:
  - Trên `565e8ec`, lượt `device-tests.yml` 44853783766 (Android 14 và hồ sơ khởi động) và 44853782265 (Android 8.0, 11 và 16) đều qua.
  - Trên `7031bb7`, lượt 44867974668 (Android 14 và hồ sơ khởi động) và 44867974119 (Android 8.0, 11 và 16) đều qua.
  - TalkBack chưa được thử trên máy thật. Nhóm nút chọn gói của Pro ("1 of 2") và lời báo đăng nhập xong mới được kiểm bằng bài kiểm thử trên cây ngữ nghĩa.
- Đã sửa sau lượt [review độc lập](./reports/review-phase-07.md); mỗi bài kiểm mới hay được sửa đều được cho chạy hỏng một lần:
  - Kết quả của việc xuất file hiện ngay trên nút "Export these bills", và danh sách cuộn để thấy trọn thẻ cùng nút, ngay lập tức khi tắt chuyển động. Tiến trình và kết quả của đăng xuất và xóa tài khoản hiện ngay trên nút Sign out. Cả hai được đọc tự động khi hiện.
  - Lời báo đăng nhập nằm trong một phần tử duy nhất đổi chữ tại chỗ, nên TalkBack đọc người vừa đăng nhập như trước.
  - Hai gói của Pro là một nhóm nút chọn một với trình đọc màn hình. Nút chọn một (`SvRadio`) tròn như bảng `Pro`: chấm chanh có dấu tích khi chọn, vòng xám khi chưa. Hàng chọn người (`SvPersonChoiceRow`) cũng là nút chọn một nên dùng dấu tròn này; màn tham gia nhóm, bảng chọn người trả, bảng thêm vào nhóm, bảng cách nhận tiền và màn đòi tiền đổi theo.
  - Ngày của hóa đơn chỉ được dùng khi nằm từ 1/1/2000 tới hôm nay. Ngoài khoảng đó, như khi gõ nhầm năm hay hóa đơn ghi năm Phật lịch, hóa đơn được tính vào ngày lưu, và lời nhắc về các hóa đơn này đếm cả chúng, ở mọi kỳ và trong file xuất.
  - "All time" vẽ một cột mỗi năm, từ năm đầu có hóa đơn tới năm nay, và danh sách dưới biểu đồ theo năm. Kỳ nào chỉ có một cột thì không vẽ biểu đồ.
  - Quay lại thẻ "All bills" thì ô tìm lại ở đầu danh sách: ô tìm và hai thẻ là một phần tử của danh sách.
  - Hàng của Recurring và các hàng của Insights đưa số tiền xuống dưới tên khi chữ dài nhất của tên không vừa bên cạnh, như hàng của All bills, qua `SvBesideOrBelow`.
  - Ô "No group" và vòng của nút chọn một có token màu riêng, `blankTile` và `radioRing`. Các liên kết pháp lý của Cài đặt dùng lại `PageLinks`.
  - Bài kiểm điều hướng cuộn tới Sign out trước khi bấm, và báo lỗi khi việc xóa bộ nhớ đệm hỏng.
- Các giá trị mà giai đoạn để ngỏ, do người thực hiện hoặc người điều phối chọn:
  - Thành phần mới hoặc mở rộng mà giai đoạn không liệt kê: `SvTabs`, nhãn không bắt buộc của `SvTextField`, `SvQuietIconTile` với ba biểu tượng Repeat, Export và None, `SvTextAction(destructive)`. Đoạn trống của `SvMeter` trên nền trang dùng `outlineVariant` như bảng `ProMember`; `SvBarChart` làm mảnh cột và bỏ bớt nhãn khi có nhiều cột.
  - Số tổng của khối đầu trang Insights đứng yên như mọi khối đầu trang khác, không còn đếm lên khi đổi kỳ.
  - Hàng hóa đơn của màn Bills ghi tên danh mục bằng chữ như bảng `Bills`; hàng của trang chủ không đổi.
  - Cài đặt có hàng hồ sơ khi đã đăng nhập; Sign out và Delete account nằm cuối trang; "Get Pro" màu tím chỉ khi đã đăng nhập mà chưa có Pro; các liên kết quyền riêng tư và điều khoản thành hàng trong thẻ. Một lần xóa tài khoản bắt đầu ở lần mở trước, hay bị đóng app giữa chừng, hiện ở thẻ đầu trang, nơi người dùng thấy đầu tiên khi mở Cài đặt.
  - Pro: thanh đo chỉ hiện với người đã mua; các liên kết pháp lý vẫn giữ dù bảng không vẽ; giá gói dùng `amountMedium` (19sp) thay vì 22sp của bảng.
  - Ô tìm của Bills giữ giới hạn 80 ký tự của `SvTextField`; trước đây ô này không có giới hạn.
  - `RepeatBillSheet` vẫn gọi `DatePickerDialog` của Material, vì hệ thiết kế không có bộ chọn ngày và luật cấm chỉ nêu sáu thành phần.
  - Chuỗi mới: "By year", "Spending by year from %1$s to %2$s.", ba chuỗi `insights_chart*` và hai chuỗi số nhiều `settings_calls_meter_*`. Lời nhắc về hóa đơn không có ngày đổi thành "%d bill has no date that can be used, so it is counted on the day it was saved." cùng dạng số nhiều.
- Còn mở, chờ chủ sản phẩm duyệt qua ảnh:
  - Kỳ "This month" không có biểu đồ, chỉ còn danh sách một tháng (`40-insights-this-month`).
  - "All time" theo năm thay vì theo tháng (`40-insights-all-time`).
  - Chữ mới của lời nhắc về hóa đơn không có ngày dùng được.
  - Số tổng không còn đếm lên khi đổi kỳ; tên danh mục chỉ ghi ở màn Bills, không ở trang chủ; đoạn trống đậm hơn của thanh đo trên nền trang.
  - Dấu chọn tròn ở các màn của giai đoạn 5 và 6 dùng hàng chọn người.
  - Ở cỡ chữ 200%, địa chỉ email trong Cài đặt bị bẻ giữa chữ vì nó là một chữ dài, và ba nút kỳ của Insights xuống hai dòng.
  - Khi người dùng cuộn lên trong lúc file còn đang lưu, danh sách vẫn cuộn xuống cuối khi thẻ kết quả hiện.
