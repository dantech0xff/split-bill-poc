---
title: "Split Vibe: đổi tên và lập plan production"
date: 2026-11-14
summary: "Đổi package thành com.dantech.splitvibe và lập plan production 9 giai đoạn, đã qua phản biện và các quyết định của chủ sản phẩm."
---

# Split Vibe: đổi tên và lập plan production

## What happened

- Package đổi từ `com.example.splitbill` sang `com.dantech.splitwise` theo yêu cầu ban đầu, rồi sang `com.dantech.splitvibe` sau khi chủ sản phẩm chọn tên Split Vibe để tránh trùng tên một app chia tiền có sẵn. Commit `d66096f`. Build sạch qua 80 unit test (1 test gọi API thật bị bỏ qua vì chưa có key); APK mang nhãn "Split Vibe".
- Dựng được ảnh của 12 màn hình và một mẫu bốn kiểu bóng đổ bằng Roborazzi 1.76.0 trên Robolectric ở chế độ đồ họa native. Cách cũ dùng `captureToImage()` đã thất bại ở phiên trước vì chờ một lượt vẽ không bao giờ tới.
- Nghiên cứu cho thấy mọi Compose BOM tới 2026.09.00 vẫn kèm material3 1.4.0, bản không có API Expressive công khai. Muốn dùng Expressive phải ghim `material3:1.5.0-rc01`.
- Nguồn tỷ giá Frankfurter: API v1 trả 30 tiền tệ và không có VND, v2 trả 200 mã và có VND (gọi thử trực tiếp ngày 14/11).
- Plan nằm tại `plans/261114-0220-split-vibe-production/` gồm 9 giai đoạn và 93 đầu việc.
- Bốn người phản biện đưa ra 40 phát hiện thô, gộp còn 15 (5 nghiêm trọng, 8 cao, 2 trung bình), chủ sản phẩm chọn áp dụng cả 15. Các lỗi nặng nhất của bản nháp:
  - bản release không phân tích được hóa đơn sau khi bỏ key, vì cổng chặn `hasApiKey` ở 4 nơi không nằm trong danh sách tệp;
  - hạn mức AI gắn với tài khoản ẩn danh nên lách được bằng cách xóa dữ liệu app;
  - danh sách tệp chuyển sang `:core:domain` thiếu `Contact` và `BillAnalyzer.kt` nên không biên dịch được;
  - không có định danh người thống nhất giữa hóa đơn và nhóm;
  - hai máy sửa offline cùng một hóa đơn có thể để lại phần chia lệch tổng.
- Một agent nghiên cứu không ghi được tệp báo cáo; nội dung được gửi về trong tin nhắn và người điều phối lưu lại tại `plans/reports/`.
- Tiến trình làm việc bị khởi động lại giữa phiên, nên danh sách 15 phát hiện đã gộp phải dựng lại từ bốn báo cáo gốc.

## Decision

Chủ sản phẩm chốt:
- App độc lập, không giữ tiền; backend Firebase với Firestore ở `nam5`; ra toàn cầu.
- v1.0 gồm đủ 12 tính năng gợi ý và gói Pro. Pro chỉ khác ở số lượt AI: 3 lượt dùng thử khi chưa đăng nhập, 10 lượt mỗi tháng khi đã đăng nhập, 300 lượt mỗi tháng với Pro.
- v1.0 chỉ có tiếng Anh và chỉ đọc hóa đơn chữ Latin.
- Koin cho DI và Navigation 3 cho điều hướng.
- Repo giữ công khai.

Người lập plan đặt mặc định, chủ sản phẩm đổi được: chọn model theo số liệu của bộ đánh giá; dùng RevenueCat; xóa hẳn `ClaudeBillAnalyzer` và thư viện Anthropic Java khỏi app thay vì giữ trong source set `debug`.

## Next steps

- Chủ sản phẩm: chọn giấy phép mã nguồn; tra cứu nhãn hiệu "Split Vibe" trước khi tạo app trên Play Console; chọn máy tham chiếu đo hiệu năng.
- Nhánh `feat/production-kickoff` chưa gộp vào `main`.
- Khi chủ sản phẩm chọn triển khai: bắt đầu từ `plans/261114-0220-split-vibe-production/phase-01-start.md`.

> Historical work record — not durable authority. Prefer docs/specs/ADRs for current decisions.
