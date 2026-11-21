---
title: "Phase 7: Hóa đơn, thống kê, cài đặt và Pro"
status: pending
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
- [ ] Cổng kiểm thử, review, ảnh gửi chủ sản phẩm

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
- Thanh đo số lượt sai khi số lượt là số lẻ (0,2 lượt cho một lần sửa bằng câu): thanh làm tròn xuống theo đoạn, câu chữ vẫn nêu số chính xác.
- Luật cấm mới bắt nhầm một tên có đuôi giống tên bị cấm, hoặc bỏ sót một lời gọi viết khác kiểu: bài tự thử của `DesignSystemBoundaryTest` có mẫu cho cả hai trường hợp.
- Một chuỗi mà `ProScreenTest` hoặc `SettingsScreenTest` tìm bị đổi khi dựng lại: giữ nguyên chuỗi; hai bài chạy trong cổng kiểm thử.

## Rollback

Hoàn lại các commit của giai đoạn chỉ dùng được khi giai đoạn 8 chưa bắt đầu; sau đó lỗi của giai đoạn này được sửa bằng commit mới, vì các giai đoạn sau đã dựng trên nó. Hóa đơn định kỳ và mọi dữ liệu lưu không đổi dạng.
