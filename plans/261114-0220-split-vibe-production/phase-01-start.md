---
title: "Phase 1: Nền móng kỹ thuật"
status: in-progress
phase: 1
priority: P1
effort: "6d"
dependencies: []
---

# Phase 1: Nền móng kỹ thuật

## Overview

Đưa dự án lên chuẩn production về công cụ và cấu trúc mà không đổi hành vi của app: thư viện giao diện đủ mới để dùng Material 3 Expressive, DI bằng Koin, điều hướng bằng Navigation 3, CI an toàn cho repo công khai, kiểm tra ảnh chụp màn hình và hồ sơ hiệu năng. Sau giai đoạn này, các giai đoạn sau chỉ còn làm tính năng.

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
- Roborazzi 1.76.0 đã chạy được trên dự án này với AGP 9.1.1 (ngày 14/11, dùng thư viện, chưa dùng plugin Gradle) và vẽ được cả bóng mờ ([ảnh mẫu](./assets/shadow-styles-check.png)). Bài test dựng 12 màn hình được lưu tại [assets/screen-gallery-test.kt.txt](./assets/screen-gallery-test.kt.txt) để làm điểm khởi đầu.
- Koin không cần plugin Gradle hay bộ sinh mã, nên không vướng câu hỏi tương thích với AGP 9. Vì đồ thị phụ thuộc chỉ được kiểm lúc chạy, cần một unit test kiểm toàn bộ module Koin để lỗi nối dây lộ ra trong CI.
- Repo công khai, nên CI phải coi mọi pull request là mã không tin cậy: không secret nào được có mặt khi build PR.

## Requirements

Chức năng: không đổi. Luồng chụp hóa đơn, phân tích, chọn người, gửi yêu cầu chạy y như trước.

Phi chức năng:
- Mọi thay đổi vào `main` đi qua CI: build, unit test, lint, so ảnh chụp, quét secret.
- CI của pull request không có secret nào; việc ký chỉ xảy ra khi gắn tag phiên bản, trong một môi trường có người duyệt.
- Phần tính tiền và các giao kèo dữ liệu nằm trong một module Kotlin thuần, không phụ thuộc Android.

## Architecture

```
:app                      giao diện, điều hướng, Koin, OCR bằng ML Kit, phần gọi AI
:core:domain              Kotlin thuần (JVM), giữ nguyên tên package:
                            domain/   Bill, BillCurrency, SplitCalculator, SplitPlan, NameMatcher, BillReconciler
                            data/contacts/Contact
                            ocr/      OcrResult, OcrLayoutEncoder
                            llm/      BillAnalyzer, BillAnalysis, BillAnalyzerException, BillAnalysisParser
:baseline-profile         com.android.test: sinh baseline profile và đo macrobenchmark
(:core:designsystem được tạo ở Phase 2)
```

<!-- Red Team: tách :core:domain không biên dịch được như bản nháp — 2026-11-14 -->
Vì sao danh sách chuyển gồm cả `Contact` và `BillAnalyzer.kt`: `domain/SplitPlan.kt:3` và `domain/NameMatcher.kt:3` nhập `Contact`; `llm/BillAnalysisParser.kt:11` dùng `BillAnalyzerException` và trả về `BillAnalysis`, cả hai định nghĩa trong `llm/BillAnalyzer.kt`. Ba việc đi kèm:
- `BillAnalysisParser` đang là `internal object` (`llm/BillAnalysisParser.kt:33`) phải thành công khai, vì nơi gọi nó ở lại `:app`.
- `core/domain/build.gradle.kts` áp plugin kotlinx.serialization.
- `app/build.gradle.kts` thêm `implementation(project(":core:domain"))`. Tên package không đổi nên các dòng `import` hiện có giữ nguyên.

<!-- Updated: Validation Session 1 - DI và điều hướng theo lựa chọn của chủ sản phẩm -->
Quyết định của chủ sản phẩm: **Koin** cho DI và **Navigation 3** cho điều hướng.
- Koin 4.2.2 (`koin-android`, `koin-androidx-compose`). `AppContainer` được thay bằng các module Koin; ViewModel lấy bằng `koinViewModel()`.
- Navigation 3 bản 1.2.1 (`navigation3-runtime`, `navigation3-ui`) cùng `androidx.lifecycle:lifecycle-viewmodel-navigation3` để gắn ViewModel theo từng mục trong ngăn xếp.
- Nếu Navigation 3 vướng một trong ba việc (khôi phục sau khi tiến trình bị hủy, vuốt ngược xem trước, deep link) sau một ngày thử, người làm dừng lại và báo chủ sản phẩm kèm bằng chứng; không tự quay về navigation-compose.

## Files

Tạo mới:
- `core/domain/build.gradle.kts`
- `baseline-profile/build.gradle.kts` và các hành trình đo
- `.github/workflows/ci.yml`, `.github/workflows/device-tests.yml`, `.github/dependabot.yml`
- `gradle/verification-metadata.xml` (kiểm chữ ký phụ thuộc)
- `app/src/main/java/com/dantech/splitvibe/di/AppModule.kt`
- `app/src/test/java/com/dantech/splitvibe/di/AppModuleTest.kt` (kiểm đồ thị Koin)
- `app/src/test/java/com/dantech/splitvibe/ui/ScreenGalleryTest.kt` và thư mục ảnh chuẩn
- `docs/architecture.md`

Chuyển sang `:core:domain` (cùng các test tương ứng trong `app/src/test/`):
- `app/src/main/java/com/dantech/splitvibe/domain/` (cả thư mục)
- `app/src/main/java/com/dantech/splitvibe/data/contacts/Contact.kt`
- `app/src/main/java/com/dantech/splitvibe/ocr/OcrResult.kt`, `app/src/main/java/com/dantech/splitvibe/ocr/OcrLayoutEncoder.kt`
- `app/src/main/java/com/dantech/splitvibe/llm/BillAnalyzer.kt`, `app/src/main/java/com/dantech/splitvibe/llm/BillAnalysisParser.kt`

<!-- Red Team: danh sách tệp bỏ sót nơi dùng — 2026-11-14 -->
Sửa:
- `settings.gradle.kts` (thêm module), `gradle/libs.versions.toml` (BOM 2026.09.00, material3 1.5.0-rc01, Navigation 3, Koin, Roborazzi, benchmark 1.5.0)
- `app/build.gradle.kts` (phụ thuộc vào `:core:domain`, cấu hình ký đọc từ biến môi trường và không có thì ra bản chưa ký, hai flavor `dev` và `prod`, baseline profile, bỏ dòng 42)
- Thay `AppContainer` bằng Koin, 4 nơi dùng: `app/src/main/java/com/dantech/splitvibe/AppContainer.kt` (xóa), `SplitBillApp.kt:6`, `ui/AppNavHost.kt:23`, `ui/split/SplitBillViewModel.kt:212-216` (factory lấy `container.contacts`, `container.settings`, `container.imageLoader`, `container.recognizer`)
- Đổi tên `SplitBillApp` thành `SplitVibeApp`, 4 chỗ tham chiếu: `app/src/main/AndroidManifest.xml:9` (`android:name`), `MainActivity.kt:14`, `ui/split/SplitBillViewModel.kt:10` và `:209`
- Đổi tên `SplitBillTheme` thành `SplitVibeTheme`, 5 chỗ trong 3 tệp: `ui/theme/Theme.kt:43`, `MainActivity.kt:8` và `:16`, `app/src/test/java/com/dantech/splitvibe/ui/split/SplitFlowTest.kt:28` và `:90`; thêm bản lưu trong `assets/screen-gallery-test.kt.txt`
- `app/src/main/java/com/dantech/splitvibe/ui/AppNavHost.kt` và `MainActivity.kt` (chuyển sang Navigation 3)
- `README.md` (công cụ, module, cách chạy kiểm tra)

## Implementation Steps

1. Thêm Roborazzi, đưa bài test dựng màn hình vào repo, ghi bộ ảnh chuẩn của 12 màn hình hiện tại.
2. Nâng BOM lên 2026.09.00 và ghim material3 1.5.0-rc01; chạy so ảnh, xem từng khác biệt, ghi lại ảnh chuẩn sau khi duyệt.
3. Tách `:core:domain` theo danh sách chuyển ở trên; các test đó chạy như test JVM thuần.
4. Thay `AppContainer` bằng module Koin, viết `AppModuleTest`; đổi tên `SplitVibeApp` và `SplitVibeTheme` ở đủ các chỗ đã liệt kê, kể cả manifest.
5. Chuyển 3 màn hình sang Navigation 3; kiểm vuốt ngược xem trước trên máy ảo API 36.
6. Cấu hình ký release bằng biến môi trường (không có thì ra bản chưa ký), xuất AAB, hai flavor `dev` (hậu tố `.dev`) và `prod`.
7. Viết CI cho pull request và `main`: build debug và release **chưa ký**, unit test, Android Lint, kiểm định dạng mã, so ảnh chụp, quét secret, kiểm wrapper của Gradle. Chỉ dùng sự kiện `pull_request`, ghim mọi action theo mã commit, bật kiểm chữ ký phụ thuộc và Dependabot.
8. Viết luồng chạy test trên thiết bị bằng Gradle Managed Devices (API 34) chạy hằng đêm cho 3 lớp test có sẵn.
9. Thêm `:baseline-profile`, sinh profile, viết hành trình đo khởi động và luồng chia hóa đơn; ghi số đo gốc vào `plans/reports/`.
10. Cập nhật README và viết `docs/architecture.md`.

## Todo

- [x] Bộ ảnh chuẩn 12 màn hình trước khi nâng thư viện
- [x] BOM 2026.09.00 và material3 1.5.0-rc01, khác biệt hình ảnh đã duyệt
- [x] Module `:core:domain` gồm cả `Contact`, `BillAnalyzer.kt` và parser công khai
- [x] Koin thay `AppContainer`, có `AppModuleTest`
- [x] Đổi tên `SplitVibeApp` (kể cả manifest) và `SplitVibeTheme`
- [x] Navigation 3 cho 3 màn hình, có test tự động cho chuyển màn hình, khôi phục và vuốt ngược xem trước
- [ ] Kiểm tay vuốt ngược xem trước trên máy ảo API 36, có quay màn hình (cần máy có tăng tốc phần cứng)
- [x] Ký bằng biến môi trường, AAB, flavor `dev` và `prod`
- [x] CI không secret cho PR; action ghim theo commit; kiểm checksum phụ thuộc; Dependabot
- [x] Test trên thiết bị chạy hằng đêm trong CI
- [x] Module `:baseline-profile`, profile đã ghi và nằm trong bản release
- [ ] Số đo gốc trên máy thật: khởi động nguội và thời gian khung hình (cần máy tham chiếu của chủ sản phẩm)
- [x] README và `docs/architecture.md`

## Success Criteria

- `./gradlew :app:assembleProdRelease :app:testProdDebugUnitTest :core:domain:test` chạy xanh khi không có biến môi trường ký nào; tổng số test không ít hơn 80 và `SplitFlowTest` không đổi phần khẳng định.
- App khởi động được trên máy ảo sau khi đổi tên lớp `Application` (kiểm bằng test trên thiết bị, không chỉ bằng build).
- `AppModuleTest` xác nhận mọi phụ thuộc trong module Koin đều giải được.
- Báo cáo phụ thuộc của Gradle cho thấy material3 1.5.0-rc01 và Compose UI 1.12.x.
- So ảnh chụp trong CI xanh; một thay đổi giao diện cố ý làm CI đỏ (thử một lần để chứng minh cổng hoạt động).
- Ba lớp test trên thiết bị (`KeystoreCipherTest`, `MlKitBillTextRecognizerTest`, `ClaudeBillAnalyzerOnDeviceTest`) qua trên thiết bị do Gradle quản lý trong CI.
- Luồng CI của pull request không khai báo secret nào và không dùng `pull_request_target`; mọi action ghim theo mã commit.
- `app/build.gradle.kts` không còn dòng `signingConfigs.getByName("debug")` trong khối release.
- `:core:domain` là module `kotlin("jvm")`, nên không thể nhập lớp Android.
- Vuốt ngược xem trước chạy trên cả 3 màn hình ở máy ảo API 36 (kiểm tay, có quay màn hình lưu vào `plans/reports/`).
- Có số đo gốc: thời gian khởi động nguội và thời gian khung hình của luồng chia hóa đơn.

## Kết quả thực hiện

<!-- Updated: thực hiện ngày 14/11/2026 trên nhánh feat/phase-01-foundation -->
Mười một trên mười ba việc đã xong và có bằng chứng. Hai việc còn lại cần thứ mà môi trường làm việc không có: một máy ảo API 36 có tăng tốc phần cứng để kiểm tay, và một điện thoại thật để đo.

| Tiêu chí | Kết quả | Bằng chứng |
|---|---|---|
| Build và test không cần biến ký | Đạt | 88 test: 65 ở `:core:domain`, 23 ở `:app` (1 bỏ qua vì cần API key thật). `SplitFlowTest` không đổi dòng nào |
| App khởi động trên thiết bị sau khi đổi tên lớp `Application` | Đạt | `AppLaunchTest` qua trên máy ảo Android 14, cả ở máy làm việc lẫn trong GitHub Actions |
| `AppModuleTest` giải được mọi phụ thuộc | Đạt | Chạy trong CI ở mỗi lần kiểm |
| material3 1.5.0-rc01 và Compose UI 1.12.x | Đạt | Báo cáo phụ thuộc của Gradle: material3 1.5.0-rc01, Compose UI 1.12.1. 13 ảnh chuẩn giống hệt từng điểm ảnh trước và sau khi nâng |
| So ảnh chụp trong CI xanh, thay đổi cố ý làm CI đỏ | Đạt | Lần chạy `43564589997` xanh, gồm cả baseline profile. Nhánh thử `check/screenshot-gate` đổi một khoảng đệm ở màn hình chính: lần chạy `43563864550` đỏ ở đúng hai bài test có màn hình đó, và gói `reports` có ảnh so sánh |
| Ba lớp test thiết bị qua trên thiết bị do Gradle quản lý trong CI | Đạt | Lần chạy `43563330640`: 7 test trên `pixel6Api34` (ba lớp cũ và `AppLaunchTest`) |
| CI của PR không có secret, không `pull_request_target`, action ghim theo commit | Đạt | `.github/workflows/ci.yml`; reviewer độc lập đã đối chiếu từng mã commit với tag |
| Không còn ký release bằng khóa debug | Đạt | Không biến: bản chưa ký. Đủ bốn biến: bản ký đúng chứng thư. Thiếu biến: build dừng và nêu tên biến thiếu |
| `:core:domain` là module Kotlin thuần | Đạt | `core/domain/build.gradle.kts` chỉ áp plugin `kotlin.jvm` |
| Vuốt ngược xem trước trên 3 màn hình ở máy ảo API 36, kiểm tay có quay màn hình | Chưa đạt | Thay bằng `BackNavigationTest`: bắt đầu vuốt, dừng giữa chừng, hủy, rồi vuốt hết. Ảnh chuẩn `14-back-swipe-preview.png` cho thấy màn hình trước hiện ra phía sau. Chưa có bản quay trên API 36 |
| Có số đo gốc | Chưa đạt | `StartupBenchmark` và `SplitFlowBenchmark` đã viết và biên dịch được, chưa chạy trên máy thật |

Khác với bản plan, và lý do:

- **`compileSdk` là 37.** Compose 1.12 và material3 1.5 chỉ biên dịch được với SDK của Android 17. `targetSdk` vẫn là 36 nên hành vi của app không đổi.
- **Kiểm checksum thay cho kiểm chữ ký.** `gradle/verification-metadata.xml` ghi SHA-256 của 839 thành phần; đã thử sửa sai một giá trị và build dừng. Kiểm chữ ký PGP cần tải khóa từ máy chủ khóa và nhiều thư viện không ký, nên chưa làm. Tệp phải được sinh từ bộ nhớ đệm Gradle trống, nếu không sẽ thiếu mục và máy CI từ chối (đã gặp và đã sửa). Chủ sản phẩm quyết định có cần thêm kiểm chữ ký không.
- **Kiểm định dạng mã bằng ktlint**, chạy như một tác vụ Gradle thường. Mã hiện có được định dạng lại một lần, không đổi hành vi.
- **Luồng thiết bị chạy cả khi có thay đổi liên quan**, không chỉ hằng đêm: test thiết bị, module profile, phiên bản thư viện hoặc checksum đổi là chạy.
- **Bấm đúp nút quay lại ở Settings giờ chỉ đóng một màn hình.** Trước đây đóng hai, có thể để lại màn hình trắng. Màn hình giờ tự đóng theo tên.
- **App bật `enableOnBackInvokedCallback`**, để hiệu ứng xem trước khi vuốt ngược có cả trên Android 13 đến 15.
- **Hai test mới ngoài danh sách tệp:** `AppStartupTest` và `BackNavigationTest` (máy build), `AppLaunchTest` (thiết bị).

Việc phát hiện thêm trong lúc làm:

- Một test về timeout chỉ lỗi trên máy của GitHub vì ở đó `localhost` còn trỏ tới `::1`. Test giờ dùng thẳng `127.0.0.1`.
- Bản release đã rút gọn vẫn giữ `INSTANCE` và `serializer()` của ba khóa điều hướng (xem bằng apkanalyzer), là thứ việc khôi phục màn hình cần. Chạy thử bản rút gọn trên thiết bị thì chưa.
- Android Lint không báo lỗi, có 19 cảnh báo. Một cảnh báo đáng làm ở Phase 9: khai báo `dataExtractionRules` cho sao lưu từ Android 12.
- [Bản review độc lập](../reports/code-reviewer-261114-0549-phase-01-foundation-review.md) và cách xử lý từng phát hiện.

Việc của chủ sản phẩm:

1. Bật bảo vệ nhánh `main`: bắt buộc hai kiểm tra "Style, tests, lint and packages" và "Secret scan".
2. Chạy `./gradlew :baseline-profile:connectedProdBenchmarkReleaseAndroidTest` trên máy tham chiếu để có số đo gốc.
3. Kiểm vuốt ngược trên một máy Android 16 và quay lại màn hình.
4. Xóa nhánh thử `check/screenshot-gate` trên GitHub. Phiên làm việc không có quyền xóa nhánh.
5. Quyết định có cần kiểm chữ ký PGP ngoài checksum không.

## Risks

| Rủi ro | Dấu hiệu nhận biết | Phản ứng đã định |
|---|---|---|
| Navigation 3 thiếu thứ cần | Không đạt một trong ba việc sau một ngày thử | Dừng, báo chủ sản phẩm kèm bằng chứng; không tự đổi quyết định |
| Lỗi nối dây Koin chỉ lộ lúc chạy | App sập khi mở một màn hình | `AppModuleTest` trong CI; test trên thiết bị mở từng màn hình |
| material3 1.5.0 đổi API khi ra bản stable | Lỗi biên dịch khi nâng từ rc01 | Sửa trong `:core:designsystem`; không phát hành production khi material3 chưa stable |
| Plugin Gradle của Roborazzi chưa hợp AGP 9 `[UNVERIFIED]` | Tác vụ `verifyRoborazziDebug` lỗi | Dùng thư viện với thuộc tính hệ thống như đã chạy ngày 14/11 |
| Số đo khung hình trên máy ảo không đáng tin | Số đo dao động lớn giữa các lần chạy | Chỉ dùng máy thật làm chuẩn; CI chỉ sinh baseline profile |

## Security

<!-- Red Team: CI trên repo công khai để secret trong tầm với của PR — 2026-11-14 -->
- Pull request từ bên ngoài chạy mã không tin cậy: luồng PR không có secret, không ký, không triển khai.
- Khóa upload chỉ được dùng trong luồng phát hành của Phase 9, trong môi trường GitHub có người duyệt bắt buộc.
- Không có tệp khóa nào trong repo (`.gitignore` đã chặn `*.jks` và `*.keystore`); CI quét secret trên mọi PR.
- Flavor `dev` dùng hậu tố `.dev` để không lẫn dữ liệu với bản thật.

## Next

Phase 2 tạo `:core:designsystem` trên nền thư viện đã nâng. Phase 3 dùng flavor `dev` và `prod` cho hai project Firebase và thêm các module Koin cho Firebase.
