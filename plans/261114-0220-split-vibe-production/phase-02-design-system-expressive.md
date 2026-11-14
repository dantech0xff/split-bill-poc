---
title: "Phase 2: Hệ thiết kế Expressive, chuyển động và đổ bóng"
status: in-progress
phase: 2
priority: P1
effort: "10d"
dependencies: [1]
---

# Phase 2: Hệ thiết kế Expressive, chuyển động và đổ bóng

## Overview

Split Vibe có ngôn ngữ thiết kế riêng: Material 3 Expressive, chuyển động mượt và đo được, bóng đổ mềm có ánh màu, bố cục sạch. Toàn bộ màn hình hiện có được dựng lại trên hệ thiết kế đó, và mọi màn hình ở các giai đoạn sau chỉ dùng thành phần của nó.

## Context

- Bốn yêu cầu của chủ sản phẩm: dùng Material Expressive, nhiều animation mượt, đổ bóng đẹp, giao diện clean.
- Giao diện hiện tại là Material 3 mặc định với bảng màu cố định trong `app/src/main/java/com/dantech/splitvibe/ui/theme/Theme.kt:43`, không có chuyển cảnh riêng và không có bóng đổ tùy biến.
- Dữ kiện API: [báo cáo giao diện và Play](../reports/researcher-261114-0209-android-expressive-ui-and-play-requirements.md), mục 1 đến 4.

## Key Insights

- Trong material3 1.5.0, `MaterialExpressiveTheme`, `MotionScheme.expressive()`, `ButtonGroup`, `SplitButtonLayout`, `FloatingActionButtonMenu`, thanh công cụ nổi, thanh tiến trình lượn sóng, thanh trên linh hoạt và kiểu chữ nhấn mạnh đều đã ổn định. Chỉ `LoadingIndicator` và `MaterialShapes` còn cần opt-in.
- `MaterialExpressiveTheme` mặc định chỉ có bảng màu sáng, nên app phải tự cấp bảng màu tối.
- `Modifier.dropShadow` và `Modifier.innerShadow` (có từ Compose UI 1.9) cho bóng có bán kính, độ lan, màu và độ lệch tùy ý. Bộ đệm của chúng không tính độ lệch, nên **animate độ lệch và độ trong thì rẻ**, còn animate bán kính, độ lan hay kích thước thì phải làm mờ lại từng khung hình.
- Màu của bóng theo độ nổi (`Modifier.shadow`) chỉ có tác dụng từ Android 9. App hỗ trợ từ Android 8 (minSdk 26), nên bóng thương hiệu dùng `dropShadow`.
- Chuyển cảnh dùng chung phần tử (`SharedTransitionLayout`, `sharedBounds`) đã ổn định từ animation 1.10.
- Các animation chuẩn của Compose tự tắt khi người dùng tắt animation hệ thống; hiệu ứng lặp vô hạn do app tự viết thì phải tự kiểm.

## Requirements

Nguyên tắc giao diện sạch:
1. Mỗi màn hình có một hành động chính, đặt ở nút lớn dưới cùng hoặc nút nổi.
2. Số tiền là nhân vật chính: kiểu chữ nhấn mạnh, chữ số đều chiều rộng, căn phải.
3. Khoảng trắng theo lưới 4dp. Nội dung được nhóm bằng khoảng cách và bề mặt, hạn chế đường kẻ.
4. Một màu nhấn cộng các màu trung tính. Màu chỉ dùng khi mang nghĩa (trạng thái, từng người).
5. Chuyển động phải giải thích được quan hệ không gian hoặc phản hồi một hành động. Không có chuyển động chỉ để trang trí.
6. Bóng đổ thể hiện tầng lớp, có 4 mức. Ở chế độ tối, tầng lớp thể hiện bằng bề mặt sáng dần thay cho bóng.

Bảng chuyển động theo hành trình:

| Hành trình | Chuyển động | API |
|---|---|---|
| Trang chủ sang chia hóa đơn | Nút quét nở ra thành màn hình | `SharedTransitionLayout`, `sharedBounds` |
| Chọn ảnh sang xem trước | Ảnh thu nhỏ bay vào khung, góc bo biến đổi | `sharedElement` |
| Đang đọc hóa đơn | Vệt quét chạy trên ảnh, biểu tượng tải đổi hình, dòng trạng thái chuyển mượt | `graphicsLayer {}`, `LoadingIndicator`, `AnimatedContent` |
| Kết quả hiện ra | Tổng tiền đếm lên, các món vào lần lượt | `animateItem`, spec không gian của `motionScheme` |
| Đổi người chia | Thẻ người bật nảy, số tiền lăn tới giá trị mới, hàng đổi chỗ mượt | spec lò xo, `animateItem(placementSpec)` |
| Bảng trượt từ dưới | Kéo theo tay, có xem trước khi vuốt ngược | `ModalBottomSheet` |
| Gửi yêu cầu | Nút biến hình thành dấu tích, rung xác nhận | `Morph` của graphics-shapes, `HapticFeedbackType.Confirm` |
| Lùi màn hình | Thấy trước màn hình phía sau khi vuốt | Navigation 3 |
| Cuộn danh sách | Thanh trên co lại theo cuộn | thanh trên linh hoạt với `scrollBehavior` |

Ngân sách hiệu năng (đo bằng `FrameTimingMetric` trên máy tham chiếu, ba hành trình: khởi động, chia hóa đơn mẫu, cuộn kết quả):
- `frameDurationCpuMs` P50 không quá 6 ms và P95 không quá 12 ms.
- `frameOverrunMs` P95 không dương, tức 95% khung hình xong trước hạn.
- Khởi động nguội tới khung đầu tiên dưới 1 giây khi có baseline profile.
- Các ngưỡng này là mục tiêu ban đầu. Chỉ được nới sau khi có số đo gốc của Phase 1 và chủ sản phẩm đồng ý.

Tiếp cận:
- Độ tương phản chữ từ 4,5:1 trở lên, vùng chạm từ 48dp, cỡ chữ 200% không cắt chữ, có nhãn cho TalkBack.
- Khi hệ thống tắt animation, không còn hiệu ứng lặp vô hạn nào chạy và mọi luồng vẫn dùng được.

## Architecture

Module `:core:designsystem` (thư viện Android), gồm:
- `tokens/`: màu (sáng và tối), kiểu chữ (kể cả các kiểu nhấn mạnh), hình khối, khoảng cách, bóng (`SvShadow.Level1` đến `Level4`, mỗi mức gồm một lớp bóng nền bán kính lớn độ đậm thấp và một lớp bóng chính lệch nhỏ có ánh màu), chuyển động (tên gọi theo ý nghĩa, lấy spec từ `MaterialTheme.motionScheme`), bản đồ rung.
- `theme/SplitVibeTheme.kt`: bọc `MaterialExpressiveTheme` với `MotionScheme.expressive()`.
- `components/`: nút chính, nhóm nút, nút tách đôi, nút nổi có menu, thanh công cụ nổi, thẻ có bóng, chữ số tiền có hiệu ứng lăn, ảnh đại diện, thẻ người, hàng món, bảng trượt, trạng thái tải, trạng thái rỗng.
- `motion/`: chuyển cảnh dùng chung phần tử, kiểm tra "giảm chuyển động".

Quy tắc ranh giới:
- Mã tính năng không gọi `MaterialTheme(`, không tự đặt màu, bóng hay spec animation; chỉ dùng thành phần và token của module này.
- `@OptIn(ExperimentalMaterial3ExpressiveApi::class)` chỉ xuất hiện trong `:core:designsystem`. Khi material3 đổi API, chỉ module này phải sửa.

## Files

Tạo mới:
- `core/designsystem/build.gradle.kts` và mã nguồn theo cấu trúc trên
- `app/src/debug/java/com/dantech/splitvibe/catalog/` (màn hình danh mục thành phần, chỉ có ở bản debug)
- `docs/design-system.md`

Sửa (dựng lại trên hệ thiết kế):
- `app/src/main/java/com/dantech/splitvibe/ui/home/HomeScreen.kt`
- `app/src/main/java/com/dantech/splitvibe/ui/split/NewSplitContent.kt`, `ResultContent.kt`, `SplitSheets.kt`, `SentContent.kt`, `SplitScreen.kt`
- `app/src/main/java/com/dantech/splitvibe/ui/settings/SettingsScreen.kt`
- `app/src/main/java/com/dantech/splitvibe/ui/components/PersonAvatar.kt` (chuyển vào hệ thiết kế)
- `app/src/main/java/com/dantech/splitvibe/ui/theme/Theme.kt` (xóa, thay bằng theme của hệ thiết kế)
- `app/src/test/java/com/dantech/splitvibe/ui/ScreenGalleryTest.kt` (ma trận ảnh chụp mới)

## Implementation Steps

1. Dựng ba hướng bảng màu trên màn hình kết quả bằng đường dựng ảnh của Roborazzi; chủ sản phẩm chọn một.
2. Viết token và `SplitVibeTheme` cho cả sáng và tối.
3. Viết token bóng bằng `dropShadow`, kèm quy tắc: chỉ animate độ lệch và độ trong.
4. Viết các thành phần và màn hình danh mục ở bản debug.
5. Viết lớp chuyển động: chuyển cảnh dùng chung phần tử giữa các màn hình, hiệu ứng lăn số, vào lần lượt, biến hình nút thành dấu tích, rung.
6. Dựng lại từng màn hình hiện có; mỗi màn hình xong thì ghi ảnh chuẩn.
7. Viết kiểm tra ranh giới chạy trong CI (không có `MaterialTheme(` hay opt-in Expressive ngoài module).
8. Đo ba hành trình trên máy tham chiếu, tối ưu cho tới khi đạt ngân sách, ghi báo cáo số đo.
9. Kiểm tra tiếp cận và chế độ tắt animation; viết `docs/design-system.md`.

## Ba hướng bảng màu: chủ sản phẩm chọn B. Violet

<!-- Updated: Validation Session 3 - chủ sản phẩm chọn hướng B. Violet ngày 14/11/2026 -->
Ba hướng được dựng trên màn hình kết quả thật, với dữ liệu của hóa đơn mẫu, ở cả sáng và tối:
[ảnh sáng](./assets/palette-directions/three-directions-light.png), [ảnh tối](./assets/palette-directions/three-directions-dark.png).

| Hướng | Màu nhấn (sáng / tối) | Màu phụ thứ ba | Cảm giác |
|---|---|---|---|
| A. Emerald | `#006C4C` / `#53DEA8` | Vàng hổ phách `#7A5901` | Gần với bản hiện tại, gợi tiền bạc và sự tin cậy |
| B. Violet | `#554ACC` / `#CEBDFF` | Hồng mận `#A53167` | Trẻ, năng động, hợp với chữ "Vibe" trong tên |
| C. Coral | `#A83900` / `#FFB595` | Xanh mòng két `#00696C` | Ấm, gợi ăn uống và bạn bè |

Cách dựng: mỗi hướng là một bộ thang sắc độ (màu nhấn, màu phụ, màu thứ ba, trung tính), mỗi vai trò màu lấy một sắc độ cố định theo bảng của Material 3 (ví dụ `primary` là sắc độ 40 ở sáng và 80 ở tối). Vì vậy chữ và nền luôn lệch nhau ít nhất 50 sắc độ; độ tương phản thấp nhất đo được trong cả sáu bảng là 6,15:1, trên ngưỡng 4,5:1. Đây là màu để chọn hướng. Giá trị cuối cùng của từng token được tinh chỉnh ở bước 2 sau khi có lựa chọn.

Màu ảnh đại diện của từng người trong ảnh vẫn là màu cũ; chúng sẽ được làm lại theo bảng màu được chọn.

## Todo

- [x] Chủ sản phẩm chọn bảng màu từ ảnh dựng thật
- [ ] Token và `SplitVibeTheme` (sáng, tối). Đã có màu, kiểu chữ số tiền, khoảng cách và theme; token hình khối chưa tách riêng, các thành phần đang dùng thang hình khối mặc định của Material
- [x] Token bóng 4 mức bằng `dropShadow`
- [ ] Bộ thành phần và màn hình danh mục. Đã có màn hình danh mục ở bản debug và 12 thành phần mà các màn hình hiện tại dùng. Chưa có nhóm nút, nút tách đôi, nút nổi có menu và thanh công cụ nổi: chưa màn hình nào cần tới, sẽ thêm cùng tính năng dùng chúng ở Phase 4 trở đi
- [ ] Lớp chuyển động theo bảng hành trình. Đã có: tổng tiền đếm lên và số tiền chạy tới giá trị mới, các hàng vào lần lượt, dòng trạng thái chuyển mượt, biểu tượng tải đổi hình, dấu tích bật ra kèm rung xác nhận, thanh trên co lại theo cuộn, xem trước khi vuốt ngược. Chưa có: nút quét nở thành màn hình, ảnh bay vào khung, vệt quét trên ảnh, thẻ người bật nảy và hàng đổi chỗ, nút biến hình thành dấu tích
- [x] Dựng lại 7 màn hình và 2 bảng trượt hiện có ([ảnh trước và sau của hai màn hình chính](./assets/first-look-home-and-result.png)). Chủ sản phẩm chưa duyệt
- [x] Kiểm tra ranh giới trong CI (`DesignSystemBoundaryTest`, chạy cùng unit test)
- [ ] Đạt ngân sách hiệu năng trên máy tham chiếu
- [ ] Tiếp cận và chế độ tắt animation. Đã có: test độ tương phản, ảnh cỡ chữ 200%, `ReducedMotionTest`. Chưa có: kiểm tra tiếp cận tự động của Compose
- [ ] `docs/design-system.md`. Đã viết cho phần đang có; còn bổ sung khi có lớp chuyển động và màn hình danh mục

## Success Criteria

- Mọi màn hình hiện có chỉ dùng thành phần của `:core:designsystem`; kiểm tra ranh giới trong CI xanh.
- <!-- Updated: Validation Session 1 - v1.0 chỉ có tiếng Anh --> Ma trận ảnh chụp có ảnh chuẩn: mọi màn hình ở sáng và tối; bốn màn hình chính thêm cỡ chữ 200%.
- Báo cáo đo trên máy tham chiếu cho thấy ba hành trình đạt ngân sách hiệu năng.
- Test với animation hệ thống đã tắt: không có hiệu ứng lặp nào chạy, luồng chia hóa đơn vẫn hoàn tất.
- Kiểm tra tiếp cận tự động của Compose qua trên các màn hình chính; ảnh ở cỡ chữ 200% không cắt chữ.
- Chủ sản phẩm duyệt giao diện từ bộ ảnh dựng và một bản cài trên máy thật.
- Toàn bộ unit test hiện có và `SplitFlowTest` vẫn qua.

## Risks

| Rủi ro | Dấu hiệu nhận biết | Phản ứng đã định |
|---|---|---|
| Bóng làm mờ gây giật trên máy yếu | `frameDurationCpuMs` P95 vượt ngân sách ở màn hình nhiều thẻ | Giảm số phần tử có bóng, dùng bóng theo độ nổi cho danh sách dài |
| Bóng trong ảnh dựng lệch so với máy thật. Đường dựng ảnh đã vẽ được cả bốn kiểu bóng (kiểm 14/11, xem `assets/shadow-styles-check.png`), nhưng độ khớp với máy thật chưa được so `[UNVERIFIED]` | So ảnh dựng với ảnh chụp máy thật thấy lệch | Duyệt bóng bằng ảnh chụp máy thật; ảnh dựng chỉ dùng chống hồi quy |
| Quá nhiều chuyển động gây mệt | Phản hồi của người thử | Giữ chuyển động ở bảng hành trình; có chế độ giảm chuyển động |
| Chuyển cảnh dùng chung phần tử vướng với Navigation 3 | Phần tử nhảy hoặc nhấp nháy khi vuốt ngược | Dùng `sharedBounds` thay `sharedElement`, hoặc bỏ chuyển cảnh đó ở hành trình bị lỗi |
| `SplitButtonLayout` đổi tên ở bản stable `[UNVERIFIED]` | Cảnh báo ngừng dùng khi nâng phiên bản | Sửa trong một tệp thành phần duy nhất |

## Security

Không có bề mặt bảo mật mới. Màn hình danh mục chỉ có trong source set `debug`.

## Next

Mọi màn hình mới ở Phase 4 đến Phase 8 dựng bằng hệ thiết kế này và thêm ảnh chuẩn vào cùng ma trận.
