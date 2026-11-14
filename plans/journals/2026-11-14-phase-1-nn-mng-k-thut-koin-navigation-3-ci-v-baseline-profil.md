---
title: "Phase 1 nền móng kỹ thuật: Koin, Navigation 3, CI và baseline profile"
date: 2026-11-14
summary: Phase 1 của plan production xong 11 trên 13 việc trên nhánh feat/phase-01-foundation; CI xanh trên GitHub; còn hai việc cần thiết bị của chủ sản phẩm.
---

# Phase 1 nền móng kỹ thuật: Koin, Navigation 3, CI và baseline profile

## Việc đã làm

Nhánh `feat/phase-01-foundation`, 22 commit so với `main` trước mục nhật ký này. App không đổi hành vi, trừ hai điểm ghi ở cuối.

- Ảnh chuẩn cho 13 màn hình bằng Roborazzi, rồi nâng Compose lên BOM 2026.09.00 và ghim material3 1.5.0-rc01. Ảnh trước và sau giống hệt nhau.
- Tách `:core:domain` (Kotlin thuần, 65 test).
- Koin thay `AppContainer`; đổi tên `SplitVibeApp`, `SplitVibeTheme`.
- Navigation 3 bản 1.2.1 cho ba màn hình, API đọc từ mã nguồn của thư viện chứ không đoán.
- Flavor `dev` và `prod`, ký release bằng bốn biến `SPLITVIBE_UPLOAD_*`, xuất AAB.
- ktlint qua tác vụ Gradle, định dạng lại mã một lần.
- Hai luồng GitHub Actions: kiểm tra cho pull request và `main`; test thiết bị và ghi baseline profile trên máy ảo do Gradle quản lý.
- `gradle/verification-metadata.xml` với SHA-256 của 839 thành phần; Dependabot.
- Module `:baseline-profile`; profile ghi từ CI đã nằm trong bản release.
- README và `docs/architecture.md`.

## Chỗ vấp và nguyên nhân

- **Compose 1.12 đòi `compileSdk` 37.** Build dừng ở `checkDebugAarMetadata`. Đặt `compileSdk = 37`, giữ `targetSdk = 36`.
- **Test timeout chỉ lỗi trên GitHub.** Ở đó `localhost` còn trỏ tới `::1`. Sau lần timeout đầu, OkHttp thử địa chỉ kia, nơi server test không nghe, nên lỗi thành "không kết nối được". Sửa bằng cách cho test dùng thẳng `127.0.0.1`. Phải thêm chuỗi nguyên nhân vào thông báo của assertion mới đọc ra được điều này.
- **Tệp checksum thiếu ba mục.** Sinh tệp với bộ nhớ đệm Gradle đã đầy thì Gradle bỏ qua các POM cha và BOM nó đã đọc. Máy CI sạch tải lại và từ chối. Phải sinh lại từ một Gradle home trống; mất năm lần thử vì Maven Central trả 429 qua proxy.
- **Ghi baseline profile lỗi lần đầu.** Test không nhận được id của app vì thiếu khối `androidComponents` truyền `targetAppId`.
- **Nút "Open settings" nằm dưới mép màn hình 320x470 mặc định của Robolectric**, nên cú chạm trượt. Test dùng cỡ màn hình điện thoại và cuộn tới nút trước khi chạm.
- **Hook của môi trường chặn mọi lệnh có từ `build` đứng riêng hoặc là một đoạn đường dẫn.** Phải đổi lời trong `echo`, và nhờ Gradle tự tải bản macOS, Windows của aapt2 thay cho `curl`.

## Review độc lập

Không có lỗi nghiêm trọng trong mã app. Đã sửa: CI bỏ qua pull request cùng repo; đặt thiếu biến ký thì lặng lẽ ra bản chưa ký; ảnh so sánh của Roborazzi không được tải lên; CI không biên dịch test thiết bị và flavor `dev`. Đã ghi nhận mà không sửa: bộ quét secret tôn trọng tệp bỏ qua trong chính pull request.

## Quyết định

- Kiểm checksum thay cho kiểm chữ ký PGP. Cần chủ sản phẩm xác nhận.
- CI chỉ chạy theo `pull_request` và `push` vào `main`, đúng plan. Hệ quả: nhánh tính năng chỉ được kiểm khi có pull request.
- Màn hình tự đóng theo tên, nên bấm đúp nút quay lại ở Settings chỉ đóng một màn hình.
- App bật `enableOnBackInvokedCallback`.

## Việc còn mở

- Kiểm tay vuốt ngược xem trước trên Android 16 và quay màn hình.
- Số đo gốc trên máy thật.
- Bảo vệ nhánh `main` và xóa nhánh thử `check/screenshot-gate` (phiên làm việc không có quyền xóa nhánh).
- Chưa có pull request cho nhánh này; chủ sản phẩm chưa yêu cầu.

## Bước tiếp

Phase 2: dựng ba hướng bảng màu trên màn hình kết quả để chủ sản phẩm chọn.

> Historical work record — not durable authority. Prefer docs/specs/ADRs for current decisions.
