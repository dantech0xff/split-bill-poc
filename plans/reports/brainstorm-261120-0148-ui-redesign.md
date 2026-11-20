# Thiết kế lại giao diện Split Vibe: phỏng vấn và hướng đã chọn

Ngày 20/11/2026. Chủ sản phẩm nhận xét giao diện hiện tại xấu và yêu cầu thiết kế lại trên canvas,
duyệt xong mới đưa vào dự án. Tệp này ghi lại câu trả lời của chủ sản phẩm, hướng thiết kế đã chọn
và những gì đã vẽ, để các phiên sau làm tiếp mà không phải hỏi lại.

## Kết quả mong muốn

Split Vibe có một giao diện mới mà chủ sản phẩm duyệt trên canvas, rồi được đưa vào app Android
(và các trang web dùng chung token) mà không mất tính năng nào.

## Câu trả lời của chủ sản phẩm

| Câu hỏi | Trả lời |
|---|---|
| Điều gì xấu nhất? | Cả bốn: nhạt và không cá tính; màu sắc; thứ bậc và bố cục; rối như biểu mẫu |
| Cảm giác mong muốn | Trẻ, vui, đậm màu |
| App tham khảo | Wise, Revolut (rõ ràng, số tiền là trung tâm) và Cash App, Duolingo (táo bạo, có cá tính) |
| Mức thay đổi | Làm lại cả điều hướng: thanh điều hướng dưới, trang chủ có tổng số nợ, được gộp hoặc tách màn hình |

## Chẩn đoán giao diện hiện tại

- Lần chọn giao diện ngày 14/11 chỉ là chọn một trong ba màu trên cùng một bố cục. Bố cục, chữ và
  nhận diện chưa từng được thiết kế, nên app trông như Material mặc định phủ tím.
- Thứ bậc yếu: chữ nhỏ, các hàng xám tím giống nhau, số tiền không nổi bật.
- Trang chủ không có con số nào; bốn chip nhỏ làm điều hướng.
- Màn kết quả đặt câu trả lời (mỗi người trả bao nhiêu) ở cuối trang.
- Biểu mẫu nhiều ô viền và câu giải thích dài; thông báo màu hồng trông như báo lỗi.

## Hướng thiết kế

Khối màu đậm, số tiền to, chữ có cá tính, trên nền trung tính không ám tím.

| Thành phần | Giá trị |
|---|---|
| Màu thương hiệu | Tím điện `#5B34F5`, chữ trắng trên nền này |
| Màu nhấn | Xanh chanh `#C9F73A`, chữ mực trên nền này; dùng trên nền tối hoặc nền tím |
| Mực và nền | Mực `#111114`, nền trang `#F6F6F4`, thẻ trắng `#FFFFFF`, nền phụ `#F0EFEA`, chữ phụ `#5F5E66` |
| Màu người và ô danh mục | Hồng `#FF6FB5`, cam `#FF8A3D`, xanh trời `#5CC8FF`, vàng `#FFD23F`, luôn đi với chữ mực |
| Tiền dương và âm | "gets back" `#0A6B4E`, "owes" `#B33A0A`, luôn kèm chữ, không chỉ dựa vào màu |
| Chữ tiêu đề và số tiền | Bricolage Grotesque 700 đến 800 |
| Chữ nội dung | Be Vietnam Pro 400 đến 700 |
| Hình khối | Thẻ bo 24, khối chính bo 32, nút dạng viên thuốc cao 56, ô biểu tượng 44 bo 14 đến 16 |
| Biểu tượng | Nét tròn 2px, không dùng emoji |

Quy tắc màu nút: trên nền sáng, nút chính màu tím; trên nền tối hoặc nền tím, nút chính màu xanh
chanh. Các cặp chữ và nền chính đã được tính và đều đạt tương phản 4,5:1.

Cả hai phông đều có giấy phép mở và có dấu tiếng Việt; đưa vào app sẽ phải đóng gói tệp phông.

## Điều hướng mới

- Thanh dưới nổi màu mực: Home, Groups, nút tròn xanh chanh "Split a bill" ở giữa, Requests (có số
  đếm), Insights.
- Cài đặt, Pro và cách nhận tiền nằm sau ô chữ cái ở góc trên trang chủ.
- Danh sách hóa đơn và hóa đơn định kỳ mở từ "See all" trên trang chủ; hai màn này dự kiến gộp
  thành hai thẻ của một màn.
- Trang chủ: khối "Friends owe you" tính từ các yêu cầu trả tiền còn mở (cần đăng nhập), dòng
  "You owe", ô chi tiêu tháng này và ô nhóm gần nhất, rồi các hóa đơn gần đây. Khi chưa đăng nhập,
  khối chính sẽ hiện chi tiêu tháng này; trạng thái này chưa vẽ.

## Đã vẽ trên canvas

Canvas tên "Split Vibe: thiết kế lại giao diện" (loại Design trên claude.ai, riêng tư của chủ sản
phẩm; tìm bằng danh sách artifact). Tệp nguồn của canvas không nằm trong repo.

- Trang "Thiết kế mới": trang chủ, hóa đơn mới, kết quả chia, đòi tiền, một nhóm. Các nút chính nối
  năm màn thành một luồng bấm thử được. Bảng trang chủ có lựa chọn thử bốn màu thương hiệu.
- Trang "Hiện tại và vấn đề": năm màn hình hiện tại tương ứng, mỗi màn kèm ghi chú vấn đề.

Thay đổi bố cục đáng chú ý ở màn kết quả: phần "Each person pays" lên ngay dưới tổng tiền và gộp
với chip người; kiểu chia thành một hàng nút cuộn ngang ngay trên danh sách món.

## Ràng buộc

- Giữ mọi tính năng và luồng dữ liệu hiện có; không thêm tính năng ngoài phần trình bày lại dữ liệu
  đã có.
- Bản thiết kế phải làm được bằng Compose với Material 3 và module `:core:designsystem`.
- Đạt các kiểm tra trợ năng đang có: tương phản, vùng chạm 48dp, nhãn cho trình đọc màn hình, chữ
  cỡ 200%.
- Chủ sản phẩm duyệt trên canvas trước khi sửa code.

## Ngoài phạm vi

- Dịch giao diện sang tiếng Việt.
- Thay đổi máy chủ, giá Pro hay hạn mức AI.

## Tiêu chí hoàn thành

1. Chủ sản phẩm duyệt hướng trên năm màn đầu, rồi duyệt đủ các màn còn lại ở cả giao diện sáng và
   tối.
2. App dùng token, thành phần và điều hướng mới trên mọi màn hình; ảnh tham chiếu được ghi lại.
3. Kiểm thử đơn vị, lint, kiểm thử trên máy ảo API 26, 30, 34, 36 và kiểm tra trợ năng đều qua.
4. Các trang web dùng chung token đổi theo; tài liệu hệ thiết kế và ảnh cho Google Play được cập
   nhật.

## Việc tiếp theo

1. Chủ sản phẩm xem năm màn đầu và chốt hướng, màu thương hiệu và điều hướng.
2. Vẽ các màn còn lại: danh sách nhóm, yêu cầu trả tiền, thống kê, hóa đơn và định kỳ, sửa hóa đơn,
   cài đặt, Pro, xin đồng ý dùng AI, các bảng trượt, các trạng thái trống, đang chạy và lỗi, giao
   diện tối.
3. Sau khi duyệt: lập plan triển khai trên nhánh `feat/ui-redesign`.

## Câu hỏi còn mở

1. Giữ tím làm màu thương hiệu, hay đổi sang một trong các màu thử trên bảng trang chủ?
2. Bốn mục điều hướng Home, Groups, Requests, Insights đã đúng chưa?
3. Định dạng tiền và ngày vẫn chờ quyết định từ trước; bản vẽ đang dùng kiểu tiếng Anh, ví dụ
   "₫902,664" và "Nov 13, 2026".
