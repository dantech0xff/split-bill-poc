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

Module `:core:designsystem` mang bảng màu sáng và tối mới, ba họ phông dựng từ bảy tệp phông đóng gói, bộ hình khối và mức nổi mới. Sau giai đoạn này mọi màn hình tự đổi màu và chữ dù bố cục chưa đổi. Trang web nhận màu mới nhưng giữ chữ và hình khối đang có cho tới giai đoạn 9.

## Context

- Token hiện tại và các bài kiểm thử canh giữ chúng: [rà hệ thiết kế](./reports/scout-design-system-navigation.md), mục 2, 4, 5 và 7.
- Phông chữ: [tra cứu phông](./research/researcher-02-fonts.md).
- Bằng chứng kèm tệp và dòng cho các yêu cầu dưới đây: phản biện về [giả định](./reports/red-team-assumptions.md), [kiểu hỏng](./reports/red-team-failure-modes.md), [phạm vi](./reports/red-team-scope.md) và [bảo mật](./reports/red-team-security.md).
- Bảng tham chiếu: [`Main`](./boards/Main.dc.html), [`Result`](./boards/Result.dc.html), [`Group`](./boards/Group.dc.html) (sáng) và [`DarkHome`](./boards/DarkHome.dc.html), [`DarkResult`](./boards/DarkResult.dc.html), [`DarkGroup`](./boards/DarkGroup.dc.html) (tối). Đủ 37 bảng có trong [danh sách bảng](./boards/README.md).

## Key Insights

- `MaterialExpressiveTheme` của material3 1.5.0-rc01 nhận tham số `shapes` (đã kiểm bằng `javap` trên gói trong bộ đệm Gradle), nên bộ hình khối đi qua theme được.
- Ở giao diện tối, nút chính và liên kết là xanh chanh, còn khối tím vẫn tím. Vì vậy màu thương hiệu phải tách khỏi `primary`: `primary` đổi theo giao diện, màu thương hiệu thì không.
- 11 chỗ màn hình gọi thẳng `FilledTonalButton` và `SvEmptyState.kt:39` đang đọc `secondaryContainer`. Nếu vai này thành màu mực, các nút phụ và huy hiệu đó thành khối mực đặc, nặng ngang nút chính. Vì vậy nó là một sắc trung tính nhạt.
- Compose chọn tệp phông trong một họ theo độ đậm và kiểu nghiêng, không theo cỡ. Hai tệp ExtraBold (bản 14pt và bản 96pt) vì thế không nằm chung một `FontFamily` được.
- Màn hình đang dùng các kiểu Emphasized của Material 3 Expressive (`RequestMoneyContent.kt:125`, `ProScreen.kt:140` và `:161`, `HomeScreen.kt:163`), và các kiểu số tiền hôm nay dựng từ chúng và từ `bodyLarge` (`SvTypography.kt:13`, `:16`, `:19`). Kiểu nào không được gán họ phông sẽ vẫn vẽ bằng phông mặc định.
- Be Vietnam Pro không có `tnum`, nên mọi kiểu chữ số tiền phải thuộc Bricolage Grotesque.
- Kiểm thử của `:core:designsystem` chỉ có JUnit (`core/designsystem/build.gradle.kts:23-28`, `:39`). Robolectric có tài nguyên Android đã được dựng sẵn ở `:app` (`app/build.gradle.kts:176`), nên bài thử nạp phông chạy ở `:app`.
- Tệp trong `res/font` không phải phụ thuộc của Gradle, nên `gradle/verification-metadata.xml` không canh chúng. Chúng cần nguồn ghim theo commit, mã băm ghi trong repo và một bài kiểm thử so mã băm.
- `WebTokensTest` xuất cho web cả cỡ, khoảng dòng và độ đậm của từng kiểu chữ, và `web/src/app.css` dùng các biến đó. Nếu phần chữ đi theo `SvTypography` mới, tiêu đề của web thành chữ hệ thống đậm 800 với khoảng dòng của Bricolage.
- Màu của người (`SvAvatar.kt:57-68`) và màu bóng (`SvElevation.kt:66`) đang nằm ngoài token.

## Requirements

Vai màu của Material. Mọi giá trị màu trong giai đoạn này là điểm xuất phát; `ColorTokensTest`, vốn đòi chữ 4,5:1 và viền 3:1 trên cả sáu bề mặt, quyết định giá trị cuối.

| Vai | Sáng | Tối |
|---|---|---|
| `primary` / `onPrimary` | `#5B34F5` / `#FFFFFF` | `#C9F73A` / `#111114` |
| `primaryContainer` / `onPrimaryContainer` | `#E9E3FF` / `#1E0A6B` | `#2A1F7A` / `#E4DCFF` |
| `secondary` / `onSecondary` | `#111114` / `#FFFFFF` | `#F4F4F1` / `#111114` |
| `secondaryContainer` / `onSecondaryContainer` | `#E9E8E2` / `#111114` | `#33333B` / `#F4F4F1` |
| `tertiary` / `onTertiary` | `#C9F73A` / `#111114` | `#C9F73A` / `#111114` |
| `tertiaryContainer` / `onTertiaryContainer` | `#EEFBC2` / `#111114` | `#2F3A0F` / `#E9F7B0` |
| `error` / `onError` | `#B42A14` / `#FFFFFF` | `#FF8A70` / `#111114` |
| `surface`, `background` / `onSurface` | `#F6F6F4` / `#111114` | `#0F0F12` / `#F4F4F1` |
| `surfaceContainerLowest`, `Low`, `Container`, `High`, `Highest` | `#FFFFFF`, `#FAFAF8`, `#F0EFEA`, `#E9E8E2`, `#E1E0D9` | `#0A0A0C`, `#1C1C21`, `#232329`, `#2A2A31`, `#33333B` |
| `onSurfaceVariant` | `#5F5E66` | `#B7B6BF` |
| `outline` / `outlineVariant` | `#76757E` / `#D9D8D2` | `#8E8D97` / `#3A3A42` |
| `inverseSurface` / `inverseOnSurface` / `inversePrimary` | `#111114` / `#F4F4F1` / `#C9F73A` | `#F4F4F1` / `#111114` / `#5B34F5` |

- Mỗi màu thô được khai báo một lần, dưới dạng hằng có tên. Vai màu của Material và `SvPalette` cùng trỏ tới hằng đó, nên chỉnh một giá trị không để lại bản sao cũ.
- Trạng thái đang chọn của các thành phần `Sv…` dùng `inverseSurface` / `inverseOnSurface`: mực ở giao diện sáng, giấy ở giao diện tối. Khối xanh chanh đặc dùng `SvPalette.accent`.
- Các vai mà `SvColors.kt` đang đặt nhưng bảng trên không nêu không được bản thiết kế dùng trực tiếp; chúng nhận giá trị cùng họ để không thành phần Material nào lạc tông: `surfaceVariant` bằng `surfaceContainerHigh`, `surfaceDim` bằng `surfaceContainerHighest`, `surfaceBright` bằng `surfaceContainerLowest`, `surfaceTint` bằng `primary`, các vai `*Fixed` theo `primaryContainer` và chữ trên nó, còn `errorContainer` / `onErrorContainer` là sắc nhạt của `error` (xuất phát `#FBE3DE` / `#3B0F08` ở giao diện sáng, `#5A1A10` / `#FFDAD3` ở giao diện tối). `ColorTokensTest` kiểm chữ trên từng vai như với các vai khác.

Bảng màu riêng `SvPalette`, cấp qua `LocalSvPalette` và đọc bằng `SvTheme.palette`:

| Token | Sáng | Tối | Dùng cho |
|---|---|---|---|
| `brand` / `onBrand` | `#5B34F5` / `#FFFFFF` | như sáng | khối chính, thẻ vé |
| `accent` / `onAccent` | `#C9F73A` / `#111114` | như sáng | khối xanh chanh, nhãn, dấu tích |
| `bar` / `onBar` / `onBarQuiet` | `#111114` / `#FFFFFF` / `#C6C5CE` | `#26262C` / `#F4F4F1` / `#B7B6BF` | thanh nổi |
| `barOutline` | bằng `bar` | `#3A3A42` | viền 1 dp mà các thanh nổi tự vẽ |
| `moneyIn` / `moneyOut` | `#0A6B4E` / `#A83508` | `#5BE3B0` / `#FF9B5E` | chữ "gets back" và "owes" |
| `person[0..6]` / `onPerson` | `#FF6FB5`, `#FF8A3D`, `#5CC8FF`, `#FFD23F`, `#5BE3A0`, `#B9A2FF`, `#7CE0D6` / `#111114` | như sáng | ô chữ cái của người |
| `category[0..8]` | `#FF8A3D`, `#5BE3A0`, `#5CC8FF`, `#B9A2FF`, `#FF6FB5`, `#FFD23F`, `#7CE0D6`, `#A8B4FF`, `#D9D8D2` | như sáng | ô danh mục, theo thứ tự của `BillCategory` |
| `notice` | `#FFD23F` | như sáng | ô biểu tượng của lời nhắc |
| `photoZone` | `#E7E6E0` | `#2A2A31` | nền sau ảnh hóa đơn |

- `moneyOut` ở giao diện sáng là `#A83508`, tính được 4,99:1 trên `#E1E0D9`. Giá trị cũ `#B33A0A` đo được 4,499:1 trên bề mặt đó, dưới ngưỡng.
- `barOutline` là nét trang trí lấy từ bảng tối đã duyệt. Nó không phải thứ duy nhất giúp nhận ra thanh, vì thanh còn có màu nền riêng, chữ và biểu tượng, nên không áp ngưỡng 3:1 cho nó và `ColorTokensTest` không so nó với các bề mặt.
- `ColorTokensTest` thêm: `onBar` và `onBarQuiet` trên `bar` đạt 4,5:1; chữ trên `brand` và `accent`; `moneyIn` và `moneyOut` trên sáu bề mặt; `onPerson` trên từng màu người; mực trên từng màu danh mục và trên `notice`.

Chữ:
- Ba họ phông. `display` gồm Bricolage Grotesque Bold 700 và ExtraBold 800, bản 14pt. `displayHero` là tệp ExtraBold bản 96pt, độ đậm 800, chỉ dành cho `displayLarge` và `amountHero`. `text` gồm Be Vietnam Pro 400, 500, 600, 700.
- Gán họ cho đủ 30 kiểu của Material 3 Expressive, tức 15 kiểu gốc và 15 bản Emphasized của chúng. `display*`, `headline*`, `titleLarge` và bản Emphasized của chúng dùng `display` 800, riêng `displayLarge` dùng `displayHero`. `titleMedium`, `titleSmall`, `body*`, `label*` và bản Emphasized của chúng dùng `text`. Mỗi bản Emphasized lấy cỡ và khoảng dòng của kiểu gốc tương ứng.
- Cỡ chính (sp, cỡ/dòng): `displayLarge` 56/60, `displaySmall` 32/36, `headlineMedium` 26/32, `headlineSmall` 24/30, `titleLarge` 20/26, `titleMedium` 16/22, `bodyLarge` 16/24, `bodyMedium` 15/22, `bodySmall` 13/18, `labelLarge` 14/20, `labelMedium` 12/18, `labelSmall` 11/16.
- Bốn kiểu số tiền được khai báo riêng, đều có `tnum`, không còn dựng từ kiểu Emphasized hay kiểu thân bài: `amountHero` 56/60 thuộc `displayHero`; `amountLarge` 24/28, `amountMedium` 19/24 và `amountSmall` 15/20 thuộc `display`.
- `FontLoadingTest` chạy trong kiểm thử đơn vị của `:app`: mỗi họ vẽ "QUÁN ỐC BA MIỀN ₫1.111.111" với bề rộng khác phông mặc định, và mọi kiểu số tiền cho "1111" và "8888" cùng bề rộng. `TypographyTest` trong `:core:designsystem` là bài thuần: cả 30 kiểu thuộc một trong ba họ, không kiểu nào dùng `FontFamily.Default`, các kiểu số tiền mang `tnum`.

Nguồn gốc của chín tệp, tải ngày 20/11/2026 theo commit ghim:

| Tài nguyên trong module | Kho | Commit | Đường dẫn nguồn | Cỡ (byte) | SHA-256 |
|---|---|---|---|---|---|
| `res/font/bricolage_grotesque_bold.ttf` | `ateliertriay/bricolage` | `84745e5b96261ae5f8c6c856e262fe78d1d6efdd` | `fonts/ttf/BricolageGrotesque-Bold.ttf` | 116260 | `f83cb3f1ddb91bdb02868eeddb4f817b326aef993f96fe6f8a3b40b0f31c689b` |
| `res/font/bricolage_grotesque_extrabold.ttf` | `ateliertriay/bricolage` | `84745e5b96261ae5f8c6c856e262fe78d1d6efdd` | `fonts/ttf/BricolageGrotesque-ExtraBold.ttf` | 118028 | `20ca28c496aef07993031176c1a02da6959682abbc4cbf73776f83e63ef80c00` |
| `res/font/bricolage_grotesque_display_extrabold.ttf` | `ateliertriay/bricolage` | `84745e5b96261ae5f8c6c856e262fe78d1d6efdd` | `fonts/ttf/BricolageGrotesque96pt-ExtraBold.ttf` | 116492 | `66d3137ecbaf1e2efd33eb74bb3dd8bbaedf6c6e3567da2113ec08dff66449eb` |
| `res/raw/bricolage_grotesque_ofl.txt` | `ateliertriay/bricolage` | `84745e5b96261ae5f8c6c856e262fe78d1d6efdd` | `OFL.txt` | 4403 | `4b5a7d8f37f5602621c8a8d7358a6a2e71317e6c231c661e15aef0275d3e07ba` |
| `res/font/be_vietnam_pro_regular.ttf` | `google/fonts` | `cc5661d43cd4f9aad5567033a8e15ca28dc35c86` | `ofl/bevietnampro/BeVietnamPro-Regular.ttf` | 132948 | `cd1ef6e9d7db28ad5cdb88a65ccbe693870e60d340b791f349d248342b4fe4c3` |
| `res/font/be_vietnam_pro_medium.ttf` | `google/fonts` | `cc5661d43cd4f9aad5567033a8e15ca28dc35c86` | `ofl/bevietnampro/BeVietnamPro-Medium.ttf` | 135980 | `b60832bfa0fcd015158112c64d7e3fdad3b0c6287d1823f85a3103636e845268` |
| `res/font/be_vietnam_pro_semibold.ttf` | `google/fonts` | `cc5661d43cd4f9aad5567033a8e15ca28dc35c86` | `ofl/bevietnampro/BeVietnamPro-SemiBold.ttf` | 136736 | `bd8e27eb02720b9d91e59e4f10a90878643219f25ce6a8d9a4f06a8a88d3bb71` |
| `res/font/be_vietnam_pro_bold.ttf` | `google/fonts` | `cc5661d43cd4f9aad5567033a8e15ca28dc35c86` | `ofl/bevietnampro/BeVietnamPro-Bold.ttf` | 140300 | `7f738fe5c43c8872807b20e2d30d42163618de8a4daf7f48a939adac32c16847` |
| `res/raw/be_vietnam_pro_ofl.txt` | `google/fonts` | `cc5661d43cd4f9aad5567033a8e15ca28dc35c86` | `ofl/bevietnampro/OFL.txt` | 4397 | `6b7f8f73609a25ea78c891e34cf37b06f8a676b7ea986e941e43b009110f2a85` |

- URL tải có dạng `https://raw.githubusercontent.com/<kho>/<commit đầy đủ>/<đường dẫn>`. Không bao giờ tải theo tên nhánh.
- Các mã băm trên được tính từ tệp tải qua proxy của môi trường lập plan, nên phải được đối chiếu trên một máy sạch của GitHub. Việc đó là một bước thêm vào workflow sẵn có `.github/workflows/checksums.yml` (chạy tay được, và tự chạy khi chính tệp này đổi): tải chín tệp theo URL có commit rồi so với danh sách.
- Danh sách mã băm được commit cạnh module, ở `core/designsystem/font-checksums.sha256`. Một bài JUnit thuần trong `:core:designsystem` băm chín tài nguyên và so với danh sách đó. Bảng nguồn gốc này được chép vào `docs/design-system.md` ở giai đoạn 10.
- App không có màn, hàng hay chuỗi giấy phép phông nào. Hai văn bản OFL đi kèm phông dưới dạng tài nguyên `res/raw`, và đó là điều giấy phép đòi.
- Tổng cỡ thô của bảy tệp phông là 896744 byte. Mức tăng thật của gói được đo và ghi lại.

Web:
- `WebTokensTest` tiếp tục xuất các biến chữ của web từ `Typography()` gốc của Material và hình khối từ `Shapes()` mặc định, nên chữ và hình khối của web không đổi trong giai đoạn này. Hai biến `--sv-shape-group-outer` và `--sv-shape-group-inner` giữ 20 px và 4 px của hôm nay dưới dạng hằng trong bài kiểm thử, vì `SvShapes` đổi góc nhóm còn `web/src/app.css` đang dùng hai biến đó.
- Bài kiểm thử không xuất token nào của `SvPalette`, và lấy màu bóng từ mực thay cho `onPrimaryFixed`. Màu của web đi theo app, vì bài kiểm thử so từng biến; bóng vẫn xuất từ `SvElevation` nên đổi theo mức nổi mới.
- Trang web được thiết kế lại ở giai đoạn 9. Hệ quả: nếu phải phát hành trước khi giai đoạn 9 xong, các trang web mang màu và bóng mới trên bố cục cũ, và việc đó cần chủ sản phẩm đồng ý trước.

Hình khối và mức nổi:
- `Shapes`: `extraSmall` 8, `small` 12, `medium` 16, `large` 24, `extraLarge` 32 dp. `SvShapes` thêm `pill`, `tile` 14 dp, và góc nhóm ngoài 24 dp, trong 0 dp.
- `SvElevation` giữ bốn mức và API hiện tại. Mức 1 và 2 phẳng ở giao diện sáng; mức 3 là bóng của thanh nổi (mờ 28 dp, lệch 12 dp, 28% màu mực); mức 4 cho vật đang kéo. Màu bóng là mực, không còn lấy từ `onPrimaryFixed`. Ở giao diện tối, mức vẫn là bề mặt sáng dần.
- `SvSpacing` thêm `screen` 20 dp cho lề màn hình.

## Files to Create / Modify

- Create: bảy tệp trong `core/designsystem/src/main/res/font/` và hai tệp trong `core/designsystem/src/main/res/raw/`, đúng tên ở bảng nguồn gốc.
- Create: `core/designsystem/font-checksums.sha256`.
- Create: `core/designsystem/src/main/java/com/dantech/splitvibe/designsystem/tokens/SvPalette.kt` (bảng màu riêng, `LocalSvPalette`, `SvTheme`).
- Create: `core/designsystem/src/test/java/com/dantech/splitvibe/designsystem/tokens/TypographyTest.kt` và `FontChecksumsTest.kt`.
- Create: `app/src/test/java/com/dantech/splitvibe/ui/FontLoadingTest.kt`.
- Modify: `tokens/SvColors.kt`, `tokens/SvTypography.kt`, `tokens/SvShapes.kt`, `tokens/SvElevation.kt`, `tokens/SvSpacing.kt`, `theme/SplitVibeTheme.kt` (truyền `shapes`, cấp `LocalSvPalette`).
- Modify: `components/SvAvatar.kt` (màu người lấy từ `SvPalette`, bỏ `Color.White` viết cứng).
- Modify: `core/designsystem/src/test/.../tokens/ColorTokensTest.kt`, `WebTokensTest.kt`; ghi lại `web/src/tokens.css`.
- Modify: `.github/workflows/checksums.yml` (thêm một bước so mã băm của phông).
- Không sửa `core/designsystem/build.gradle.kts`, `gradle/libs.versions.toml` hay `gradle/verification-metadata.xml`: giai đoạn này không thêm phụ thuộc nào.

## Implementation Steps

1. Việc đầu tiên, để gỡ rủi ro lớn nhất: tải chín tệp theo URL có commit, đối chiếu cỡ và SHA-256 với bảng nguồn gốc, đặt chúng vào `res/font` và `res/raw`, khai báo ba họ phông, rồi viết `FontLoadingTest` trong `:app`. Nếu một họ cho bề rộng bằng phông mặc định, tức Robolectric không nạp phông từ `res/font`, dừng lại và báo trước khi làm tiếp.
2. Ghi `core/designsystem/font-checksums.sha256`, viết `FontChecksumsTest`, và thêm bước so mã băm vào `.github/workflows/checksums.yml`. Đọc kết quả của lượt chạy trên máy GitHub và ghi lại; nếu lệch thì dừng.
3. Dựng `SvTypography`: gán họ, độ đậm và cỡ cho đủ 30 kiểu, và khai báo riêng bốn kiểu số tiền. Mọi kiểu Bricolage có độ đậm ghi rõ. Viết `TypographyTest`.
4. Khai báo các hằng màu, viết lại `LightColors` và `DarkColors` theo bảng vai màu, thêm `SvPalette` cho sáng và tối (kể cả `barOutline`), cấp nó trong `SplitVibeTheme`, và cho `SvAvatar` lấy màu người từ đó.
5. Truyền `Shapes` mới vào `MaterialExpressiveTheme`; cập nhật `SvShapes`, `SvElevation` và `SvSpacing`.
6. Mở rộng `ColorTokensTest` theo danh sách ở mục Requirements. Chỉnh giá trị màu cho tới khi qua.
7. Sửa `WebTokensTest` theo phần Web, rồi ghi lại `web/src/tokens.css` bằng `-PwebTokens=write`. Soát `git diff web/src/tokens.css`: chỉ các biến màu và biến bóng được đổi.
8. Chạy cổng kiểm thử của plan với `-Pscreenshots=compare`. Lấy ảnh trang chủ, màn kết quả, màn nhóm và trang mẫu thành phần, sáng và tối, từ `app/build/outputs/roborazzi/*_actual.png` để gửi chủ sản phẩm. Đo mức tăng của gói và ghi vào báo cáo của giai đoạn.

## Todo

- [ ] Chín tệp khớp cỡ và mã băm, ba họ phông được khai báo, `FontLoadingTest` qua
- [ ] Danh sách mã băm, `FontChecksumsTest`, bước so trong `checksums.yml`; kết quả trên máy GitHub được ghi lại
- [ ] `SvTypography` đủ 30 kiểu và bốn kiểu số tiền; `TypographyTest` qua
- [ ] Hằng màu, bảng màu sáng và tối, `SvPalette` có `barOutline`, `SvAvatar` dùng màu người mới
- [ ] Hình khối, mức nổi, lề màn hình mới
- [ ] `ColorTokensTest` mở rộng và qua
- [ ] `WebTokensTest` sửa, `tokens.css` ghi lại và soát khác biệt
- [ ] Cổng kiểm thử qua, ảnh gửi chủ sản phẩm, số đo mức tăng của gói

## Success Criteria

- Cổng kiểm thử của plan qua (với `-Pscreenshots=compare`); vì giai đoạn này đổi `web/src/tokens.css`, cổng gồm cả các lệnh của việc `proxy` trong `ci.yml`. Các bài riêng của giai đoạn: `ColorTokensTest`, `WebTokensTest`, `TypographyTest` và `FontChecksumsTest` ở `:core:designsystem`; `FontLoadingTest` và `DesignSystemBoundaryTest` ở `:app`.
- Bước so mã băm trong `checksums.yml` qua trên máy của GitHub, và kết quả được ghi trong báo cáo của giai đoạn.
- Lượt `device-tests.yml` trên commit cuối của giai đoạn được đọc và ghi lại, kể cả kiểm tra trợ năng trên máy ảo API 34 với bảng màu và phông mới.
- Ảnh trang chủ và màn kết quả lấy từ `app/build/outputs/roborazzi/*_actual.png` cho thấy hai phông mới, dấu tiếng Việt đúng và ký hiệu ₫.
- `web/src/tokens.css` chỉ khác bản trước ở biến màu và biến bóng.
- `git status --porcelain app/src/test/screenshots` rỗng: không ảnh tham chiếu nào được commit trước giai đoạn 8.

## Risk Assessment

- Robolectric không nạp phông từ `res/font`: dấu hiệu là `FontLoadingTest` cho bề rộng bằng phông mặc định. Ứng phó: dừng và báo; phương án là nạp phông qua `Typeface.createFromAsset` cho riêng kiểm thử.
- Mã băm trên máy sạch lệch với bảng: dấu hiệu là bước trong `checksums.yml` hỏng. Ứng phó: dừng và báo chủ sản phẩm; không sửa danh sách cho khớp khi chưa biết vì sao lệch.
- Một phông đóng gói không nạp được trên máy thật: chữ rơi về phông hệ thống, hoặc app dừng ngay ở lần dựng chữ đầu tiên. Giai đoạn 8 soát cả hai phông trên máy ảo API 26.
- Một cặp màu không đạt tương phản: `ColorTokensTest` chỉ ra cặp nào. Ứng phó: chỉnh sắc độ, giữ sắc màu; nếu phải đổi một màu thấy rõ trên bảng, báo chủ sản phẩm.
- Thành phần Material chưa bọc vẫn đọc `secondaryContainer` và `tertiaryContainer`: với hai sắc nhạt ở trên, chúng vẫn là bề mặt phụ. `SvEmptyState` được sửa ở giai đoạn 2. Trong 11 nút `FilledTonalButton`, một nút mất cùng các nút dẫn của trang chủ ở giai đoạn 3, số còn lại được chuyển sang thành phần `Sv…` ở các giai đoạn sở hữu màn hình (5, 6, 7).
- Phát hành trước khi giai đoạn 9 xong: trang web mang màu và bóng mới trên bố cục cũ. Ứng phó: hỏi chủ sản phẩm trước khi phát hành.

## Rollback

Hoàn lại các commit của giai đoạn chỉ dùng được khi giai đoạn 2 chưa bắt đầu. Sau đó, lỗi được sửa bằng commit mới, vì các giai đoạn sau dựng trên `SvPalette`, ba họ phông và bộ hình khối này. Không có dữ liệu hay giao kèo nào đổi; `web/src/tokens.css` chỉ tới người dùng ở lần triển khai Worker kế tiếp.
