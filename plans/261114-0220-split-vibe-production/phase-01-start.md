---
title: "Phase 1: Nền móng kỹ thuật"
status: todo
phase: 1
priority: P1
effort: "6d"
dependencies: []
---

# Phase 1: Nền móng kỹ thuật

## Overview

Đưa dự án lên chuẩn production về công cụ và cấu trúc mà không đổi hành vi của app: thư viện giao diện đủ mới để dùng Material 3 Expressive, có DI, điều hướng mới, CI, kiểm tra ảnh chụp màn hình và hồ sơ hiệu năng. Sau giai đoạn này, các giai đoạn sau chỉ còn làm tính năng.

## Context

- Việc đã xong trước giai đoạn này: package đổi thành `com.dantech.splitvibe`, tên hiển thị là "Split Vibe" (commit `d66096f`), 80 unit test vẫn qua.
- Dữ kiện nền: [báo cáo giao diện và Play](../reports/researcher-261114-0209-android-expressive-ui-and-play-requirements.md).
- Hiện trạng cần sửa:
  - `gradle/libs.versions.toml:4` dùng Compose BOM 2026.03.01, tức material3 1.4.0, chưa có API Expressive nào.
  - `app/build.gradle.kts:42` ký bản release bằng khóa debug.
  - `app/src/main/java/com/dantech/splitvibe/AppContainer.kt:15` nối đối tượng bằng tay.
  - `app/src/main/java/com/dantech/splitvibe/ui/AppNavHost.kt:29` dùng navigation-compose 2.9.8 với 3 màn hình.
  - Repo chưa có `.github/`, tức chưa có CI.
  - Ba lớp test trên thiết bị trong `app/src/androidTest/` chưa từng chạy bằng tác vụ Gradle.

## Key Insights

- Muốn có Expressive phải nâng BOM lên 2026.09.00 **và** ghim riêng `androidx.compose.material3:material3:1.5.0-rc01`, vì mọi BOM hiện nay vẫn kèm 1.4.0. Bản 1.5.0 đã đóng băng API ở mức RC; chỉ `LoadingIndicator`, `ContainedLoadingIndicator` và `MaterialShapes` còn cần opt-in.
- Nâng thư viện giao diện là thay đổi có rủi ro hình ảnh, nên chụp bộ ảnh chuẩn của 12 màn hình **trước** khi nâng, rồi so lại sau khi nâng.
- Roborazzi 1.76.0 đã chạy được trên dự án này với AGP 9.1.1 (ngày 14/11, dùng thư viện, chưa dùng plugin Gradle). Bài test dựng 12 màn hình được lưu tại [assets/screen-gallery-test.kt.txt](./assets/screen-gallery-test.kt.txt) để làm điểm khởi đầu.
- Navigation 3 bản 1.2.1 đã ổn định và có sẵn API cho vuốt ngược xem trước, deep link và kết quả trả về. Google chưa bắt buộc dùng nó, nhưng app sắp có nhiều màn hình và nhiều chuyển cảnh nên chuyển ngay khi mới có 3 màn hình là rẻ nhất.

## Requirements

Chức năng: không đổi. Luồng chụp hóa đơn, phân tích, chọn người, gửi yêu cầu chạy y như trước.

Phi chức năng:
- Mọi thay đổi vào `main` đi qua CI: build, unit test, lint, so ảnh chụp, quét secret.
- Bản release ký bằng khóa upload lấy từ secret của CI, xuất ra AAB.
- Phần tính tiền nằm trong một module Kotlin thuần, không phụ thuộc Android.

## Architecture

```
:app                      giao diện, điều hướng, DI, OCR, phần gọi AI
:core:domain              Kotlin thuần (JVM): Bill, BillCurrency, SplitCalculator, SplitPlan,
                          NameMatcher, BillReconciler, OcrResult, OcrLayoutEncoder, BillAnalysisParser
:baseline-profile         com.android.test: sinh baseline profile và đo macrobenchmark
(:core:designsystem được tạo ở Phase 2)
```

Quyết định kỹ thuật (người làm đề xuất, chủ sản phẩm có thể đổi):
- **DI: Hilt.** Lý do: chuẩn của Android, kiểm lỗi lúc biên dịch. Điều kiện lùi: nếu plugin Hilt chưa chạy được với AGP 9.1.1 và Kotlin tích hợp sẵn `[UNVERIFIED]` sau nửa ngày thử, dùng Koin.
- **Điều hướng: Navigation 3 bản 1.2.1.** Điều kiện lùi: nếu sau một ngày thử mà chưa đạt cả ba việc (khôi phục sau khi tiến trình bị hủy, vuốt ngược xem trước, deep link), ở lại navigation-compose và nâng lên 2.10.2.

## Files

Tạo mới:
- `core/domain/build.gradle.kts` và mã nguồn chuyển từ `app/src/main/java/com/dantech/splitvibe/domain/`, `app/src/main/java/com/dantech/splitvibe/ocr/OcrResult.kt`, `app/src/main/java/com/dantech/splitvibe/ocr/OcrLayoutEncoder.kt`, `app/src/main/java/com/dantech/splitvibe/llm/BillAnalysisParser.kt` cùng các test tương ứng
- `baseline-profile/build.gradle.kts` và các hành trình đo
- `.github/workflows/ci.yml`, `.github/workflows/device-tests.yml`, `.github/dependabot.yml`
- `app/src/test/java/com/dantech/splitvibe/ui/ScreenGalleryTest.kt` và thư mục ảnh chuẩn
- `docs/architecture.md`

Sửa:
- `settings.gradle.kts` (thêm module), `gradle/libs.versions.toml` (BOM 2026.09.00, material3 1.5.0-rc01, Navigation 3, Hilt, Roborazzi, benchmark 1.5.0)
- `app/build.gradle.kts` (ký release, hai flavor `dev` và `prod`, baseline profile, bỏ dòng 42)
- `app/src/main/java/com/dantech/splitvibe/AppContainer.kt` (thay bằng module DI), `SplitBillApp.kt` (đổi tên thành `SplitVibeApp`), `MainActivity.kt`
- `app/src/main/java/com/dantech/splitvibe/ui/AppNavHost.kt` (chuyển sang Navigation 3)
- `app/src/main/java/com/dantech/splitvibe/ui/theme/Theme.kt` (đổi tên `SplitBillTheme` thành `SplitVibeTheme`; 12 chỗ tham chiếu)
- `README.md` (công cụ, module, cách chạy kiểm tra)

## Implementation Steps

1. Thêm Roborazzi, đưa bài test dựng màn hình vào repo, ghi bộ ảnh chuẩn của 12 màn hình hiện tại.
2. Nâng BOM lên 2026.09.00 và ghim material3 1.5.0-rc01; chạy so ảnh, xem từng khác biệt, ghi lại ảnh chuẩn sau khi duyệt.
3. Tách `:core:domain`, chuyển mã và test; các test đó chạy như test JVM thuần.
4. Thử Hilt trong nửa ngày theo điều kiện lùi ở trên; thay `AppContainer`; đổi tên `SplitBillApp` và `SplitBillTheme`.
5. Chuyển 3 màn hình sang Navigation 3 theo điều kiện lùi ở trên; kiểm vuốt ngược xem trước trên máy ảo API 36.
6. Cấu hình ký release bằng biến môi trường, xuất AAB, hai flavor `dev` (hậu tố `.dev`) và `prod`.
7. Viết CI: build debug và release, unit test, Android Lint, kiểm định dạng mã, so ảnh chụp, quét secret; bật Dependabot.
8. Viết luồng chạy test trên thiết bị bằng Gradle Managed Devices (API 34) chạy hằng đêm cho 3 lớp test có sẵn.
9. Thêm `:baseline-profile`, sinh profile, viết hành trình đo khởi động và luồng chia hóa đơn; ghi số đo gốc vào `plans/reports/`.
10. Cập nhật README và viết `docs/architecture.md`.

## Todo

- [ ] Bộ ảnh chuẩn 12 màn hình trước khi nâng thư viện
- [ ] BOM 2026.09.00 và material3 1.5.0-rc01, khác biệt hình ảnh đã duyệt
- [ ] Module `:core:domain` với test JVM thuần
- [ ] DI thay `AppContainer`; đổi tên `SplitVibeApp` và `SplitVibeTheme`
- [ ] Navigation 3 cho 3 màn hình, vuốt ngược xem trước hoạt động
- [ ] Ký release bằng khóa upload, AAB, flavor `dev` và `prod`
- [ ] CI cho PR và `main`; Dependabot
- [ ] Test trên thiết bị chạy hằng đêm trong CI
- [ ] Module `:baseline-profile` và số đo gốc
- [ ] README và `docs/architecture.md`

## Success Criteria

- `./gradlew :app:assembleProdRelease :app:testProdDebugUnitTest :core:domain:test` chạy xanh; tổng số test không ít hơn 80 và `SplitFlowTest` không đổi phần khẳng định.
- Báo cáo phụ thuộc của Gradle cho thấy material3 1.5.0-rc01 và Compose UI 1.12.x.
- So ảnh chụp trong CI xanh; một thay đổi giao diện cố ý làm CI đỏ (thử một lần để chứng minh cổng hoạt động).
- Ba lớp test trên thiết bị (`KeystoreCipherTest`, `MlKitBillTextRecognizerTest`, `ClaudeBillAnalyzerOnDeviceTest`) qua trên thiết bị do Gradle quản lý trong CI.
- AAB release không ký bằng khóa debug: `app/build.gradle.kts` không còn dòng `signingConfigs.getByName("debug")` trong khối release.
- `:core:domain` là module `kotlin("jvm")`, nên không thể nhập lớp Android.
- Vuốt ngược xem trước chạy trên cả 3 màn hình ở máy ảo API 36 (kiểm tay, có quay màn hình lưu vào `plans/reports/`).
- Có số đo gốc: thời gian khởi động nguội và thời gian khung hình của luồng chia hóa đơn.

## Risks

| Rủi ro | Dấu hiệu nhận biết | Phản ứng đã định |
|---|---|---|
| Plugin Hilt chưa hợp AGP 9.1.1 | Build lỗi ở bước KSP hoặc plugin sau nửa ngày thử | Dùng Koin, ghi quyết định vào `docs/architecture.md` |
| Navigation 3 thiếu thứ cần | Không đạt một trong ba việc sau một ngày thử | Ở lại navigation-compose 2.10.2 |
| material3 1.5.0 đổi API khi ra bản stable | Lỗi biên dịch khi nâng từ rc01 | Sửa trong `:core:designsystem`; không phát hành production khi material3 chưa stable |
| Plugin Gradle của Roborazzi chưa hợp AGP 9 | Tác vụ `verifyRoborazziDebug` lỗi | Dùng thư viện với thuộc tính hệ thống như đã chạy ngày 14/11 |
| Số đo khung hình trên máy ảo không đáng tin | Số đo dao động lớn giữa các lần chạy | Chỉ dùng máy thật làm chuẩn; CI chỉ sinh baseline profile |

## Security

- Khóa upload và mật khẩu chỉ nằm trong secret của CI; không có tệp khóa nào trong repo (`.gitignore` đã chặn `*.jks` và `*.keystore`).
- Repo đang công khai, nên CI có bước quét secret trên mọi PR.
- Flavor `dev` dùng hậu tố `.dev` để không lẫn dữ liệu với bản thật.

## Next

Phase 2 tạo `:core:designsystem` trên nền thư viện đã nâng. Phase 3 dùng flavor `dev` và `prod` cho hai project Firebase.
