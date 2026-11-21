# Bảng thiết kế web đã duyệt

Thư mục này giữ bản cố định của 12 bảng thiết kế cho các trang web mà người nhận liên kết nhìn thấy. Giai đoạn 9 của plan bám theo các bảng này: 10 bảng giao diện sáng và 2 bảng giao diện tối, 11 bảng ở bề rộng 390 px và 1 bảng ở 1280 px.

- Nguồn: trang "Trang web" của canvas "Split Vibe: thiết kế lại giao diện" của chủ sản phẩm trên claude.ai. Các tệp ở đây giống hệt từng byte với bản đang có trên canvas ngày 21/11/2026 (UTC).
- Chủ sản phẩm duyệt các bảng web ngày 21/11/2026 và cho bắt đầu giai đoạn 9 cùng ngày.
- Dữ liệu trên bảng là dữ liệu mẫu: tên người, số tiền, số tài khoản và địa chỉ `proxy.example` đều không có thật.

## Cách đọc

- Mỗi tệp là một trang HTML; kiểu dáng viết ngay trên phần tử, nên màu, cỡ chữ, khoảng cách và bo góc đọc được trực tiếp. 1 px trên bảng ứng với 1 px CSS trên trang thật.
- Bảng vẽ bằng kiểu dáng trên phần tử chỉ vì đó là cách vẽ. Trang thật cấm kiểu dáng trên phần tử, nên khi làm, mọi kiểu dáng thành lớp trong `web/src/app.css`, như giai đoạn 9 nói.
- `support.js` là bộ chạy của canvas và không nằm ở đây. Mở tệp bằng trình duyệt vẫn thấy bố cục, nhưng các nút đổi trạng thái của canvas không chạy.
- Hai phông chữ được nạp từ Google Fonts khi mở bảng; trang thật dùng tệp phông do chính Worker phục vụ, theo giai đoạn 9.

## Quy tắc

- Người thực hiện và người review so với các tệp ở đây, không so với canvas đang sống.
- Sửa trên canvas sau ngày lưu là một yêu cầu thay đổi. Khi chủ sản phẩm duyệt thay đổi đó, bảng ở đây, `SHA256SUMS` và giai đoạn 9 được cập nhật trong cùng một commit.
- Kiểm tra bản lưu: chạy `sha256sum -c SHA256SUMS` trong thư mục này.
- Khi bảng và trang thật khác nhau về chữ, chữ của trang thật giữ nguyên, vì giai đoạn 9 không đổi một chữ nào của trang.

## Danh sách bảng

| Tệp | Trang | Kích thước (px) | Giao diện |
|---|---|---|---|
| [`WebBill.dc.html`](./WebBill.dc.html) | Hóa đơn `/s/` khi đang mở cho chọn món | 390 × 1180 | sáng |
| [`WebBillClosing.dc.html`](./WebBillClosing.dc.html) | Hóa đơn `/s/` khi đã khóa chọn và đang chia | 390 × 1030 | sáng |
| [`WebBillSettled.dc.html`](./WebBillSettled.dc.html) | Hóa đơn `/s/` khi đã chia xong | 390 × 2260 | sáng |
| [`WebStates.dc.html`](./WebStates.dc.html) | Các trạng thái nhỏ của liên kết hóa đơn | 390 × 1750 | sáng |
| [`WebDarkBill.dc.html`](./WebDarkBill.dc.html) | Hóa đơn `/s/` khi đang mở | 390 × 1180 | tối |
| [`WebRequest.dc.html`](./WebRequest.dc.html) | Yêu cầu trả tiền `/r/` | 390 × 1380 | sáng |
| [`WebInvite.dc.html`](./WebInvite.dc.html) | Lời mời vào nhóm `/g/` | 390 × 830 | sáng |
| [`WebDeleteAccount.dc.html`](./WebDeleteAccount.dc.html) | Xóa tài khoản `/delete-account` | 390 × 2680 | sáng |
| [`WebDeleteSteps.dc.html`](./WebDeleteSteps.dc.html) | Các bước xóa tài khoản | 390 × 2000 | sáng |
| [`WebLegal.dc.html`](./WebLegal.dc.html) | Chính sách quyền riêng tư, mẫu cho `/privacy` và `/terms` | 390 × 1440 | sáng |
| [`WebDarkRequest.dc.html`](./WebDarkRequest.dc.html) | Yêu cầu trả tiền `/r/` | 390 × 1380 | tối |
| [`WebWide.dc.html`](./WebWide.dc.html) | Yêu cầu trả tiền `/r/` trên màn hình rộng | 1280 × 900 | sáng |
