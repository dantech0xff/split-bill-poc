---
title: "Giao diện mới của Split Vibe xong mười giai đoạn, chờ merge"
date: 2026-11-21
summary: "App và trang web đã mang bản thiết kế lại đã duyệt; mỗi giai đoạn qua cổng kiểm thử, review độc lập và máy ảo; hồ sơ khởi động ghi từ mã app cuối; Worker và trang web mới lên cùng bản phát hành đầu tiên sau khi merge."
---

# Giao diện mới của Split Vibe xong mười giai đoạn, chờ merge

## Chuyện gì đã xảy ra

- Mười giai đoạn của `plans/261120-0528-ui-redesign/` được làm xong trên nhánh `feat/ui-redesign`: bảng màu và phông, thành phần dùng chung, một ngăn xếp điều hướng với thanh dưới, tiền và ngày theo vùng của máy, từng nhóm màn hình, 157 ảnh tham chiếu ghi lại một lần, sáu trang web theo 12 bảng web, rồi tài liệu và ảnh cửa hàng.
- Mỗi giai đoạn chạy cổng kiểm thử tại chỗ, một lượt review độc lập, một lượt sửa, và hai lượt máy ảo trên GitHub (Android 14 với kiểm tra trợ năng, Android 8.0, 11 và 16). Lượt review cuối trên toàn nhánh bắt được một chỗ vẫn hiện tên Claude khi câu trả lời của AI không đọc được, đã sửa.
- Soát ảnh ở giai đoạn 8 tìm ra hai lỗi thật: máy đặt tiếng Việt viết "1 bills", và giao diện tối lộ cửa sổ sáng giữa hai màn. Bản sửa đầu của lỗi số nhiều dựng tài nguyên tiếng Anh trên chính bộ quản lý tài nguyên của máy, làm chữ của Android và thư viện chuyển sang tiếng Anh; review tiếp theo bắt được, và bản sửa dùng `createConfigurationContext`.
- Máy làm việc không có máy ảo, nên phông trên Android 8.0 được xem qua ảnh do lượt chạy trên GitHub chụp, và bài gõ khi bàn phím mở được chuyển sang lượt Android 8.0, nơi máy ảo có bàn phím.
- Hồ sơ khởi động được ghi lại từ `f0d0786`, commit cuối của mã app, và commit ở `60a0fba`; mọi lệnh của `ci.yml` qua tại chỗ trên commit đó.

## Quyết định

- Dạng số nhiều theo luật tiếng Anh trên mọi máy, vì chữ của app là tiếng Anh; số tiền và ngày trong câu vẫn theo vùng của máy.
- Ngày của hóa đơn chỉ được dùng khi nằm từ năm 2000 tới hôm nay; "All time" của Insights vẽ theo năm.
- Trang web dùng ba tệp phông do Worker phục vụ (103 KB, dưới trần 120 KB), chính sách bảo mật nội dung chỉ thêm `font-src 'self'`.
- Token màu chốt từ cuối giai đoạn 8; trang web dùng màu đỏ báo lỗi của token thay cho màu của bảng.

## Bước tiếp

- Chờ chủ sản phẩm nói "ok merge"; không mở pull request khi chưa được yêu cầu. Sau khi merge, đọc lượt `ci.yml` trên `main`.
- Bản phát hành đầu tiên sau khi merge cũng triển khai Worker, nên các trang web mới lên cùng lúc, kể cả khi bản đó được cắt vì lý do khác.
- Còn cần máy thật: chuyển động, TalkBack và VoiceOver, quét mã QR bằng app ngân hàng; chủ sản phẩm duyệt bảy ảnh cửa hàng.

> Historical work record — not durable authority. Prefer docs/specs/ADRs for current decisions.
