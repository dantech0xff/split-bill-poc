---
title: "Giao diện mới lên main, biểu tượng mới và số tài khoản mẫu không có thật"
date: 2026-11-21
summary: Bản thiết kế lại và nhánh biểu tượng được fast-forward vào main với CI xanh; biểu tượng B do bài kiểm thử vẽ ra cả ảnh cho Google Play; các số tài khoản ngân hàng Việt Nam mẫu trong ảnh và kiểm thử của app thành toàn số 0; màn nhận phần thôi báo đổi cách nhận tiền khi danh sách chưa tải.
---

# Giao diện mới lên main, biểu tượng mới và số tài khoản mẫu không có thật

## Chuyện gì đã xảy ra

- Chủ sản phẩm cho merge: `main` được fast-forward lên `47bd154`, không pull request. Trên `main`, CI qua cả hai việc, bài trên máy ảo Android 14 và việc ghi hồ sơ khởi động qua, Security và Dependency checksums qua. Dependency graph không qua vì repo chưa bật dependency graph, đúng như trước khi merge.
- Biểu tượng mới: ba phương án được vẽ từ dấu tròn tách đôi của trang chủ. Một phương án nghiêng đường cắt bị loại trước khi gửi, vì vòng tròn có gạch chéo đọc như biển "cấm". Chủ sản phẩm chọn B, dấu lớn hơn với hai nửa tách rõ.
- B được viết thành biểu tượng thích ứng: nền tím, lớp hình là hai nửa đĩa trên lưới 108, lớp đơn sắc dùng lại lớp hình. Biểu tượng nhỏ của thông báo là cùng dấu một màu. Màn chờ của Android 12 trở lên tự dùng biểu tượng này.
- `LauncherIconTest` so màu với bảng màu của hệ thiết kế, kiểm hình của lớp đơn sắc và của biểu tượng thông báo, và vẽ `store-icon-512.png` cho Google Play từ chính các lớp của biểu tượng, nên ảnh trên Play không thể lệch khỏi app.
- Số tài khoản mẫu: số Vietcombank trong ảnh tham chiếu trông như thật. Số ACB mẫu lấy từ ví dụ trong README của vietnam-qr-pay, có thể là tài khoản của tác giả. Cả hai thành toàn số 0, mười hai ảnh được ghi lại, và so từng điểm ảnh thì chỉ dòng số tài khoản và mã QR khác. Bài so mã QR với vietnam-qr-pay giữ ví dụ của thư viện.
- Review độc lập tìm ra một số Vietcombank thứ hai còn sót trong catalog debug, hai phép kiểm chuỗi số 0 lỏng, và hai bài kiểm hình còn thiếu; tất cả đã được sửa. Hai bài kiểm hình được thử làm hỏng có chủ đích, và cả hai đều báo lỗi.
- Review cũng chỉ ra một lỗi có từ trước: màn nhận phần coi "danh sách cách nhận tiền chưa tải" là "đã đổi", nên một liên kết đã gửi số tiền có thể thoáng hiện "Send the new details". Theo yêu cầu của chủ sản phẩm, lỗi được sửa ngay trong phiên này. Bài mới tái hiện được lỗi trên mã cũ trước khi sửa.

## Quyết định

- Số mẫu chỉ có chữ số 0: không ngân hàng nào cấp số như vậy. Hai số cần khác nhau thì khác độ dài, và các phép kiểm khớp đúng cả dòng hoặc đúng trường, vì một chuỗi số 0 cũng nằm trong chuỗi số 0 dài hơn.
- Ảnh 512 px cho Play nằm cùng các ảnh tham chiếu và do bài kiểm thử vẽ, thay vì được vẽ tay bên ngoài.
- Trong bản sửa màn nhận phần, cách nhận tiền được chọn ngay trong cùng phép kết hợp với danh sách. Nếu chỉ chờ danh sách tải mà vẫn đọc cách đã chọn từ một luồng riêng, thì giữa hai lần cập nhật vẫn có một khoảnh khắc báo nhầm.
- Không viết lại lịch sử git công khai để xóa số cũ.

## Bước tiếp

- Chủ sản phẩm quyết định có merge bản sửa màn nhận phần không, và có đổi luôn các mã thanh toán mẫu khác không: số Vietcombank trong kiểm thử của proxy; số PromptPay, IBAN ví dụ, `revolut.me/dana` và `paypal.me/minhtran` trong ảnh.
- Chủ sản phẩm còn phải duyệt bảy ảnh cửa hàng, bật dependency graph của repo, và thử tay trên điện thoại thật.
- Nhánh cũ `fix/fictional-sample-account` không xóa được từ phiên này (GitHub trả 403); commit duy nhất của nó đã nằm trên `main`.

> Historical work record — not durable authority. Prefer docs/specs/ADRs for current decisions.
