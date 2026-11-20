---
title: "Phase 2: Thành phần dùng chung"
status: completed
phase: 2
priority: P1
effort: "4d"
dependencies: [1]
---

# Phase 2: Thành phần dùng chung

## Overview

Các thành phần `Sv…` hiện có mang hình dáng mới, và các thành phần mà bản thiết kế cần thêm, kể cả thanh điều hướng và hộp thoại, được dựng trong `:core:designsystem`. Màn hình vẫn không tự đặt màu, bóng hay chuyển động nào. Trong mã của app, giai đoạn này chỉ chuyển `ui/common/InputFields.kt` và một nút trong thanh ở `ui/split/AiConsent.kt:82` sang thành phần mới; các chỗ màn hình gọi thẳng Material khác do giai đoạn sở hữu gói đó chuyển (5, 6, 7).

## Context

- Danh sách thành phần, chỗ dùng và luật của `DesignSystemBoundaryTest`: [rà hệ thiết kế](./reports/scout-design-system-navigation.md), mục 3 và 5.
- Bằng chứng kèm tệp và dòng cho các yêu cầu dưới đây: phản biện về [giả định](./reports/red-team-assumptions.md), [kiểu hỏng](./reports/red-team-failure-modes.md), [phạm vi](./reports/red-team-scope.md) và [bảo mật](./reports/red-team-security.md).
- Bảng tham chiếu: [`Main`](./boards/Main.dc.html), [`HomeEmpty`](./boards/HomeEmpty.dc.html), [`NewSplit`](./boards/NewSplit.dc.html), [`Consent`](./boards/Consent.dc.html), [`Working`](./boards/Working.dc.html), [`Result`](./boards/Result.dc.html), [`AskForMoney`](./boards/AskForMoney.dc.html), [`Group`](./boards/Group.dc.html), [`Requests`](./boards/Requests.dc.html), [`Insights`](./boards/Insights.dc.html), [`Settings`](./boards/Settings.dc.html), [`Pro`](./boards/Pro.dc.html), [`SheetItemSplit`](./boards/SheetItemSplit.dc.html), [`DeleteAccount`](./boards/DeleteAccount.dc.html), và [`DarkHome`](./boards/DarkHome.dc.html) cho thanh nổi ở giao diện tối.

## Key Insights

- `DesignSystemBoundaryTest` cấm màn hình tự viết màu, bóng, nút nổi, menu thả và chuyển động. Mọi hình dáng mới vì thế phải nằm trong module này.
- `SvBottomBar` được mô tả là thanh nổi nhưng mã là một dải chữ nhật kín chiều ngang (`SvPrimaryButton.kt:46`). Tám trong chín nơi gọi đặt nó trong ô `bottomBar` của `Scaffold`; ở màn xin đồng ý nó là phần tử cuối của một cột, dưới phần cuộn. Ở cả chín nơi, nội dung kết thúc phía trên thanh, và giữ chỗ đứng đó thì bàn phím không bao giờ đẩy một ô đang nhập ra sau thanh.
- `SvPrimaryButton` vẽ màu `primary`, tức tím ở giao diện sáng, còn chữ thường vẽ màu `onSurface`. Trên thanh màu `bar` ở giao diện sáng, `primary` chỉ đạt 2,9:1 và `onSurface` trùng hẳn với nền. Nơi gọi lại đang đặt trong thanh không chỉ nút: `AnalyzeBar` của `NewSplitContent.kt` đặt `SvWorkingRow`, và `BottomPart` của `ProScreen.kt` đặt một `SvMessageCard`, một `SvLoadingIndicator` cùng một dòng chữ. Vì vậy cần một cơ chế đổi màu mà chín nơi gọi không phải biết tới.
- Các bài kiểm thử tìm phần tử bằng chữ và mô tả, không có `testTag`. Thành phần mới phải mang vai và nhãn trợ năng đúng để kiểm thử và trình đọc màn hình cùng dùng.
- Ở cỡ chữ 200%, bốn nhãn cùng nút giữa không vừa thanh điều hướng, và một bậc co chữ tính bằng sp cũng lớn gấp đôi. Ứng xử ở chữ lớn phải được định trong thành phần, không để tới lúc ghi ảnh. `TextAutoSize` có trong bản Compose đang ghim (foundation 1.12.1, và `Text` của material3 có dạng nhận nó).
- `performScrollToNode` chỉ đưa phần tử vào khung nhìn của vùng cuộn; một thanh vẽ đè lên không làm nó đổi. Bài chỉ cuộn rồi đếm, như `BillSafetyTest.kt:130-132`, không thấy được nội dung bị che.
- Màn hình có 32 chỗ gọi thẳng ô nhập, ô chọn, công tắc và `AlertDialog` của Material, cùng 11 nút `FilledTonalButton`. Chúng nằm trong các gói mà giai đoạn 5, 6 và 7 dựng lại ngay sau đó, nên giai đoạn này chỉ dựng thành phần thay thế.
- `InputField` đặt giới hạn độ dài và tùy chọn bàn phím (`ui/common/InputFields.kt:36-44`); `PaymentMethodSheet.kt:237-252` đặt `autoCorrectEnabled = false` và một giới hạn độ dài cho các ô về cách nhận tiền, như số tài khoản. Ô nhập mới không được làm mất những thứ đó.
- `SvFabMenu` và `SvFloatingToolbar` chỉ có trong trang mẫu; chúng nhận token mới, không cần thiết kế lại.

## Requirements

Thành phần hiện có, hình dáng mới. Tham số giữ nguyên để màn hình vẫn biên dịch; ngoại lệ duy nhất là `SvLargeTopBar`.

| Thành phần | Thay đổi |
|---|---|
| `SvCard` | Phẳng, bo `large`, nền `surfaceContainerLowest`; vẫn nhận màu |
| `SvListRow` | Các hàng liền nhau trong một thẻ trắng, góc nhóm 24 dp, không vạch ngăn |
| `SvSectionHeader` | Tiêu đề `titleLarge`, gợi ý `bodySmall` màu `onSurfaceVariant` |
| `SvPrimaryButton` | Viên thuốc cao 56 dp, màu `primary`; nằm trong `SvBottomBar` thì vẽ `accent` / `onAccent` |
| `SvBottomBar` | Viên thuốc màu `bar`, cách hai mép 20 dp, bóng mức 3, viền `barOutline`, chữ bên trong màu `onBar`; chỗ đứng ở nơi gọi không đổi |
| `SvSplitButton` | Nút chính cùng nút tròn mũi tên; nằm trong `SvBottomBar` thì vẽ `accent` / `onAccent` |
| `SvPrimaryFab` | Viên thuốc có biểu tượng, màu `primary` |
| `SvChoiceGroup`, `SvChoiceChips` | Viên thuốc cao 44 dp; mục đang chọn dùng `inverseSurface` / `inverseOnSurface`, không dùng `secondaryContainer`; `SvChoiceChips` có kiểu cuộn ngang một hàng |
| `SvAvatar` | Ô vuông bo góc, chữ cái màu `onPerson`; thêm `SvAvatarStack` |
| `SvPersonPickRow`, `SvPersonChoiceRow` | Ô chọn và nút chọn một kiểu mới: ô 24 dp bo 8 dp, khi chọn là nền `inverseSurface`; ở giao diện sáng là nền mực với dấu tích xanh chanh như bảng `SheetItemSplit`, ở giao diện tối là nền giấy với dấu tích mực |
| `SvBottomSheet` | Bo trên 32 dp, tay nắm, tiêu đề `headlineSmall`, hàng nút ở đáy |
| `SvMessageCard` | Thẻ trắng với ô biểu tượng màu `notice`; lỗi dùng ô màu `error` |
| `SvEmptyState` | Theo bảng `HomeEmpty`; thôi đọc `secondaryContainer` (`SvEmptyState.kt:39`) |
| `SvSuccessMark`, `SvWorkingRow`, `SvBillPhoto` | Theo bảng `AskForMoney`, `Working`, `NewSplit` |
| `SvLargeTopBar` | Tách thành thanh của màn gốc (tiêu đề `displaySmall`) và thanh của màn con (nút lùi tròn, tiêu đề `headlineMedium`, nút phụ tròn) |

Thành phần mới:

| Thành phần | Mô tả |
|---|---|
| `SvNavBar` | Thanh điều hướng nổi theo bảng `Main`: bốn mục có vai `Tab`, trạng thái đang chọn và nhãn; số đếm của một mục được gộp vào nhãn của mục đó; nút giữa là nút tròn màu `accent` chỉ có biểu tượng, mô tả do nơi gọi truyền |
| `SvHero` | Khối bo 32 dp với ba kiểu `brand`, `accent`, `bar`; có chỗ cho nhãn, số tiền và nội dung phụ |
| `SvHeroAmount` | Số tiền chính, nhận ba phần tiền tố, số và hậu tố cùng một câu để trình đọc màn hình đọc; ký hiệu tiền tệ nhỏ hơn số |
| `SvTile`, `SvTag`, `SvIconTile` | Ô thông tin 124 dp, nhãn nhỏ dạng viên thuốc, ô biểu tượng 44 dp nhận màu |
| `SvQuietButton`, `SvSmallAction`, `SvTextAction` | Nút phụ nền `surfaceContainer` hoặc trắng, là thứ thay cho `FilledTonalButton`; nút hành động nhỏ màu mực chữ xanh chanh; nút chữ gạch chân |
| `SvTextField`, `SvSwitch`, `SvCheckbox`, `SvRadio` | Ô nhập viền `outline` bo 16 dp cao 52 dp với nhãn phía trên; công tắc và ô chọn theo token |
| `SvDialog` | Hộp thoại theo bảng `DeleteAccount`: tiêu đề, lời văn, nội dung tùy chọn, nút xác nhận và nút bỏ; có biến thể cho việc không hoàn lại được, với màu `error` |
| `SvMeter`, `SvStatBar`, `SvBarChart` | Thanh đo chia đoạn, thanh tỉ lệ, biểu đồ cột; mỗi cái có mô tả bằng chữ cho trình đọc màn hình |
| `SvTicket` | Khung thẻ vé có hai khuyết tròn và đường đứt, dùng cho thẻ chia tiền |

Thanh đáy và thanh điều hướng:
- `SvBottomBar` vẫn nằm trong ô `bottomBar` của `Scaffold` ở nơi gọi (riêng ở màn xin đồng ý là cuối cột), và nội dung kết thúc phía trên nó như hôm nay, nên bàn phím không bao giờ che một ô đang nhập sau thanh.
- `SvBottomBar` cấp một `CompositionLocal` để `SvPrimaryButton` và `SvSplitButton` nằm trong nó vẽ `accent` / `onAccent`. Cùng cơ chế đó cho `SvWorkingRow` và `SvLoadingIndicator` vẽ theo màu của thanh, như bảng `Working`. Chín nơi gọi, không nơi nào phải đổi chữ ký: `ui/editor/BillEditorContent.kt:83`, `ui/split/NewSplitContent.kt:272`, `ui/split/ResultContent.kt:724`, `ui/split/AiConsent.kt:82`, `ui/split/RequestMoneyContent.kt:113`, `ui/paywall/ProScreen.kt:342`, `ui/claim/ClaimScreen.kt:406`, `ui/insights/InsightsScreen.kt:123` (giai đoạn 7 thay bằng một nút nằm trong nội dung) và `app/src/debug/java/com/dantech/splitvibe/catalog/CatalogScreen.kt:96`.
- `SvNavBar` ở chữ lớn: khi có một nhãn không vừa một dòng, cả thanh chỉ hiện biểu tượng, và các nhãn ở lại làm tên trợ năng. Thanh nằm trong ô `bottomBar` của `Scaffold` ở màn gốc, như `SvBottomBar`, và danh sách của màn kết thúc phía trên nó: không gì cuộn xuống dưới hai thanh.
<!-- Updated: Review giai đoạn 2 - nội dung cuộn dưới thanh nổi làm kiểm tra trợ năng trên máy hỏng, nên cả hai thanh nằm trong ô bottomBar -->

Số tiền chính:
- Ký hiệu tiền tệ đứng trước hay sau số tùy vùng của máy (giai đoạn 4), nên `SvHeroAmount` nhận cả tiền tố và hậu tố. Chữ tự co từ 56 xuống 20 sp trên một dòng; nếu vẫn không vừa thì xuống dòng thứ hai; không bao giờ bị cắt.

Ô nhập, nút phụ và hộp thoại:
- `SvTextField` nhận `keyboardOptions`, `keyboardActions`, `maxLength`, `isError`, dòng chữ phụ và chữ gợi ý, và không có mặc định nào yếu hơn `ui/common/InputFields.kt:36-44` hôm nay. Nó cũng nhận những phần mà nơi gọi đang dùng: hậu tố (`InputField`), tiền tố và nhiều dòng (`PaymentMethodSheet`), biểu tượng đầu ô (ô tìm của `HistoryScreen`), một chỗ đặt nút ở cuối ô và trạng thái tắt (ô nhập câu lệnh và ô sửa bằng câu của giai đoạn 5).
- Ô nhập một dòng cắt chữ ở mép khung của dòng. Với Be Vietnam Pro cỡ 16/24, dấu trên của chữ hoa xếp chồng như Ể, Ẩ, Ẳ nhô lên quá khung tới khoảng 1,3 sp, nên tên quán như "QUÁN ỐC BA MIỂN" mất đỉnh dấu. Chữ trong `SvTextField` vì thế có khoảng dòng ít nhất 26 sp ở cỡ 16, và trang mẫu có một ô chứa "Ẳ QUÁN ỐC BA MIỂN" để thấy điều đó ở cả cỡ chữ 200%.
<!-- Updated: Thực hiện giai đoạn 1 - review thấy dấu chữ hoa bị cắt trong ô nhập một dòng -->
- Giai đoạn này chỉ chuyển `ui/common/InputFields.kt` sang `SvTextField`, cùng một ngoại lệ: nút `OutlinedButton` nằm trong thanh ở `ui/split/AiConsent.kt:82` chuyển sang `SvQuietButton`, vì màu mới của thanh làm chữ của nó chỉ còn 2,9:1. Mọi chỗ gọi thẳng Material khác trong màn hình do giai đoạn sở hữu gói chuyển: 12 chỗ gọi `AlertDialog` sang `SvDialog`, các nút `FilledTonalButton` sang `SvQuietButton`, cùng các ô nhập, ô chọn và công tắc còn lại, ở giai đoạn 5, 6 và 7.
- `ui/common/CategoryStyle.kt` (biểu tượng và màu `SvPalette.category` cho từng `BillCategory`) vẫn được tạo ở giai đoạn này.

Kiểm thử:
- `ComponentBehaviourTest` khẳng định rằng tùy chọn bàn phím đi qua `SvTextField` nguyên vẹn và chữ bị cắt ở `maxLength`.
- Phép kiểm thanh che nội dung, cho cả hai thanh: mép dưới của chính vùng cuộn nằm phía trên mép trên của thanh, tính theo tọa độ gốc, và phần tử cuối vẫn cuộn tới được phía trên thanh. Chỉ xét phần tử cuối thì không thấy được một danh sách cuộn xuống dưới thanh mà chỉ có phần đệm ở cuối.
<!-- Updated: Review giai đoạn 2 - cả hai thanh nằm trong ô bottomBar, thanh điều hướng không còn báo chiều cao -->
- `ComponentCatalogTest` thêm ảnh thứ ba, chụp trang mẫu ở cỡ chữ 200%, tên `catalog-large-text`. Ảnh được đọc từ kết quả của chế độ so ngay trong giai đoạn này.

Ràng buộc chung: vùng chạm tối thiểu 48 dp; mọi nút chỉ có biểu tượng có nhãn; không thành phần nào tự chạy khi `LocalReducedMotion` bật; thời lượng lấy từ `SvMotion` và `motionScheme`.

## Files to Create / Modify

- Modify trong `core/designsystem/src/main/java/com/dantech/splitvibe/designsystem/components/`: `SvCard.kt`, `SvListRow.kt`, `SvSectionHeader.kt`, `SvPrimaryButton.kt` (chứa cả `SvBottomBar`), `SvSplitButton.kt`, `SvPrimaryFab.kt`, `SvChoiceGroup.kt` (chứa cả `SvChoiceChips`), `SvAvatar.kt`, `SvBottomSheet.kt` (chứa cả `SvPersonPickRow` và `SvPersonChoiceRow`), `SvMessageCard.kt`, `SvEmptyState.kt`, `SvSuccessMark.kt`, `SvWorkingRow.kt`, `SvBillPhoto.kt`, `SvTopBar.kt`.
- Create trong cùng thư mục: `SvNavBar.kt`, `SvHero.kt`, `SvTile.kt`, `SvButtons.kt`, `SvFields.kt`, `SvDialog.kt`, `SvMeter.kt`, `SvBarChart.kt`, `SvTicket.kt`.
- Modify: `app/src/main/java/com/dantech/splitvibe/ui/common/InputFields.kt` để `InputField` và `NumberField` dùng `SvTextField`.
- Modify: `app/src/main/java/com/dantech/splitvibe/ui/home/HomeScreen.kt`, chỉ ở chỗ gọi `SvLargeTopBar`.
- Modify: `app/src/main/java/com/dantech/splitvibe/ui/split/AiConsent.kt`, chỉ ở nút `OutlinedButton` trong thanh (`:82`), chuyển sang `SvQuietButton`.
- Create: `app/src/main/java/com/dantech/splitvibe/ui/common/CategoryStyle.kt`, để các giai đoạn 5 và 7 cùng dùng.
- Modify: `app/src/debug/java/com/dantech/splitvibe/catalog/CatalogScreen.kt` (thêm mọi thành phần mới; chỗ gọi `SvLargeTopBar`).
- Modify: `app/src/test/java/com/dantech/splitvibe/ui/ComponentBehaviourTest.kt`, `ComponentCatalogTest.kt`.

## Implementation Steps

1. Đổi hình dáng các thành phần hiện có theo bảng thứ nhất, giữ nguyên tham số. Tách `SvLargeTopBar` và sửa hai nơi gọi nó: `ui/home/HomeScreen.kt` và `catalog/CatalogScreen.kt`.
2. `SvBottomBar`: dáng viên thuốc mới, chữ màu `onBar`, và `CompositionLocal` đổi màu cho nút, `SvWorkingRow` và `SvLoadingIndicator`. Soát chín nơi gọi: không nơi nào đổi lời gọi, nút chính trong thanh vẽ `accent`, chữ trong thanh đọc được.
3. Dựng các thành phần mới theo bảng thứ hai, kể cả `SvDialog`. Mỗi cái kèm chú thích khi nào dùng và một mục trong trang mẫu.
4. `SvNavBar`: mỗi mục có vai `Tab`, nhãn và trạng thái chọn đọc được; số đếm gộp vào nhãn; nút giữa nhận mô tả từ nơi gọi; quy tắc chữ lớn; nằm trong ô `bottomBar` của `Scaffold`, nên không cần báo chiều cao.
<!-- Updated: Review giai đoạn 2 - cả hai thanh nằm trong ô bottomBar, thanh điều hướng không còn báo chiều cao -->
5. `SvHeroAmount`: ba phần và câu đọc; tự co từ 56 xuống 20 sp, rồi xuống dòng thứ hai; không cắt chữ.
6. `SvTextField` với đủ tham số ở mục Requirements; chuyển `ui/common/InputFields.kt` sang nó. Chuyển nút `OutlinedButton` trong thanh ở `ui/split/AiConsent.kt:82` sang `SvQuietButton`. Không đụng chỗ gọi Material nào khác trong màn hình.
7. Tạo `ui/common/CategoryStyle.kt`.
8. Viết kiểm thử hành vi trong `ComponentBehaviourTest`: vai và trạng thái của `SvNavBar`, kể cả khi thanh chỉ còn biểu tượng; câu đọc của `SvHeroAmount`; `SvMeter` và `SvBarChart` có mô tả chữ; ô chọn và công tắc đổi trạng thái; nút chia đôi vẫn mở menu; hai nút của `SvDialog`; tùy chọn bàn phím và `maxLength` của `SvTextField`; phép kiểm thanh che nội dung cho `SvBottomBar` và `SvNavBar`.
9. Thêm ảnh `catalog-large-text` vào `ComponentCatalogTest`. Chạy cổng kiểm thử của plan với `-Pscreenshots=compare`, đọc `catalog-light_actual.png`, `catalog-dark_actual.png` và `catalog-large-text_actual.png` trong `app/build/outputs/roborazzi/`, sửa chỗ chữ bị cắt, rồi gửi ba ảnh cho chủ sản phẩm.

## Todo

- [x] Thành phần hiện có đổi hình dáng; `SvLargeTopBar` tách đôi và hai nơi gọi được sửa
- [x] `SvBottomBar` mới, nút bên trong vẽ `accent`, chữ bên trong đọc được, chín nơi gọi không đổi
- [x] Thành phần mới, kể cả `SvDialog`, và mục trong trang mẫu
- [x] `SvNavBar` với vai, số đếm và quy tắc chữ lớn, nằm trong ô `bottomBar`
- [x] `SvHeroAmount` ba phần, tự co và xuống dòng
- [x] `SvTextField`; `InputFields.kt` chuyển sang nó; nút trong thanh ở `AiConsent.kt:82` thành `SvQuietButton`
- [x] `CategoryStyle.kt`
- [x] Kiểm thử hành vi, bàn phím, `maxLength` và thanh che nội dung
- [x] Ảnh `catalog-large-text`; ba ảnh trang mẫu gửi chủ sản phẩm

## Success Criteria

- Cổng kiểm thử của plan qua (với `-Pscreenshots=compare`). Các bài riêng của giai đoạn: `ComponentBehaviourTest`, `ComponentCatalogTest` và `DesignSystemBoundaryTest`; luồng chia hóa đơn mẫu vẫn chạy trọn trong `SplitFlowTest` và `ReducedMotionTest`.
- Trang mẫu có mọi thành phần của hai bảng trên, ở giao diện sáng và tối, và ảnh `catalog-large-text_actual.png` không có chữ nào bị cắt.
- Không nơi nào trong chín nơi gọi `SvBottomBar` phải sửa lời gọi. Trên ảnh `_actual.png`, nút chính trong thanh vẽ `accent` ở màn kết quả và màn sửa hóa đơn, và dòng trạng thái trong thanh đọc được ở ảnh `04-working`.
- Lượt `device-tests.yml` trên commit cuối của giai đoạn được đọc và ghi lại.
- `git status --porcelain app/src/test/screenshots` rỗng.

## Risk Assessment

- Thanh che nội dung: `SvBottomBar` và `SvNavBar` cùng nằm trong ô `bottomBar`, và danh sách nhận phần đệm của `Scaffold` làm `Modifier.padding`, nên nội dung kết thúc phía trên thanh. Phép kiểm thanh che nội dung so mép dưới của chính vùng cuộn với mép trên của thanh, nên bắt được một danh sách cuộn xuống dưới thanh.
<!-- Updated: Review giai đoạn 2 - cả hai thanh nằm trong ô bottomBar, thanh điều hướng không còn báo chiều cao -->
- Nhãn của `SvNavBar` hoặc số tiền chính vẫn bị cắt ở cỡ chữ 200%: dấu hiệu nằm trên ảnh `catalog-large-text_actual.png`. Ứng phó: sửa trong thành phần ngay ở giai đoạn này, không để tới giai đoạn 8.
- Trong thanh ở `ui/split/AiConsent.kt:82` có một `OutlinedButton` gọi thẳng, thứ mà `CompositionLocal` của thanh không đổi màu được: ở giao diện sáng, chữ `primary` của nó chỉ đạt 2,9:1 trên `bar`, và `SplitFlowOnDeviceTest` chạy kiểm tra trợ năng qua màn này. Vì giai đoạn này là nơi đổi màu thanh, nó chuyển luôn nút đó sang `SvQuietButton`, để không giai đoạn nào kết thúc với một nút khó đọc. Giai đoạn 5 sau đó đưa hai câu trả lời ra khỏi thanh theo bảng `Consent`.
- `SvTextField` làm yếu bàn phím hoặc mất giới hạn độ dài, khiến số tài khoản đi vào từ điển của bàn phím hoặc giá trị quá dài tới máy chủ: bài kiểm thử về tùy chọn bàn phím và `maxLength` canh việc này.
- Các chỗ gọi thẳng Material còn lại trông lệch với thành phần mới cho tới khi giai đoạn 5, 6 và 7 chuyển chúng: chấp nhận trong lúc chuyển tiếp. Nhờ `secondaryContainer` là sắc trung tính nhạt, các nút `FilledTonalButton` vẫn là nút phụ.

## Rollback

Hoàn lại các commit của giai đoạn chỉ dùng được khi giai đoạn 3 chưa bắt đầu. Sau đó, lỗi được sửa bằng commit mới, vì giai đoạn 3 dùng `SvNavBar`, còn các giai đoạn 5 đến 7 dùng `SvDialog`, `SvTextField`, `SvQuietButton` và `CategoryStyle.kt`.

## Kết quả thực hiện

Làm ngày 20/11/2026, trong bốn commit: `ed723d4` (thành phần hiện có mang hình dáng mới, thanh đáy nổi), `4a8bb44` (thành phần mới), `dbc1584` (danh sách kết thúc phía trên thanh đáy) và `130df45` (sửa theo review).

- Cổng kiểm thử của plan qua với `-Pscreenshots=compare` trên `130df45`: 518 bài kiểm thử đơn vị của `:app`, 12 bài của `:core:designsystem`, 200 bài của `:core:domain`, ktlint, lint, các gói và kiểm tra gói phát hành. `git status --porcelain app/src/test/screenshots` rỗng.
- Lượt `device-tests.yml` 44746212371 trên `130df45`: kiểm thử trên máy ảo Android 14, kèm kiểm tra trợ năng, và việc ghi hồ sơ khởi động đều qua.
- Lượt trên `4a8bb44` (44728891917) từng hỏng ở `SplitFlowOnDeviceTest`: kiểm tra trợ năng báo một nút chỉ còn cao 26 dp. Nguyên nhân là bốn màn (trình sửa, kết quả, đòi tiền, thống kê) cho danh sách cuộn xuống dưới thanh nổi bằng `contentPadding`, nên nút bị thanh che một phần chỉ còn lộ một dải mỏng. `dbc1584` cho danh sách nhận phần đệm làm `Modifier.padding`, và lượt 44731402258 trên commit đó qua. Từ đó cả hai thanh nằm trong ô `bottomBar` và không gì cuộn xuống dưới chúng; quy tắc này đã ghi vào giai đoạn 3 và 5, và `SvNavBar` không còn báo chiều cao đo được.
- Đã sửa sau review độc lập ([báo cáo](./reports/review-phase-02.md)); mỗi phép kiểm mới hay được sửa đều được cho chạy trên mã sai một lần, thấy hỏng, rồi mới giữ:
  - Phép kiểm thanh che nội dung so mép dưới của chính vùng cuộn với mép trên của thanh, cho cả hai thanh và cho màn đòi tiền thật.
  - Phép kiểm vùng chạm đo kích thước bố cục, thay cho vùng chạm vốn luôn đủ 48 dp. Nó bắt được nút lùi của `SvTopBar` chỉ giữ 44 dp; nút giờ giữ 48 dp quanh vòng tròn 44 dp, nên vòng tròn lùi vào thêm 2 dp so với bảng.
  - Chú thích của `SvTextField` nói đúng rằng tắt tự sửa không ngăn bàn phím gợi ý hay học chữ, còn `KeyboardType.Password` thì có, và ô vẫn hiện chữ. Trình đọc màn hình vẫn đọc số tài khoản, vì Compose chỉ coi ô là mật khẩu khi chữ bị che.
  - `SvMessageCard` nằm trên thẻ, bảng hay hộp thoại thì dùng màu trầm của nền đó; độ tương phản của chữ là 16,37:1 ở giao diện sáng và 12,93:1 ở giao diện tối.
  - Ở chế độ chỉ có biểu tượng, mục đang chọn của thanh điều hướng có thêm một vạch xanh chanh 16×4 dp dưới biểu tượng.
  - Hàng ba ô số của trình sửa thẳng nhau theo đáy, và nút xóa nằm giữa chiều cao của khung ô.
  - Ô "Date" và "Total" của trang mẫu rộng hết hàng, và `ComponentCatalogTest` khẳng định trang mẫu không dài quá khung ảnh.
  - Chồng ảnh đại diện mỗi ô một chữ cái, viền 2 dp.
  - Chip một hàng tự cuộn tới mục đang chọn: nhảy khi hiện lần đầu, lướt khi đổi lựa chọn, và nhảy khi giảm chuyển động.
  - Hai bài kiểm lại giới hạn 3 ký tự của ô tiền tệ và 20 ký tự của ô số; chú thích của `SV_TEXT_FIELD_MAX_LENGTH` nhắc ô nhiều dòng phải tự truyền giới hạn.
- Sửa thêm khi soát lượt sửa, mỗi điều có bài kiểm và đã cho chạy hỏng có chủ ý:
  - Viền của chồng ảnh đại diện lấy màu của nền nó nằm trên: trắng trên thẻ ở giao diện sáng, `#1C1C21` trên thẻ ở giao diện tối như bảng `DarkResult`, và trắng trên khối tím hay xanh chanh như bảng `Main` và `DarkHome`.
  - Nhãn của `SvTile` co nhỏ dần, tới 9 sp, thay vì ngắt giữa một từ như "Novemb / er" ở cỡ chữ 200%.
  - Chú thích của `SvTextField` nói ô nhập chỉ đặt trên trang, thẻ, bảng hay hộp thoại, vì nhãn nằm ngoài khung ô không đổi màu theo thanh tối hay khối màu; không bảng thiết kế nào đặt ô nhập ở những chỗ đó.
- Các giá trị mà giai đoạn để ngỏ, do người thực hiện chọn: vòng xoay chờ trong thanh màu xanh chanh; lề hai bên thanh nổi trong suốt; `SvChoiceChips` có thêm kiểu một hàng cuộn ngang; ảnh trang mẫu chụp ở mật độ mdpi trong cửa sổ cao 9.000 dp (sáng, tối) và 15.000 dp (chữ lớn), vì 512 MB bộ nhớ của tiến trình kiểm thử không đủ cho ảnh lớn hơn.
- Còn mở, cho các giai đoạn sau:
  - Các điểm Low của review chưa làm: một thẻ có màu riêng vẫn cấp nền thẻ thường, nên viền ảnh đại diện và nút trầm trên đó lấy màu của thẻ trắng; ở giao diện tối thẻ mức 3 trùng màu nút trầm; `BoxWithConstraints` của thanh điều hướng và cỡ chữ cố định của số đếm; vai của nút mũi tên trong nút chia đôi; mô tả lỗi "Invalid input" viết cứng bằng tiếng Anh; `SvDialog` chưa có trong ảnh nào.
  - Hàng phụ phí của trình sửa chưa căn ô theo đáy; đã ghi vào giai đoạn 5.
  - `docs/design-system.md:147` còn nói thanh trên co lại khi cuộn; giai đoạn 10 sửa tài liệu.
  - Ở ảnh `01-home-large-text`, nút nổi "Split a bill" che cuối dòng chữ của trạng thái trống; giai đoạn 3 bỏ nút này.
