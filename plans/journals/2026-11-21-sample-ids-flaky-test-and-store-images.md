---
title: "Mã mẫu, bài kiểm thử chập chờn và bảy ảnh cửa hàng đã duyệt"
date: 2026-11-21
summary: Mọi mã thanh toán và tên hóa đơn mẫu có thể là của người hay doanh nghiệp thật được đổi; một bài Robolectric chập chờn được chứng minh nguyên nhân và sửa; chủ sản phẩm duyệt bảy ảnh cho Google Play.
---

# Mã mẫu, bài kiểm thử chập chờn và bảy ảnh cửa hàng đã duyệt

## Chuyện gì đã xảy ra

- Theo các quyết định của chủ sản phẩm, những mã mẫu có thể là của người thật được đổi khắp app, proxy, web và tài liệu: số Vietcombank trong kiểm thử của proxy thành số 0, PromptPay thành `000-000-0000`, các tên Revolut, PayPal và UPI thành `splitvibedemo`. Hai hóa đơn mẫu mang tên một quán phở và một công ty gọi xe có thật thành "Phở sáng" và "Taxi". Bộ mẫu so với promptpay-qr và vietnam-qr-pay giữ ví dụ của hai thư viện, IBAN ví dụ chuẩn giữ nguyên, và các bảng thiết kế đã duyệt không sửa.
- Tên thay thế đầu tiên, "Tuk-tuk ride", bị ngắt giữa từ ở cỡ chữ 200% trong danh sách hóa đơn. Tên ngắn bằng tên cũ giữ được bố cục, nên chỉ dòng tên trong 15 ảnh đổi.
- Lượt CI trên `main` sau bản sửa màn nhận phần đỏ một lần ở `ReducedMotionTest`, và chạy lại thì xanh. Báo cáo kiểm thử cho thấy truy vấn `PersonDao.recent` gặp cơ sở dữ liệu đã đóng. Bài thử tạm đầu tiên không tái hiện được, vì độ trễ đặt trước khi truy vấn bắt đầu thì lệnh hủy luôn thắng. Bài thứ hai cũng không, vì lần đọc lại phải đi qua looper chính mà bài thử không cho chạy. Bài thứ ba giữ lần đọc sau khi nó bắt đầu, và tái hiện được lỗi một cách chắc chắn.
- `TestStorage.close()` giờ hủy các view model đọc, đợi chúng dừng hẳn trong khi looper chính chạy, rồi mới đóng cơ sở dữ liệu. Mười một lớp kiểm thử dùng cơ chế này chạy 10 lượt liền đều qua.
- Chủ sản phẩm duyệt bảy ảnh cho Google Play và giữ nguyên hóa đơn mẫu mà app kèm sẵn.

## Quyết định

- Mã mẫu chỉ dùng số toàn 0 hoặc tên gắn với app. Các bài kiểm "không được lộ" dùng dấu chữ riêng, vì chuỗi số 0 cũng có trong mã và dấu thời gian.
- Tên mẫu thay thế phải ngắn như tên cũ, để ảnh tham chiếu chỉ đổi đúng chỗ.
- Một lỗi chập chờn chỉ được sửa sau khi tái hiện được cơ chế; chạy lại CI thì chỉ xác nhận đó là lỗi chập chờn.

## Bước tiếp

- Chủ sản phẩm bật dependency graph của repo và thử tay trên điện thoại thật.
- Nhánh cũ `fix/fictional-sample-account` vẫn còn trên GitHub, vì phiên làm việc không có quyền xóa nhánh.

> Historical work record — not durable authority. Prefer docs/specs/ADRs for current decisions.
