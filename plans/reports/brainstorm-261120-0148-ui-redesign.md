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
phẩm; tìm bằng danh sách artifact). Tệp nguồn của canvas không nằm trong repo. Canvas có 42 bảng
trên ba trang.

Chủ sản phẩm đã xem năm màn đầu, bảo vẽ tiếp, và chỉ yêu cầu bỏ nhãn "Photo stays on this phone"
trên màn hóa đơn mới.

Trang "Thiết kế mới" có 32 bảng theo năm hàng:

| Hàng | Bảng |
|---|---|
| Luồng chính | Trang chủ, hóa đơn mới, kết quả chia, đòi tiền, một nhóm |
| Luồng chia hóa đơn: các trạng thái | Trang chủ lần đầu, hóa đơn mới chưa có ảnh (có công tắc "AI chưa cài"), xin đồng ý dùng AI, đang đọc hóa đơn, đề xuất của AI, chia theo phần trăm |
| Nhóm, yêu cầu trả tiền và liên kết hóa đơn | Các nhóm, tham gia nhóm, yêu cầu trả tiền, một yêu cầu trả tiền, cách nhận tiền, liên kết hóa đơn lúc mở và lúc đã khóa chọn |
| Bảng trượt, sửa hóa đơn và hộp thoại | Thêm người, chia một món, ai đã trả, sửa hóa đơn, lặp lại hóa đơn, xuất file, xóa tài khoản |
| Hóa đơn, thống kê, cài đặt và Pro | Hóa đơn, hóa đơn định kỳ, thống kê, cài đặt, các trạng thái tài khoản, mua Pro, đã có Pro |

Trang "Giao diện tối" có năm màn của luồng chính. Các màn khác suy ra từ cùng bảng màu tối: nền
`#0F0F12`, thẻ `#1C1C21`, nền phụ `#2A2A31`, chữ `#F4F4F1`, chữ phụ `#B7B6BF`, nút chính màu xanh
chanh, thanh nổi `#26262C` có viền `#3A3A42`.

Trang "Hiện tại và vấn đề" có năm màn hình hiện tại của luồng chính, mỗi màn kèm ghi chú vấn đề.

Các nút trên bảng nối với nhau thành bản bấm thử được, và không liên kết nào trỏ tới bảng chưa có.
Chưa bảng nào được dựng lại để soát bố cục: chiều cao và chỗ xuống dòng là ước lượng.

## Thay đổi so với app hiện tại cần chủ sản phẩm xác nhận

- Màn kết quả: "Each person pays" lên ngay dưới tổng tiền và gộp với chip người; kiểu chia thành
  một hàng nút ngay trên danh sách món.
- Danh sách hóa đơn và hóa đơn định kỳ gộp thành hai thẻ "All bills" và "Recurring" của một màn.
  Ô tìm kiếm chỉ có ở thẻ "All bills".
- Màn yêu cầu trả tiền có hai ô tóm tắt "You owe" và "Friends owe you", cộng từ các yêu cầu còn mở.
- Màn sửa hóa đơn hiện món và phụ phí thành hàng, mỗi lúc mở một hàng để sửa, thay cho bốn ô nhập
  trên mỗi món. Phần "Total" cuối màn lấy theo code hiện tại.
- Màn kết quả không còn phần "Show what was sent to Claude": chủ sản phẩm yêu cầu bỏ vì nó quá kỹ
  thuật với người dùng. Khi đưa vào app, phần này và hai chuỗi của nó sẽ được gỡ.
- Thống kê theo tháng là biểu đồ cột 12 tháng.
- Cài đặt có hàng hồ sơ và thanh đo số lượt gọi AI.
- Nhãn mới chưa có trong `strings.xml`: "Paid the bill", "All bills", "Recurring", "You owe",
  "Friends owe you", "Tell the AI what to change", và ngày viết kiểu "Nov 13".
- Mã tiền tệ cách số một dấu cách, ví dụ "THB 187.00"; app hiện in "THB187.00".
- Ô nhóm trên trang chủ cần số dư của nhóm đó. Danh sách nhóm từ máy chủ không có số dư, nên ô này
  chỉ hiện số dư khi trạng thái nhóm đã lưu trên máy.

Chưa vẽ: bản tóm tắt PDF (là tài liệu, không phải màn hình), hai khung hình chuyển cảnh, và các
trang web cho người nhận liên kết.

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

1. Chủ sản phẩm duyệt hướng trên năm màn đầu, rồi duyệt đủ các màn còn lại. Giao diện tối được
   duyệt trên năm màn chính và bảng màu tối.
2. App dùng token, thành phần và điều hướng mới trên mọi màn hình; ảnh tham chiếu được ghi lại.
3. Kiểm thử đơn vị, lint, kiểm thử trên máy ảo API 26, 30, 34, 36 và kiểm tra trợ năng đều qua.
4. Các trang web dùng chung token đổi theo; tài liệu hệ thiết kế và ảnh cho Google Play được cập
   nhật.

## Việc tiếp theo

1. Chủ sản phẩm duyệt 37 bảng thiết kế, kể cả các thay đổi liệt kê ở trên.
2. Sửa theo góp ý của chủ sản phẩm trên canvas.
3. Sau khi duyệt: lập plan triển khai trên nhánh `feat/ui-redesign`.

## Câu hỏi còn mở

1. Chủ sản phẩm bảo vẽ tiếp mà không yêu cầu đổi màu thương hiệu hay điều hướng. Bản vẽ giữ tím
   và bốn mục Home, Groups, Requests, Insights; cần xác nhận lần cuối khi duyệt.
2. Định dạng tiền và ngày vẫn chờ quyết định từ trước; bản vẽ đang dùng kiểu tiếng Anh, ví dụ
   "₫902,664" và "Nov 13, 2026".
3. Có dựng lại và chụp các bảng để soát bố cục trước khi duyệt không?
4. Chủ sản phẩm muốn người dùng nghĩ việc đọc hóa đơn do một model OCR ở backend làm. Bản vẽ không
   làm theo, vì app đọc chữ trên máy và chỉ gửi chữ cho Claude, đúng như màn xin đồng ý và chính
   sách quyền riêng tư đang nói. Đang chờ chủ sản phẩm trả lời đề xuất thay thế: bỏ tên Claude
   khỏi dòng trạng thái, thẻ tóm tắt và câu giới thiệu, giữ nó ở màn xin đồng ý và chính sách.

## Kết quả (21/11/2026)

Bản thiết kế lại được làm xong trên nhánh `feat/ui-redesign` theo plan [`261120-0528-ui-redesign`](../261120-0528-ui-redesign/plan.md), qua mười giai đoạn, và đang chờ chủ sản phẩm cho merge. Bốn câu hỏi còn mở ở trên đã được trả lời:

1. Màu thương hiệu tím và bốn mục Home, Groups, Requests, Insights được giữ, và chủ sản phẩm duyệt cả 37 bảng ngày 20/11/2026.
2. Số tiền và ngày theo vùng của máy, ở app (giai đoạn 4) và ở trang web theo vùng của trình duyệt (giai đoạn 9).
3. Các bảng được lưu cố định trong `boards/` và `boards/web/`; ảnh chụp màn hình thật của từng nhóm được chủ sản phẩm duyệt ngày 21/11/2026.
4. Chủ sản phẩm chọn đề xuất thay thế: tên Claude được bỏ khỏi dòng trạng thái, thẻ tóm tắt và câu giới thiệu, và chỉ còn ở màn xin đồng ý, mục đồng ý trong Cài đặt và chính sách quyền riêng tư. Một bài kiểm thử giữ quy tắc này cho mọi chuỗi của app.
