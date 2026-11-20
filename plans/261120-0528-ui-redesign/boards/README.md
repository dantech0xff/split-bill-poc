# Bảng thiết kế đã duyệt

Thư mục này giữ bản cố định của 37 bảng thiết kế mà plan bám theo: 32 bảng giao diện sáng và 5 bảng giao diện tối.

- Nguồn: canvas "Split Vibe: thiết kế lại giao diện" của chủ sản phẩm trên claude.ai, phiên bản 19, lưu ngày 20/11/2026 (UTC).
- Chủ sản phẩm duyệt bản thiết kế ngày 20/11/2026 và cho lưu vào repo cùng ngày.
- Dữ liệu trên bảng là dữ liệu mẫu: tên người, số tiền, địa chỉ `proxy.example` và hộp thư `example.com` đều không có thật.

## Cách đọc

- Mỗi tệp là một trang HTML rộng 390 px. Kiểu dáng viết ngay trên phần tử, nên màu, cỡ chữ, khoảng cách và bo góc đọc được trực tiếp. 1 px trên bảng ứng với 1 dp trong app.
- `support.js` là bộ chạy của canvas và không nằm ở đây. Mở tệp bằng trình duyệt vẫn thấy bố cục, nhưng các nút đổi trạng thái của canvas không chạy.
- Ảnh hóa đơn mẫu trỏ tới kho ảnh của canvas (`/_blob/…`), nên không hiện khi mở tệp ngoài canvas.
- Hai phông chữ được nạp từ Google Fonts khi mở bảng; app thì đóng gói tệp phông theo giai đoạn 1 của plan.

## Quy tắc

- Người thực hiện và người review so với các tệp ở đây, không so với canvas đang sống.
- Sửa trên canvas sau ngày lưu là một yêu cầu thay đổi. Khi chủ sản phẩm duyệt thay đổi đó, bảng ở đây, `SHA256SUMS` và phần plan bị ảnh hưởng được cập nhật trong cùng một commit.
- Kiểm tra bản lưu: chạy `sha256sum -c SHA256SUMS` trong thư mục này.
- Khi bảng và app khác nhau về chữ, quy tắc ở mục "Nguồn thiết kế" của [plan](../plan.md) quyết định.

## Danh sách bảng

| Tệp | Màn hình | Kích thước (px) | Giao diện |
|---|---|---|---|
| [`Main.dc.html`](./Main.dc.html) | Trang chủ | 390 × 844 | sáng |
| [`NewSplit.dc.html`](./NewSplit.dc.html) | Hóa đơn mới | 390 × 844 | sáng |
| [`Result.dc.html`](./Result.dc.html) | Kết quả chia | 390 × 2200 | sáng |
| [`AskForMoney.dc.html`](./AskForMoney.dc.html) | Đòi tiền | 390 × 1160 | sáng |
| [`Group.dc.html`](./Group.dc.html) | Một nhóm | 390 × 2040 | sáng |
| [`DarkHome.dc.html`](./DarkHome.dc.html) | Tối: trang chủ | 390 × 844 | tối |
| [`DarkNewSplit.dc.html`](./DarkNewSplit.dc.html) | Tối: hóa đơn mới | 390 × 844 | tối |
| [`DarkResult.dc.html`](./DarkResult.dc.html) | Tối: kết quả chia | 390 × 2200 | tối |
| [`DarkAskForMoney.dc.html`](./DarkAskForMoney.dc.html) | Tối: đòi tiền | 390 × 1160 | tối |
| [`DarkGroup.dc.html`](./DarkGroup.dc.html) | Tối: một nhóm | 390 × 2040 | tối |
| [`HomeEmpty.dc.html`](./HomeEmpty.dc.html) | Trang chủ lần đầu | 390 × 844 | sáng |
| [`NewSplitEmpty.dc.html`](./NewSplitEmpty.dc.html) | Hóa đơn mới, chưa có ảnh | 390 × 844 | sáng |
| [`Consent.dc.html`](./Consent.dc.html) | Xin đồng ý dùng AI | 390 × 844 | sáng |
| [`Working.dc.html`](./Working.dc.html) | Đang đọc hóa đơn | 390 × 844 | sáng |
| [`ResultProposal.dc.html`](./ResultProposal.dc.html) | Đề xuất của AI | 390 × 1330 | sáng |
| [`SplitByPercent.dc.html`](./SplitByPercent.dc.html) | Chia theo phần trăm | 390 × 844 | sáng |
| [`Groups.dc.html`](./Groups.dc.html) | Các nhóm | 390 × 844 | sáng |
| [`JoinGroup.dc.html`](./JoinGroup.dc.html) | Tham gia nhóm | 390 × 844 | sáng |
| [`Requests.dc.html`](./Requests.dc.html) | Yêu cầu trả tiền | 390 × 960 | sáng |
| [`RequestLink.dc.html`](./RequestLink.dc.html) | Một yêu cầu trả tiền | 390 × 1060 | sáng |
| [`PaymentMethods.dc.html`](./PaymentMethods.dc.html) | Cách nhận tiền | 390 × 844 | sáng |
| [`BillLink.dc.html`](./BillLink.dc.html) | Liên kết hóa đơn | 390 × 1020 | sáng |
| [`BillLinkClosed.dc.html`](./BillLinkClosed.dc.html) | Liên kết hóa đơn, đã khóa chọn | 390 × 1140 | sáng |
| [`SheetAddPerson.dc.html`](./SheetAddPerson.dc.html) | Bảng: thêm người | 390 × 844 | sáng |
| [`SheetItemSplit.dc.html`](./SheetItemSplit.dc.html) | Bảng: chia một món | 390 × 844 | sáng |
| [`SheetWhoPaid.dc.html`](./SheetWhoPaid.dc.html) | Bảng: ai đã trả | 390 × 844 | sáng |
| [`EditBill.dc.html`](./EditBill.dc.html) | Sửa hóa đơn | 390 × 2010 | sáng |
| [`SheetRepeat.dc.html`](./SheetRepeat.dc.html) | Bảng: lặp lại hóa đơn | 390 × 844 | sáng |
| [`SheetExport.dc.html`](./SheetExport.dc.html) | Bảng: xuất file | 390 × 844 | sáng |
| [`DeleteAccount.dc.html`](./DeleteAccount.dc.html) | Hộp thoại: xóa tài khoản | 390 × 844 | sáng |
| [`Bills.dc.html`](./Bills.dc.html) | Hóa đơn | 390 × 900 | sáng |
| [`Recurring.dc.html`](./Recurring.dc.html) | Hóa đơn định kỳ | 390 × 844 | sáng |
| [`Insights.dc.html`](./Insights.dc.html) | Thống kê | 390 × 2440 | sáng |
| [`Settings.dc.html`](./Settings.dc.html) | Cài đặt | 390 × 1420 | sáng |
| [`SettingsStates.dc.html`](./SettingsStates.dc.html) | Cài đặt: các trạng thái tài khoản | 390 × 1050 | sáng |
| [`Pro.dc.html`](./Pro.dc.html) | Mua Pro | 390 × 1050 | sáng |
| [`ProMember.dc.html`](./ProMember.dc.html) | Đã có Pro | 390 × 890 | sáng |
