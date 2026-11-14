---
title: "Phase 2: hệ thiết kế Expressive, chuyển động, bộ thành phần và review"
date: 2026-11-14
summary: "Phase 2 dựng xong hệ thiết kế, lớp chuyển động và bộ thành phần; test thiết bị có kiểm tra trợ năng tìm ra lỗi thật; review độc lập tìm ra hai lỗi nặng, đã sửa cả mười lăm phát hiện."
---

# Phase 2: hệ thiết kế Expressive, chuyển động, bộ thành phần và review

## Chuyện gì đã xảy ra

Phase 2 dựng xong hệ thiết kế `:core:designsystem` trên nhánh `feat/phase-02-design-system`: bảng màu Violet do chủ sản phẩm chọn, token chữ, khoảng cách, hình khối, bốn mức độ nổi bằng `dropShadow`, đủ bộ thành phần mà plan liệt kê, và lớp chuyển động theo bảng hành trình. Bảy màn hình và hai bảng trượt dựng lại trên đó.

Năm chỗ đáng nhớ:

1. **Số tiền không chạy khi đổi giá trị.** Khi viết test cho một lỗi nhỏ (giá trị mới đến giữa lúc đang chạy), lần chạy đỏ trên mã cũ cho thấy lỗi lớn hơn: sau lần đếm đầu, mọi lần đổi đều nhảy thẳng tới giá trị mới. Bản sửa giữ điểm đầu và điểm cuối của mỗi lần chạy. `AmountAnimationTest` được chứng minh đỏ trên mã cũ và trên một biến thể chỉ có lỗi nhảy giữa chừng.
2. **Kiểm tra tiếp cận tìm ra lỗi thật.** Test thiết bị mới chia hóa đơn mẫu tới bước gửi yêu cầu, có kiểm tra tiếp cận ở mỗi lần chạm. Lần chạy đầu hỏng ở màn hình kết quả: một hàng món chỉ ló 8dp trên thanh dưới cùng. Đọc mã đã biên dịch của Compose cho thấy nút trợ năng chỉ lấy chữ từ chính nó, còn chữ bị thanh che thì bị bỏ khỏi cây, nên trình đọc màn hình không có gì để đọc. Sửa ở hệ thiết kế: hàng bấm được mang nhãn riêng của nó, và chữ ký hàm cho hàng bấm được bắt buộc có nhãn.
3. **Bóng của Material.** Nút bật tắt của menu nổi Material tự vẽ bóng xám không tắt được, lệch với bóng ánh tím của app. Hệ thiết kế tự vẽ nút đó với cùng kiểu đổi hình và lấy độ nổi từ token.
4. **Review độc lập tìm ra hai lỗi nặng mà test không bắt được.** Khóa của các bước là lớp Kotlin, mã băm của nó đổi mỗi lần app chạy, nên sau khi hệ thống tắt app thì mọi thứ lưu trong một bước không quay lại, kể cả ảnh vừa chụp bằng camera. Và nút trang chủ khi nở ra thành màn hình chia hóa đơn bị đo lại ở mọi kích thước, kéo theo bóng bị làm mờ lại bằng CPU ở từng khung hình. Đã sửa cả mười lăm phát hiện, có bằng chứng trong `plans/reports/code-reviewer-261114-1054-phase-02-design-system-review.md`.
5. **Lỗi workflow lộ ra khi lấy baseline profile mới.** Một lần chạy có hai artifact cùng tên `baseline-profile`; một cái chỉ là bản cũ đã commit, do job test tải lên. Tải theo tên có thể lấy nhầm bản cũ. Đã giới hạn bước tải lên cho job ghi profile.

## Quyết định

- Ảnh đến từ bộ chọn ảnh của hệ thống nên không có ảnh thu nhỏ để bay từ đó; ảnh mới đáp vào khung, lớn dần từ một thẻ nhỏ và bo tròn hơn.
- Nút yêu cầu thu lại thành dấu tích bằng bề mặt dùng chung giữa hai bước, không dùng `Morph` vì hai đầu đều bo tròn hoàn toàn.
- Các bước của luồng mờ dần qua nhau theo kiểu fade-through (bước cũ tắt trong 140 ms đầu, bước mới hiện trong 260 ms sau), vì ảnh chụp giữa chừng cho thấy hai bước đè lên nhau khi mờ cùng lúc.
- Menu nổi để màn hình giữ trạng thái mở và tự xử lý nút quay lại, vì kéo `activity-compose` vào hệ thiết kế làm lộ các phiên bản cũ hơn của lifecycle chưa có checksum.

## Bước tiếp theo

- Chủ sản phẩm duyệt giao diện trên máy thật bằng bản dev và chọn máy tham chiếu để đo ngân sách hiệu năng.
- Quyết định gộp Phase 2 vào `main`.
- Phase 3: backend Firebase và cổng AI.

> Historical work record — not durable authority. Prefer docs/specs/ADRs for current decisions.
