---
title: "Phase 7: Kiểm thử, ảnh tham chiếu và hồ sơ khởi động"
status: pending
phase: 7
priority: P1
effort: "2d"
dependencies: [4, 5, 6]
---

# Phase 7: Kiểm thử, ảnh tham chiếu và hồ sơ khởi động

## Overview

Toàn bộ giao diện mới được kiểm một lượt cuối: ảnh tham chiếu ghi lại một lần, giao diện tối và chữ cỡ lớn được soát trên từng màn, kiểm thử trên máy ảo chạy ở bốn mức API, và hồ sơ khởi động được ghi lại.

## Context

- Mọi thứ kiểm tra giao diện và những gì sẽ đổi: [rà kiểm thử, CI, web và tài liệu](./reports/scout-tests-ci-web-docs.md), mục 1 đến 4.
- Bảng tham chiếu trên canvas: cả 37 bảng, để so với ảnh thật.

## Key Insights

- 97 ảnh tham chiếu nằm trong `app/src/test/screenshots/`; mọi ảnh đều đổi. Bốn ảnh không còn bài kiểm thử nào ghi (`07-pick-people`, `08-item-sharers`, mỗi cái có bản tối), và ảnh `09-sent-to-claude` mất theo tính năng.
- Giao diện tối không có bảng vẽ riêng cho đa số màn: nó đến từ token. Ảnh tối của từng màn là nơi duy nhất thấy lỗi tương phản do bố cục.
- Chỉ hai lớp kiểm thử trên máy ảo bật kiểm tra trợ năng, và chỉ chạy ở API 34. Ba mức API còn lại chỉ chạy các hành trình, không chặn.
- Hồ sơ khởi động đã commit mô tả mã cũ. Bản phát hành mang theo tệp đã commit, nên phải ghi lại trước cổng phát hành.
- Ứng xử của Roborazzi 1.76 khi thiếu ảnh tham chiếu ở chế độ so chưa được kiểm; chế độ ghi thì ghi mọi ảnh.

## Requirements

- Mỗi màn hình của app có ảnh tham chiếu sáng; các màn đang có bản tối tiếp tục có bản tối; bốn màn chính có ảnh chữ cỡ 200%; năm màn có ảnh ngôn ngữ giả `en-XA`; trang mẫu thành phần có ảnh sáng và tối.
- Các trạng thái mới có ảnh: trang chủ lần đầu, trang chủ khi chưa đăng nhập, màn hóa đơn với thẻ "Recurring", màn giấy phép phông, hộp thoại xóa tài khoản.
- Không còn ảnh nào trong thư mục mà không bài kiểm thử nào ghi.
- Không chữ nào bị cắt ở cỡ 200% trên trang chủ, màn thêm hóa đơn, màn kết quả và màn đòi tiền.
- Mọi cặp chữ và nền thấy trên ảnh tối đạt 4,5:1.
- Kiểm thử trên máy ảo qua ở API 34 với kiểm tra trợ năng; các hành trình qua ở API 26, 30 và 36.
- Hồ sơ khởi động được ghi lại từ mã mới và commit.
- Kích thước gói tăng thêm được đo và ghi lại.

## Files to Create / Modify

- Modify: `app/src/test/screenshots/*.png` (ghi lại; xóa `07-pick-people*.png`, `08-item-sharers*.png`, `09-sent-to-claude*.png`).
- Modify: các lớp `…GalleryTest.kt` để thêm ảnh của trạng thái mới.
- Modify: `app/src/androidTest/java/com/dantech/splitvibe/AppLaunchTest.kt` để kiểm tra trợ năng đi qua cả bốn tab.
- Modify: `app/src/prodRelease/generated/baselineProfiles/startup-prof.txt`, `baseline-prof.txt`.
- Modify: `baseline-profile/src/main/java/com/dantech/splitvibe/baselineprofile/Journeys.kt` nếu hành trình còn bám nhãn cũ.

## Implementation Steps

1. Chạy toàn bộ kiểm thử đơn vị ở chế độ ghi: `./gradlew :app:testProdDebugUnitTest -Pscreenshots=record`. Xóa sáu ảnh thừa. Chạy lại ở chế độ so để chắc mọi ảnh ổn định giữa hai lần chạy.
2. Thêm ảnh cho các trạng thái mới vào lớp ảnh phù hợp rồi ghi.
3. Ghép ảnh thành các bảng xem nhanh theo nhóm màn, sáng và tối, gửi chủ sản phẩm so với canvas. Sửa mọi lệch mà chủ sản phẩm chỉ ra, rồi ghi lại ảnh của màn đó.
4. Soát từng ảnh tối: chữ trên thẻ, nhãn, thanh nổi, biểu đồ. Lỗi tương phản được sửa trong token hoặc thành phần, không sửa trong màn hình.
5. Soát bốn ảnh chữ cỡ 200% và năm ảnh `en-XA`.
6. Chạy `./gradlew ktlintCheck :app:lintProdDebug :core:designsystem:lintDebug` và kiểm tra gói phát hành như CI.
7. Đẩy nhánh và chạy `device-tests.yml` với đầu vào `api-levels=true`; đọc kết quả của cả bốn mức API, vì ma trận ba mức không chặn kết luận của lượt chạy.
8. Lấy hồ sơ khởi động từ việc `profile` của `device-tests.yml`, commit hai tệp. Đo cỡ gói `prodRelease` trước và sau, ghi số chênh.
9. Thử tay trên máy ảo: hiệu ứng nở từ nút giữa, vuốt lùi có xem trước giữa các tab, bảng trượt kéo theo tay, và app chạy được khi tắt chuyển động.

## Todo

- [ ] Ảnh tham chiếu ghi lại, sáu ảnh thừa bị xóa, chế độ so qua
- [ ] Ảnh của các trạng thái mới
- [ ] Chủ sản phẩm duyệt các bảng ảnh
- [ ] Giao diện tối, chữ cỡ 200% và `en-XA` đã soát
- [ ] ktlint, lint, kiểm tra gói qua
- [ ] Kiểm thử trên máy ảo ở API 26, 30, 34, 36
- [ ] Hồ sơ khởi động ghi lại, cỡ gói được đo
- [ ] Thử tay các chuyển động

## Success Criteria

- `./gradlew :core:domain:test :core:designsystem:testDebugUnitTest :app:testProdDebugUnitTest` qua ở chế độ so ảnh.
- Lượt `device-tests.yml` trên nhánh có cả bốn mức API xanh.
- Chủ sản phẩm xác nhận các bảng ảnh khớp với canvas.
- Báo cáo của giai đoạn nêu số chênh cỡ gói và ngày ghi hồ sơ khởi động.

## Risk Assessment

- Ảnh không ổn định giữa hai lần chạy vì phông hoặc chuyển động: dấu hiệu là chế độ so hỏng ngay sau khi ghi. Ứng phó: dừng đồng hồ như `ScreenGalleryTest` đang làm cho màn đang đọc, hoặc tắt chuyển động của thành phần gây lệch trong bài chụp.
- Máy ảo API 26 hoặc 30 vẽ phông hoặc hình khối khác: hành trình vẫn qua nhưng giao diện lệch. Ứng phó: chụp màn hình trên hai mức API đó trong bước 9 và so bằng mắt.
- Việc ghi hồ sơ khởi động chỉ chạy theo lịch đêm hoặc khi gọi tay: gọi tay ở bước 8.
- Ngân sách khung hình trên máy tham chiếu là việc của chủ sản phẩm theo `docs/release-checklist.md`; giai đoạn này không đo được.

## Rollback

Ảnh tham chiếu và hồ sơ khởi động là tệp sinh ra; hoàn lại commit là đủ.
