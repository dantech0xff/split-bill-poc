---
title: "Plan đưa giao diện mới vào app và web: phản biện, tám quyết định, mười giai đoạn"
date: 2026-11-20
summary: "Bản plan thiết kế lại được sửa sau lượt phản biện: một ngăn xếp điều hướng, cổng kiểm thử bằng chế độ so ảnh, phông ghim theo mã băm, thêm giai đoạn tiền và ngày theo vùng của máy, và thêm giai đoạn trang web với 12 bảng chờ duyệt."
---

# Plan đưa giao diện mới vào app và web: phản biện, tám quyết định, mười giai đoạn

## Chuyện gì đã xảy ra

- Bốn người phản biện độc lập đọc bản nháp tám giai đoạn của `plans/261120-0528-ui-redesign/` và báo 39 phát hiện, gộp lại còn 15, trong đó 8 ở mức cao. Chủ sản phẩm chọn áp dụng cả 15.
- Phát hiện nặng nhất nằm ở điều hướng: thanh dưới chỉ hiện ở bốn màn gốc, nên lịch sử riêng của từng tab không bao giờ có gì để giữ, trong khi công thức nhiều ngăn xếp lại cần lifecycle 2.11. Plan giờ giữ một ngăn xếp như hôm nay và chỉ thêm phép thay màn gốc.
- Bản nháp viết "qua khi bỏ các bài so ảnh" mà không có cách làm. Đã thử thật với `ComponentCatalogTest`, một ảnh tham chiếu bị đổi và một ảnh bị xóa: `-Pscreenshots=compare` trên Roborazzi 1.76.0 chạy trọn bài kiểm thử, không hỏng vì ảnh, không ghi gì vào `app/src/test/screenshots/`, và để ảnh mới ở `app/build/outputs/roborazzi/`. Đó là cổng của các giai đoạn 1 đến 7.
- Bảy tệp phông và hai tệp giấy phép được ghim theo commit của kho gốc và ghi mã băm SHA-256 trong giai đoạn 1. Mã băm lấy qua proxy của môi trường làm việc, nên giai đoạn 1 còn phải so lại trên một máy sạch của GitHub.
- Chủ sản phẩm trả lời bảy câu hỏi kiểm định. Hai câu trả lời làm plan lớn hơn: số tiền và ngày theo vùng của điện thoại (thêm giai đoạn 4), và thiết kế lại cả trang web trong plan này (thêm giai đoạn 9).
- 37 bảng đã duyệt được lưu vào `boards/` cùng danh sách mã băm. Ba chỗ chữ trên canvas đổi sang giọng sản phẩm. 12 bảng web được vẽ lên trang "Trang web" của canvas và đang chờ duyệt.
- Plan giờ có mười giai đoạn, 36 ngày công ước lượng, chạy lần lượt.

## Quyết định

- Một ngăn xếp điều hướng, bốn màn gốc, nút giữa "Split a bill" chỉ có mô tả trợ năng; Bills và Recurring thành hai thẻ của một màn ngay ở giai đoạn điều hướng.
- Tổng yêu cầu trả tiền có một nguồn duy nhất, tải lại khi mở app, dùng chung cho số đếm trên tab, trang chủ và màn Requests.
- Tiền theo vùng của máy với chữ số 0 đến 9 và ký hiệu tiền tệ như hôm nay; ngày theo thứ tự của vùng với tên tháng tiếng Anh.
- Tên Claude rời khỏi mọi chuỗi ngoài phần xin đồng ý; các chuỗi `ai_consent_*` không đổi một chữ.
- Không mở pull request khi chủ sản phẩm chưa yêu cầu; cuối mỗi giai đoạn chạy tại chỗ các lệnh của `ci.yml`.

## Bài học

- Năm câu trong bản nháp có dạng "bài kiểm thử X bắt lỗi này" và cả năm đều sai khi lần theo mã. Từ nay câu như vậy phải kèm dòng mã của phép khẳng định.
- Bản tóm tắt giao cho người viết lại từng giai đoạn có ba chỗ sai so với mã (một số dòng, nơi làm mới danh sách yêu cầu, và một nơi đặt thanh ngoài `Scaffold`). Người viết đối chiếu mã và sửa, nên các tệp giai đoạn đúng hơn bản tóm tắt.
- Dữ liệu vùng của Java dùng trong kiểm thử khác dữ liệu của từng bản Android. Kiểm thử chỉ khẳng định chuỗi chính xác cho ba vùng ổn định và khẳng định tính chất cho các vùng còn lại.

## Việc tiếp theo

- Chủ sản phẩm duyệt 12 bảng web; sau đó lưu chúng vào `boards/web/` và chốt giai đoạn 9.
- Chủ sản phẩm xem các mặc định do người lập plan đặt trong `plan.md`, nhất là việc tải lại danh sách yêu cầu khi mở app và trần 120 KB cho phông của web.
- Khi chủ sản phẩm chọn thực hiện, bắt đầu từ giai đoạn 1.

> Historical work record — not durable authority. Prefer docs/specs/ADRs for current decisions.
