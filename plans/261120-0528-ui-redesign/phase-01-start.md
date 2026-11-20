---
title: "Phase 1: Nền tảng: màu, chữ, hình khối"
status: pending
phase: 1
priority: P1
effort: "3d"
dependencies: []
---

# Phase 1: Nền tảng: màu, chữ, hình khối

## Overview

Module `:core:designsystem` mang bảng màu sáng và tối mới, hai phông chữ đóng gói, bộ hình khối và mức nổi mới. Sau giai đoạn này mọi màn hình tự đổi màu và chữ dù bố cục chưa đổi.

## Context

- Token hiện tại và các bài kiểm thử canh giữ chúng: [rà hệ thiết kế](./reports/scout-design-system-navigation.md), mục 2, 4, 5 và 7.
- Phông chữ: [tra cứu phông](./research/researcher-02-fonts.md).
- Bảng tham chiếu trên canvas: `Main`, `Result`, `Group` (sáng) và `DarkHome`, `DarkResult`, `DarkGroup` (tối).

## Key Insights

- `MaterialExpressiveTheme` của material3 1.5.0-rc01 nhận tham số `shapes` (đã kiểm bằng `javap` trên gói trong bộ đệm Gradle), nên bộ hình khối đi qua theme được.
- Ở giao diện tối, nút chính và liên kết là xanh chanh, còn khối tím vẫn tím. Vì vậy màu thương hiệu phải tách khỏi `primary`: `primary` đổi theo giao diện, màu thương hiệu thì không.
- Be Vietnam Pro không có `tnum`. Mọi kiểu chữ số tiền, kể cả `amountSmall` đang dựa trên `bodyLarge`, phải chuyển sang Bricolage Grotesque.
- `ColorTokensTest` đòi chữ 4,5:1 và viền 3:1 trên cả sáu bề mặt. Các giá trị dưới đây là điểm xuất phát; bài kiểm thử quyết định giá trị cuối.
- `WebTokensTest` đọc `Shapes()` mặc định và liệt kê màu bằng tay, nên token mới không tự sang web nếu không sửa bài kiểm thử.
- Màu của người (`SvAvatar.kt:57-68`) và bóng đổ (`SvElevation.kt:66`) đang nằm ngoài token.

## Requirements

Vai màu của Material, giá trị xuất phát:

| Vai | Sáng | Tối |
|---|---|---|
| `primary` / `onPrimary` | `#5B34F5` / `#FFFFFF` | `#C9F73A` / `#111114` |
| `primaryContainer` / `onPrimaryContainer` | `#E9E3FF` / `#1E0A6B` | `#2A1F7A` / `#E4DCFF` |
| `secondary`, `secondaryContainer` / chữ trên chúng | `#111114` / `#FFFFFF` | `#F4F4F1` / `#111114` |
| `tertiary`, `tertiaryContainer` / chữ trên chúng | `#C9F73A` / `#111114` | `#C9F73A` / `#111114` |
| `error` / `onError` | `#B42A14` / `#FFFFFF` | `#FF8A70` / `#111114` |
| `surface`, `background` / `onSurface` | `#F6F6F4` / `#111114` | `#0F0F12` / `#F4F4F1` |
| `surfaceContainerLowest`, `Low`, `Container`, `High`, `Highest` | `#FFFFFF`, `#FAFAF8`, `#F0EFEA`, `#E9E8E2`, `#E1E0D9` | `#0A0A0C`, `#1C1C21`, `#232329`, `#2A2A31`, `#33333B` |
| `onSurfaceVariant` | `#5F5E66` | `#B7B6BF` |
| `outline` / `outlineVariant` | `#76757E` / `#D9D8D2` | `#8E8D97` / `#3A3A42` |
| `inverseSurface` / `inverseOnSurface` / `inversePrimary` | `#111114` / `#F4F4F1` / `#C9F73A` | `#F4F4F1` / `#111114` / `#5B34F5` |

Bảng màu riêng `SvPalette`, cấp qua `LocalSvPalette` và đọc bằng `SvTheme.palette`:

| Token | Sáng | Tối | Dùng cho |
|---|---|---|---|
| `brand` / `onBrand` | `#5B34F5` / `#FFFFFF` | như sáng | khối chính, thẻ vé |
| `accent` / `onAccent` | `#C9F73A` / `#111114` | như sáng | khối xanh chanh, nhãn, dấu tích |
| `bar` / `onBar` / `onBarQuiet` | `#111114` / `#FFFFFF` / `#C6C5CE` | `#26262C` / `#F4F4F1` / `#B7B6BF` | thanh nổi |
| `moneyIn` / `moneyOut` | `#0A6B4E` / `#B33A0A` | `#5BE3B0` / `#FF9B5E` | chữ "gets back" và "owes" |
| `person[0..6]` / `onPerson` | `#FF6FB5`, `#FF8A3D`, `#5CC8FF`, `#FFD23F`, `#5BE3A0`, `#B9A2FF`, `#7CE0D6` / `#111114` | như sáng | ô chữ cái của người |
| `category[0..8]` | `#FF8A3D`, `#5BE3A0`, `#5CC8FF`, `#B9A2FF`, `#FF6FB5`, `#FFD23F`, `#7CE0D6`, `#A8B4FF`, `#D9D8D2` | như sáng | ô danh mục, theo thứ tự của `BillCategory` |
| `notice` | `#FFD23F` | như sáng | ô biểu tượng của lời nhắc |
| `photoZone` | `#E7E6E0` | `#2A2A31` | nền sau ảnh hóa đơn |

Chữ:
- Họ `display`: Bricolage Grotesque Bold 700 và ExtraBold 800 (bản 14pt), thêm ExtraBold bản 96pt cho cỡ từ 40sp. Họ `text`: Be Vietnam Pro 400, 500, 600, 700.
- `display*`, `headline*` và `titleLarge` dùng `display` 800; `titleMedium`, `titleSmall`, `body*`, `label*` dùng `text`.
- Cỡ chính (sp, cỡ/dòng): `displayLarge` 56/60, `displaySmall` 32/36, `headlineMedium` 26/32, `headlineSmall` 24/30, `titleLarge` 20/26, `titleMedium` 16/22, `bodyLarge` 16/24, `bodyMedium` 15/22, `bodySmall` 13/18, `labelLarge` 14/20, `labelMedium` 12/18, `labelSmall` 11/16.
- Số tiền, đều có `tnum`: `amountHero` 56/60 (bản 96pt), `amountLarge` 24/28, `amountMedium` 19/24, `amountSmall` 15/20; đều thuộc họ `display`.

Hình khối và mức nổi:
- `Shapes`: `extraSmall` 8, `small` 12, `medium` 16, `large` 24, `extraLarge` 32 dp. `SvShapes` thêm `pill`, `tile` 14 dp, và góc nhóm ngoài 24 dp, trong 0 dp.
- `SvElevation` giữ bốn mức và API hiện tại. Mức 1 và 2 phẳng ở giao diện sáng; mức 3 là bóng của thanh nổi (mờ 28 dp, lệch 12 dp, 28% màu mực); mức 4 cho vật đang kéo. Màu bóng là mực, không còn lấy từ `onPrimaryFixed`. Ở giao diện tối, mức vẫn là bề mặt sáng dần.
- `SvSpacing` thêm `screen` 20 dp cho lề màn hình.

Giấy phép phông: hai tệp `OFL.txt` được đưa vào app dưới dạng tài nguyên văn bản. Màn hiển thị chúng thuộc giai đoạn 6.

## Files to Create / Modify

- Create: `core/designsystem/src/main/res/font/` với `bricolage_grotesque_bold.ttf`, `bricolage_grotesque_extrabold.ttf`, `bricolage_grotesque_display_extrabold.ttf`, `be_vietnam_pro_regular.ttf`, `be_vietnam_pro_medium.ttf`, `be_vietnam_pro_semibold.ttf`, `be_vietnam_pro_bold.ttf`; nguồn và commit ghim theo mục 7 và 8 của bản tra cứu phông.
- Create: `core/designsystem/src/main/res/raw/bricolage_grotesque_ofl.txt`, `be_vietnam_pro_ofl.txt`.
- Create: `core/designsystem/src/main/java/com/dantech/splitvibe/designsystem/tokens/SvPalette.kt` (bảng màu riêng, `LocalSvPalette`, `SvTheme`).
- Create: `core/designsystem/src/test/java/com/dantech/splitvibe/designsystem/tokens/TypographyTest.kt`.
- Modify: `tokens/SvColors.kt`, `tokens/SvTypography.kt`, `tokens/SvShapes.kt`, `tokens/SvElevation.kt`, `tokens/SvSpacing.kt`, `theme/SplitVibeTheme.kt` (truyền `shapes`, cấp `LocalSvPalette`).
- Modify: `components/SvAvatar.kt` (màu người lấy từ `SvPalette`, bỏ `Color.White` viết cứng).
- Modify: `core/designsystem/src/test/.../tokens/ColorTokensTest.kt`, `WebTokensTest.kt`; ghi lại `web/src/tokens.css`.

## Implementation Steps

1. Việc đầu tiên, để gỡ rủi ro lớn nhất: thêm hai tệp phông và một bài kiểm thử Robolectric vẽ chữ "QUÁN ỐC BA MIỀN ₫1.111.111" bằng từng họ rồi so bề rộng với phông mặc định. Nếu Robolectric không nạp phông từ `res/font`, dừng lại và báo trước khi làm tiếp.
2. Thêm đủ bảy tệp phông và hai tệp giấy phép. Khai báo hai `FontFamily`, dựng `SvTypography` bằng cách chép từng kiểu của `Typography()` với họ, độ đậm và cỡ ở trên; mọi kiểu Bricolage có độ đậm ghi rõ.
3. Viết lại `LightColors` và `DarkColors` theo bảng vai màu. Thêm `SvPalette` cho sáng và tối, cấp trong `SplitVibeTheme`.
4. Truyền `Shapes` mới vào `MaterialExpressiveTheme`; cập nhật `SvShapes`, `SvElevation` và `SvSpacing`.
5. Mở rộng `ColorTokensTest`: chữ trên `brand`, `accent`, `bar`; `moneyIn` và `moneyOut` trên sáu bề mặt; `onPerson` trên từng màu người; mực trên từng màu danh mục và `notice`. Chỉnh giá trị màu cho tới khi qua.
6. Sửa `WebTokensTest`: đọc hình khối từ theme, thêm các token của `SvPalette` vào danh sách màu cho cả sáng và tối, lấy màu bóng từ mực. Biến phông của web giữ là bộ phông hệ thống. Ghi lại `web/src/tokens.css` bằng `-PwebTokens=write`.
7. Viết `TypographyTest`: mọi kiểu số tiền cho "1111" và "8888" cùng bề rộng; mọi kiểu `display`, `headline`, `titleLarge` thuộc họ `display`.
8. Chạy kiểm thử đơn vị của `:core:designsystem` và `:app` với `-Pscreenshots=record`, ghép ảnh mới thành bảng xem nhanh trong thư mục tạm, rồi trả cây làm việc về trạng thái đã commit cho thư mục ảnh tham chiếu.

## Todo

- [ ] Bài kiểm thử nạp phông dưới Robolectric qua
- [ ] Bảy tệp phông và hai tệp giấy phép có trong module
- [ ] Bảng màu sáng, tối và `SvPalette` theo bảng
- [ ] Hình khối, mức nổi, lề màn hình mới
- [ ] `ColorTokensTest` mở rộng và qua
- [ ] `WebTokensTest` sửa, `tokens.css` ghi lại
- [ ] `TypographyTest` qua
- [ ] Bảng ảnh xem nhanh gửi chủ sản phẩm

## Success Criteria

- `./gradlew :core:designsystem:testDebugUnitTest` qua, gồm `ColorTokensTest`, `WebTokensTest` và `TypographyTest`.
- `./gradlew :app:testProdDebugUnitTest` qua khi bỏ các bài so ảnh; `DesignSystemBoundaryTest` qua.
- `./gradlew ktlintCheck :app:lintProdDebug :core:designsystem:lintDebug` qua.
- Ảnh trang chủ và màn kết quả dựng ra cho thấy hai phông mới, dấu tiếng Việt đúng và ký hiệu ₫.

## Risk Assessment

- Robolectric không nạp phông từ `res/font`: dấu hiệu là bài kiểm thử ở bước 1 cho bề rộng bằng phông mặc định. Ứng phó: dừng và báo; phương án là nạp phông qua `Typeface.createFromAsset` cho riêng kiểm thử.
- Một cặp màu không đạt tương phản: `ColorTokensTest` chỉ ra cặp nào. Ứng phó: chỉnh sắc độ, giữ sắc màu; nếu phải đổi một màu thấy rõ trên canvas, báo chủ sản phẩm.
- Bề mặt của Material đổi nghĩa (`secondaryContainer` thành mực) làm một thành phần Material chưa bọc trông sai: giai đoạn 2 rà từng thành phần.
- Gói tăng khoảng 0,4 MB vì phông: ghi số đo thật vào báo cáo của giai đoạn.

## Rollback

Mọi thay đổi nằm trong `:core:designsystem` và `web/src/tokens.css`. Hoàn lại các commit của giai đoạn là đủ; không có dữ liệu hay giao kèo nào đổi.
