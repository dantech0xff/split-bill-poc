---
title: "Phase 2: Thành phần dùng chung"
status: pending
phase: 2
priority: P1
effort: "4d"
dependencies: [1]
---

# Phase 2: Thành phần dùng chung

## Overview

Các thành phần `Sv…` hiện có mang hình dáng mới, và các thành phần mà bản thiết kế cần thêm được dựng trong `:core:designsystem`. Màn hình vẫn không tự đặt màu, bóng hay chuyển động nào.

## Context

- Danh sách thành phần, chỗ dùng và luật của `DesignSystemBoundaryTest`: [rà hệ thiết kế](./reports/scout-design-system-navigation.md), mục 3 và 5.
- Bảng tham chiếu trên canvas: `Main`, `NewSplit`, `Result`, `AskForMoney`, `Group`, `Requests`, `Insights`, `Settings`, `Pro`, `SheetItemSplit`, `DeleteAccount`.

## Key Insights

- `DesignSystemBoundaryTest` cấm màn hình tự viết màu, bóng, nút nổi, menu thả và chuyển động. Mọi hình dáng mới vì thế phải nằm trong module này.
- `SvBottomBar` được mô tả là thanh nổi nhưng mã là một dải chữ nhật kín chiều ngang (`SvPrimaryButton.kt:46`). Bản thiết kế cần thanh nổi thật.
- Các bài kiểm thử tìm phần tử bằng chữ và mô tả, không có `testTag`. Thành phần mới phải mang vai và nhãn trợ năng đúng để kiểm thử và trình đọc màn hình cùng dùng.
- `SvFabMenu` và `SvFloatingToolbar` chỉ có trong trang mẫu; chúng nhận token mới, không cần thiết kế lại.

## Requirements

Thành phần hiện có, hình dáng mới:

| Thành phần | Thay đổi |
|---|---|
| `SvCard` | Phẳng, bo `large`, nền `surfaceContainerLowest`; vẫn nhận màu |
| `SvListRow` | Các hàng liền nhau trong một thẻ trắng, góc nhóm 24 dp, không vạch ngăn |
| `SvSectionHeader` | Tiêu đề `titleLarge`, gợi ý `bodySmall` màu `onSurfaceVariant` |
| `SvPrimaryButton` | Viên thuốc cao 56 dp, màu `primary` |
| `SvBottomBar` | Thanh nổi màu `bar`, bo tròn hết cỡ, cách mép 20 dp, bóng mức 3; nút chính bên trong màu `accent` |
| `SvSplitButton` | Nút chính `accent` cùng nút tròn mũi tên, đặt trong `SvBottomBar` |
| `SvPrimaryFab` | Viên thuốc có biểu tượng, màu `primary` |
| `SvChoiceGroup`, `SvChoiceChips` | Viên thuốc cao 44 dp; mục đang chọn màu `secondaryContainer`; `SvChoiceChips` có kiểu cuộn ngang một hàng |
| `SvAvatar` | Ô vuông bo góc, chữ cái màu `onPerson`; thêm `SvAvatarStack` |
| `SvPersonPickRow`, `SvPersonChoiceRow` | Ô chọn và nút chọn một kiểu mới: ô 24 dp bo 8 dp, khi chọn là nền mực với dấu tích xanh chanh |
| `SvBottomSheet` | Bo trên 32 dp, tay nắm, tiêu đề `headlineSmall`, hàng nút ở đáy |
| `SvMessageCard` | Thẻ trắng với ô biểu tượng màu `notice`; lỗi dùng ô màu `error` |
| `SvEmptyState`, `SvSuccessMark`, `SvWorkingRow`, `SvBillPhoto` | Theo bảng `HomeEmpty`, `AskForMoney`, `Working`, `NewSplit` |
| `SvLargeTopBar` | Tách thành thanh của màn gốc (tiêu đề `displaySmall`) và thanh của màn con (nút lùi tròn, tiêu đề `headlineMedium`, nút phụ tròn) |

Thành phần mới:

| Thành phần | Mô tả |
|---|---|
| `SvNavBar` | Thanh điều hướng nổi: bốn mục có vai `Tab` và trạng thái đang chọn, một nút giữa có nhãn; mục có thể mang số đếm |
| `SvHero` | Khối bo 32 dp với ba kiểu `brand`, `accent`, `bar`; có chỗ cho nhãn, số tiền và nội dung phụ |
| `SvHeroAmount` | Số tiền lớn tự co cỡ chữ khi dài, ký hiệu tiền tệ nhỏ hơn; trình đọc màn hình nhận nguyên số tiền |
| `SvTile`, `SvTag`, `SvIconTile` | Ô thông tin 124 dp, nhãn nhỏ dạng viên thuốc, ô biểu tượng 44 dp nhận màu |
| `SvQuietButton`, `SvSmallAction`, `SvTextAction` | Nút phụ nền `surfaceContainer` hoặc trắng, nút hành động nhỏ màu mực chữ xanh chanh, nút chữ gạch chân |
| `SvTextField`, `SvSwitch`, `SvCheckbox`, `SvRadio` | Ô nhập viền `outline` bo 16 dp cao 52 dp với nhãn phía trên; công tắc và ô chọn theo token |
| `SvMeter`, `SvStatBar`, `SvBarChart` | Thanh đo chia đoạn, thanh tỉ lệ, biểu đồ cột; mỗi cái có mô tả bằng chữ cho trình đọc màn hình |
| `SvTicket` | Khung thẻ vé có hai khuyết tròn và đường đứt, dùng cho thẻ chia tiền |

Ràng buộc chung: vùng chạm tối thiểu 48 dp; mọi nút chỉ có biểu tượng có nhãn; không thành phần nào tự chạy khi `LocalReducedMotion` bật; thời lượng lấy từ `SvMotion` và `motionScheme`.

## Files to Create / Modify

- Modify: mọi tệp trong `core/designsystem/src/main/java/com/dantech/splitvibe/designsystem/components/` có trong bảng thứ nhất.
- Create trong cùng thư mục: `SvNavBar.kt`, `SvHero.kt`, `SvTile.kt`, `SvButtons.kt`, `SvFields.kt`, `SvMeter.kt`, `SvBarChart.kt`, `SvTicket.kt`.
- Modify: `app/src/main/java/com/dantech/splitvibe/ui/common/InputFields.kt` để `InputField` và `NumberField` dùng `SvTextField`.
- Create: `app/src/main/java/com/dantech/splitvibe/ui/common/CategoryStyle.kt` (biểu tượng và màu `SvPalette.category` cho từng `BillCategory`), để các giai đoạn 4 và 6 cùng dùng.
- Modify: `app/src/debug/java/com/dantech/splitvibe/catalog/CatalogScreen.kt` (thêm mọi thành phần mới).
- Modify: `app/src/test/java/com/dantech/splitvibe/ui/ComponentBehaviourTest.kt`, `ComponentCatalogTest.kt`.

## Implementation Steps

1. Đổi hình dáng các thành phần hiện có theo bảng, giữ nguyên tham số để màn hình vẫn biên dịch. Nơi phải đổi tham số, sửa luôn các chỗ gọi.
2. Dựng các thành phần mới, mỗi cái kèm chú thích khi nào dùng và một mục trong trang mẫu.
3. `SvNavBar`: mỗi mục là một phần tử có vai `Tab`, nhãn là tên mục, trạng thái chọn đọc được; nút giữa là nút có nhãn riêng. Số đếm được gộp vào nhãn của mục.
4. `SvHeroAmount`: dùng cơ chế tự co cỡ chữ của Compose với các bậc từ 56 xuống 32 sp; không cắt chữ và không xuống dòng.
5. Viết kiểm thử hành vi: vai và trạng thái của `SvNavBar`; nhãn của `SvHeroAmount`; `SvMeter` và `SvBarChart` có mô tả chữ; ô chọn và công tắc đổi trạng thái; nút chia đôi vẫn mở menu.
6. Rà những chỗ màn hình gọi thẳng thành phần Material chưa bọc (ô nhập, công tắc, ô chọn, `AlertDialog`) và chuyển sang thành phần `Sv…` hoặc xác nhận chúng trông đúng với token mới.
7. Dựng trang mẫu sáng và tối ra thư mục tạm, ghép bảng xem nhanh.

## Todo

- [ ] Thành phần hiện có đổi hình dáng
- [ ] Thành phần mới và mục trong trang mẫu
- [ ] Kiểm thử hành vi và trợ năng của thành phần
- [ ] Màn hình không còn gọi thẳng ô nhập, công tắc, ô chọn của Material
- [ ] Bảng ảnh trang mẫu gửi chủ sản phẩm

## Success Criteria

- `./gradlew :core:designsystem:testDebugUnitTest :app:testProdDebugUnitTest` qua khi bỏ các bài so ảnh; `ComponentBehaviourTest` và `DesignSystemBoundaryTest` qua.
- Trang mẫu có mọi thành phần của hai bảng trên, ở cả giao diện sáng và tối.
- Luồng chia hóa đơn mẫu vẫn chạy trọn trong `SplitFlowTest` và `ReducedMotionTest`.

## Risk Assessment

- Đổi `SvBottomBar` thành thanh nổi làm nội dung cuối danh sách bị che: mỗi màn dùng thanh này phải có lề đáy bằng chiều cao thanh; các bài kiểm thử cuộn của giai đoạn 4 đến 6 bắt lỗi này.
- Cơ chế tự co cỡ chữ không có trong bản Compose đang ghim: dấu hiệu là lỗi biên dịch. Ứng phó: đo chữ bằng `TextMeasurer` và chọn bậc cỡ thủ công.
- Thành phần Material bên trong đổi nghĩa màu: bước 6 rà từng chỗ.

## Rollback

Hoàn lại các commit của giai đoạn. Màn hình chưa đổi bố cục nên không có phụ thuộc ngược.
