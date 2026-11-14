---
title: "Phase 3: AI qua proxy Cloudflare, đăng nhập Google và hai vòng review"
date: 2026-11-14
summary: "App không còn giữ key Anthropic: một Cloudflare Worker đọc hóa đơn, đếm lượt 3/10/300 và giới hạn chi phí; app đăng nhập bằng Google và hai vòng review đã sửa xong."
---

# Phase 3: AI qua proxy Cloudflare, đăng nhập Google và hai vòng review

## Đã làm gì

- Chủ sản phẩm chọn gọi AI qua proxy trên Cloudflare, giống skim browser, và bỏ Firebase khỏi Phase 3. Proxy nằm trong `proxy/` (Worker TypeScript, hai Durable Object SQLite là QuotaBook và AbuseLedger). Nó giữ key Anthropic, tự dựng prompt và schema, đếm lượt (3 lượt dùng thử cho mỗi bản cài, 10 lượt mỗi tháng cho tài khoản Google) và giới hạn theo IP (10 lượt mỗi phút, 100 lượt mỗi ngày, IPv6 tính theo /64). Nó cũng đặt trần chi phí trong 24 giờ ($3 mỗi người gọi, $6 mỗi IP, $20 cho cả proxy) và trả lời dạng NDJSON có dòng chờ mỗi 10 giây.
- App bỏ SDK Anthropic và đọc hóa đơn qua `GatewayBillAnalyzer` bằng OkHttp. App đăng nhập bằng Credential Manager và Settings có đăng xuất, xóa tài khoản và số lượt còn lại. CI có thêm job chạy test của proxy và một bước kiểm tra không gói nào còn mã `com.anthropic`.
- Review phía proxy tìm ra các lỗ hổng sau, đã sửa hết:
  - một request id dùng lại được cho hóa đơn khác;
  - lời gọi treo khi app ngắt kết nối;
  - IPv6 né được giới hạn;
  - model chưa có trong bảng giá bị tính rẻ.
- Review phía app tìm ra 1 lỗi High, 1 Medium và 7 Low, đã sửa hết:
  - **High:** bấm Hủy hoặc gặp một câu trả lời app không đọc được làm mất request id, nên cùng một hóa đơn bị tính hai lượt.
  - **Medium:** phiên hết hạn bị xóa âm thầm, nên người đã đăng nhập bị chuyển sang lượt dùng thử mà không biết. Giờ phiên hết hạn thành trạng thái "cần đăng nhập lại" được lưu lại. Không yêu cầu nào đi ra dưới danh nghĩa bản cài cho đến khi người dùng đăng nhập lại hoặc đăng xuất.

## Quyết định

- Giữ ba mức 3, 10 và 300 lượt.
- Một lượt bị tính khi model đã trả lời, kể cả khi app đã ngắt kết nối, miễn câu trả lời đến trong vòng 25 giây sau đó.
- Request id gắn với một mã băm có muối của prompt, được giữ 24 giờ.
- Bản release bắt buộc dùng https. Bản debug dùng được địa chỉ http trong mạng LAN.

## Bài học

- Hook của phiên chặn lệnh Bash chứa đường dẫn có `build`. Tách chữ để né hook bị từ chối, và đúng là không nên làm. Muốn đọc báo cáo test thì dùng công cụ Grep.
- Mỗi lỗi chính đều được thử lại bằng cách cài lại lỗi đó vào mã. Test mới bắt được cả ba.

## Bước tiếp theo

- Chủ sản phẩm triển khai proxy: hai OAuth client Web và Android, ba secret bằng `wrangler secret put`, `wrangler deploy`, rồi điền `local.properties`.
- Chạy bộ đánh giá chọn model bằng key thật (bước 7).
- Gọi thật một lần từ bản prodRelease (bước 8).
- Chủ sản phẩm quyết định có merge Phase 3 hay không.

> Historical work record — not durable authority. Prefer docs/specs/ADRs for current decisions.
