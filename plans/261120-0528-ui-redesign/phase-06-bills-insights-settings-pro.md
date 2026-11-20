---
title: "Phase 6: Hóa đơn, thống kê, cài đặt và Pro"
status: pending
phase: 6
priority: P1
effort: "3d"
dependencies: [3]
---

# Phase 6: Hóa đơn, thống kê, cài đặt và Pro

## Overview

Danh sách hóa đơn và hóa đơn định kỳ thành hai thẻ của một màn. Thống kê có biểu đồ cột theo tháng. Cài đặt, hộp thoại xóa tài khoản và hai màn Pro mang bố cục của bản thiết kế, và app có chỗ hiển thị giấy phép của hai phông chữ.

## Context

- Cấu trúc hiện tại: [rà màn hình](./reports/scout-screens-and-state.md), mục 1.5 đến 1.7, 1.13, 1.14, 4 và 5.
- Yêu cầu của giấy phép phông: [tra cứu phông](./research/researcher-02-fonts.md), mục 1.
- Bảng tham chiếu trên canvas: `Bills`, `Recurring`, `SheetRepeat`, `Insights`, `SheetExport`, `Settings`, `SettingsStates`, `DeleteAccount`, `Pro`, `ProMember`.
- Gói do giai đoạn này sở hữu: `ui/history/`, `ui/recurring/`, `ui/insights/`, `ui/settings/`, `ui/paywall/`.

## Key Insights

- Lịch sử và định kỳ hôm nay là hai mục điều hướng với hai view model khác vòng đời: `HistoryViewModel` sống theo màn, `RecurringViewModel` sống theo Activity để việc lặp lại không mất khi rời màn. Gộp màn không được đổi vòng đời đó.
- Tìm kiếm chỉ có cho lịch sử. Thẻ "Recurring" vì thế không có ô tìm.
- `ProScreenTest` có 55 lời tìm phần tử và `SettingsScreenTest` có 36, phần lớn theo chữ. Giữ nguyên chữ của nút và tiêu đề để hai bài này chỉ phải sửa ở chỗ bố cục đổi.
- Khối đồng ý dùng AI trong Cài đặt chỉ hiện khi người dùng đã đồng ý, và nút của nó là "Take back".
- Giấy phép OFL đòi app mang theo dòng bản quyền và văn bản giấy phép; một màn hiển thị là cách dễ thấy nhất.
- Thống kê bỏ qua hóa đơn không có tỉ giá và nói rõ điều đó bằng hai lời nhắc; các lời nhắc phải ở lại.

## Requirements

- `Bills`: thanh trên có nút lùi; ô tìm; lựa chọn hai thẻ "All bills" và "Recurring"; hàng hóa đơn có ô danh mục (theo `CategoryStyle` của giai đoạn 2), ngày ngắn, người, số tiền, nhãn "Draft" và menu. Thẻ "Recurring" hiện câu giải thích và các hóa đơn định kỳ như bảng `Recurring`.
- Mở từ thông báo hoặc từ hành động "Repeat" vẫn đưa tới thẻ "Recurring".
- `Insights`: màn gốc của tab; lựa chọn kỳ; khối tổng; hai lời nhắc dạng thẻ trắng; biểu đồ cột cho các tháng trong kỳ, có mô tả bằng chữ; danh mục, người và nhóm có thanh tỉ lệ; nút "Export these bills".
- `Settings`: hàng hồ sơ; thẻ gói với nhãn gói, nút "Get Pro" hoặc "Manage Pro", câu số lượt còn lại và thanh đo; ô tiền tệ của máy và tên; công tắc dịch vụ giọng nói; khối đồng ý dùng AI; hàng "Ways to get paid"; hai liên kết pháp lý; hàng "Font licences"; "Save"; "Sign out"; "Delete account". Bốn trạng thái tài khoản theo `SettingsStates`.
- Hộp thoại xóa tài khoản giữ nguyên nội dung, ô chọn xóa dữ liệu trên máy mặc định không chọn, và nút xác nhận màu `error`.
- `Pro` và `ProMember` theo bảng; hai gói là hai lựa chọn có vai nút chọn một.
- Màn giấy phép phông hiện nguyên văn hai tệp `OFL.txt` từ tài nguyên của `:core:designsystem`.
- Nhãn mới: "All bills", "Recurring", "Font licences".

## Files to Create / Modify

- Create: `ui/settings/FontLicencesScreen.kt`; khóa `FontLicencesKey` và mục của nó trong `ui/AppNavHost.kt`.
- Modify: `ui/history/HistoryScreen.kt`, `HistoryViewModel.kt`; `ui/recurring/RecurringScreen.kt`, `RepeatBillSheet.kt`; `HistoryKey` thành lớp mang thẻ đang mở và bỏ `RecurringKey` trong `ui/AppNavHost.kt` (dòng 98, 129, 277-337).
- Modify: `ui/insights/InsightsScreen.kt`, `ExportSheet.kt`.
- Modify: `ui/settings/SettingsScreen.kt`; `ui/paywall/ProScreen.kt`, `ProTexts.kt`.
- Modify: `app/src/main/res/values/strings.xml` (các chuỗi `history_*`, `recurring_*`, `insights_*`, `settings_*`, `pro_*`).
- Modify tests: `ui/SpendingGalleryTest.kt`, `ui/ProGalleryTest.kt`, `ui/paywall/ProScreenTest.kt`, `ui/settings/SettingsScreenTest.kt`, `ui/recurring/RepeatBillSheetTest.kt`, `ui/PseudoLocaleGalleryTest.kt`, và phần lịch sử trong `ui/ScreenGalleryTest.kt`.

## Implementation Steps

1. Đổi `HistoryKey` thành lớp có tham số thẻ, mặc định là "All bills". Mọi chỗ mở `RecurringKey` chuyển sang mở `HistoryKey` với thẻ "Recurring". `RecurringViewModel` vẫn lấy theo Activity.
2. Dựng màn `Bills` với hai thẻ; nội dung mỗi thẻ dùng lại danh sách hiện có. Thẻ đang mở được giữ qua xoay màn hình và khôi phục tiến trình.
3. Dựng lại `RepeatBillSheet`; giữ nhãn "Change the day of the first draft" và "Repeat" mà `RepeatBillSheetTest` tìm.
4. Dựng lại `InsightsScreen`: biểu đồ `SvBarChart` lấy dữ liệu từ `Insights.byMonth`; các hàng danh mục, người, nhóm dùng `SvStatBar`.
5. Dựng lại `ExportSheet`.
6. Dựng lại `SettingsScreen` và hộp thoại xóa tài khoản; thanh đo lấy số lượt còn lại và tổng số lượt của gói. Thêm hàng và màn "Font licences".
7. Dựng lại `ProScreen` ở hai trạng thái; giữ mọi chữ mà `ProScreenTest` tìm.
8. Sửa các bài kiểm thử trong danh sách.
9. Dựng ảnh các màn của giai đoạn ra thư mục tạm, ghép bảng xem nhanh sáng và tối.

## Todo

- [ ] `HistoryKey` có thẻ, bỏ `RecurringKey`
- [ ] Màn `Bills` hai thẻ và bảng lặp lại
- [ ] `Insights` với biểu đồ và thanh tỉ lệ, bảng xuất file
- [ ] `Settings`, bốn trạng thái tài khoản, hộp thoại xóa tài khoản
- [ ] Màn giấy phép phông
- [ ] Hai màn Pro
- [ ] Kiểm thử của các màn này
- [ ] Bảng ảnh gửi chủ sản phẩm

## Success Criteria

- `./gradlew :app:testProdDebugUnitTest` qua khi bỏ các bài so ảnh, gồm `ProScreenTest`, `SettingsScreenTest` và `RepeatBillSheetTest`.
- Lặp lại một hóa đơn từ thẻ "All bills" mở thẻ "Recurring" với hóa đơn mới, và hóa đơn đó còn sau khi rời rồi quay lại màn.
- Tổng của biểu đồ theo tháng bằng tổng của khối đầu trang với cùng kỳ.
- Màn giấy phép phông hiện đủ hai dòng bản quyền.

## Risk Assessment

- Đổi `HistoryKey` làm trạng thái lưu cũ không khôi phục được: trạng thái điều hướng chỉ sống trong một phiên của Activity, nên chỉ ảnh hưởng lần mở đầu sau khi cập nhật; `AppStartupTest` chạy lại để chắc app mở bình thường.
- Biểu đồ cột khó đọc khi một tháng lớn gấp nhiều lần các tháng khác: cột có chiều cao tối thiểu và danh sách tháng bên dưới vẫn nêu số.
- Thanh đo số lượt sai khi số lượt là số lẻ (0,2 lượt cho một lần sửa bằng câu): thanh làm tròn xuống theo đoạn, câu chữ vẫn nêu số chính xác.

## Rollback

Hoàn lại các commit của giai đoạn. Hóa đơn định kỳ và mọi dữ liệu lưu không đổi dạng.
