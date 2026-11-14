---
title: "Phase 6: Trang web cho người nhận và tự nhận món trực tiếp"
status: todo
phase: 6
priority: P1
effort: "8d"
dependencies: [5]
---

# Phase 6: Trang web cho người nhận và tự nhận món trực tiếp

## Overview

Người nhận yêu cầu mở một link và thấy phần của mình cùng cách trả, không cần cài app. Cả bàn có thể mở cùng một link (hoặc app) và mỗi người tự chạm vào món mình ăn; mọi người thấy cập nhật trực tiếp, rồi người trả tiền chốt cách chia.

Tính năng v1.0 thuộc giai đoạn này: trang web trong "thẻ chia sẻ và trang web"; mỗi người tự nhận món.

## Context

- Đây là bề mặt mới hoàn toàn: repo chưa có mã web nào và chưa có dữ liệu công khai nào.
- Đây cũng là bề mặt đầu tiên mà người **chưa đăng nhập bằng tài khoản thật** được đọc và ghi dữ liệu, nên phần bảo mật là trọng tâm của giai đoạn.
- Dữ kiện: [báo cáo backend](../reports/researcher-261114-0209-product-backend-and-payment-links.md), mục 4.2 (App Check) và 4.5 (rules).

## Key Insights

- Rules của Firestore không lọc được theo tham số trên URL, nên dùng mẫu "đường dẫn là chìa khóa": tài liệu công khai nằm ở `publicSplits/{token}`, cho phép đọc **một** tài liệu theo đúng đường dẫn và cấm liệt kê.
- Tài liệu công khai chỉ là một **bản chiếu** của hóa đơn: đúng những gì người nhận cần thấy. Hóa đơn gốc trong `bills` không bao giờ mở cho người lạ.
- Mỗi người nhận món ghi vào một tài liệu riêng của mình, nên hai người chạm cùng lúc không ghi đè nhau.
- Trang web không có mã tính tiền của Kotlin, nên phần mỗi người do app của người chốt tính rồi ghi vào bản chiếu. Trang web chỉ hiển thị.

## Requirements

Chức năng:
- **Xem:** trang `…/s/{token}` hiện hóa đơn, phần của từng người, cách trả của người đòi (link, QR hoặc thông tin), bằng ngôn ngữ của trình duyệt.
- **Tự nhận món:** người mở link nhập tên, chạm vào các món của mình; một món có thể có nhiều người nhận; danh sách cập nhật trực tiếp cho mọi người đang mở, trên web lẫn trong app.
- **Chốt:** người trả tiền xem ai đã nhận gì, sửa nếu cần, rồi chốt. Việc nhận món khóa lại và trang hiện phần phải trả của từng người.
- **Thu hồi:** người tạo link tắt được link bất cứ lúc nào; link tự hết hạn sau số ngày đã định.

Phi chức năng:
- Trang mở nhanh trên điện thoại: điểm hiệu năng và điểm tiếp cận của Lighthouse từ 90 trở lên ở chế độ di động.
- Trang dùng cùng token màu, chữ và hình khối với app.
- Công cụ tìm kiếm không lập chỉ mục các trang chia sẻ.

## Architecture

```
web/  (ứng dụng một trang tĩnh, TypeScript, Firebase JS SDK)
  /s/{token}        xem bản chiếu, tự nhận món, xem phần phải trả
Firebase Hosting ── phục vụ web/, kèm tệp xác minh App Links cho link mời nhóm của Phase 5
Firestore
  publicSplits/{token}                 bản chiếu, chủ hóa đơn ghi, ai có đường dẫn thì đọc được
  publicSplits/{token}/claims/{cid}    món đã nhận của một người, người đó ghi
App Android ── ghi bản chiếu, nghe claims, chốt và ghi phần mỗi người
```

Bản chiếu `publicSplits/{token}` gồm: tên quán, ngày, tiền tệ, các món và giá, phụ phí, tổng, tên người tham gia như đã nhập, phần mỗi người (sau khi chốt), cách trả của người đòi, `ownerUid`, `status` (`open`, `locked`), `expiresAt`.

Luật truy cập:
- `get` cho mọi người; `list` bị cấm. Chỉ `ownerUid` tạo, sửa và xóa bản chiếu.
- `claims/{cid}`: người dùng đã đăng nhập ẩn danh trên web tạo và sửa tài liệu có `cid` bằng `uid` của chính họ, chỉ khi `status` là `open`; các trường bị kiểm kiểu, độ dài tên và phạm vi chỉ số món.
- App Check (reCAPTCHA Enterprise trên web) bắt buộc cho Firestore.
- Chính sách TTL của Firestore xóa bản chiếu theo `expiresAt`.

Khung web: ứng dụng một trang tĩnh bằng TypeScript với một thư viện giao diện nhỏ, chốt cụ thể ở bước lập kế hoạch lại đầu giai đoạn. Điều kiện chọn: gói tải ban đầu dưới 150 KB đã nén và không cần server dựng trang.

## Files

Tạo mới:
- `web/` (`package.json`, `index.html`, `src/main.ts`, `src/split-view.ts`, `src/claim.ts`, `src/tokens.css`, `tests/`)
- `functions/src/share-token.ts` (tạo token và bản chiếu qua callable, để token do server sinh)
- `app/src/main/java/com/dantech/splitvibe/data/share/PublicSplitRepository.kt`
- `app/src/main/java/com/dantech/splitvibe/ui/claim/` (màn hình theo dõi nhận món và chốt)
- `firestore-tests/public-splits.test.ts`

Sửa:
- `firebase.json` (Hosting, đường dẫn `/s/**`, tiêu đề cấm lập chỉ mục), `firestore.rules`
- `core/designsystem` (xuất token ra `web/src/tokens.css`)
- `app/src/main/java/com/dantech/splitvibe/ui/settle/` (thêm link web vào yêu cầu trả tiền)

## Implementation Steps

1. Viết test rules trước cho `publicSplits` và `claims` (đọc theo đường dẫn, cấm liệt kê, kiểm dữ liệu ghi, khóa sau khi chốt, thu hồi).
2. Viết callable tạo token (128 bit ngẫu nhiên, mã hóa an toàn cho URL) và bản chiếu; `PublicSplitRepository` trong app.
3. Dựng trang xem trên `web/` với token thiết kế xuất từ app; triển khai lên Hosting của project dev.
4. Thêm đăng nhập ẩn danh và App Check trên web; dựng chế độ tự nhận món với trình nghe trực tiếp.
5. Màn hình trong app: theo dõi ai nhận gì, sửa, chốt. Khi chốt, app chuyển các món đã nhận thành cách chia, tính phần mỗi người bằng `:core:domain`, ghi vào bản chiếu và khóa.
6. Thu hồi, hết hạn, trang báo lỗi cho link không còn hiệu lực.
7. Test đầu-cuối bằng trình duyệt tự động ở khung nhìn điện thoại; đo Lighthouse.

## Todo

- [ ] Test rules cho `publicSplits` và `claims`
- [ ] Callable tạo token và bản chiếu
- [ ] Trang xem trên Hosting với token thiết kế
- [ ] Đăng nhập ẩn danh và App Check trên web
- [ ] Tự nhận món trực tiếp trên web và trong app
- [ ] Chốt: món đã nhận thành cách chia, ghi phần mỗi người, khóa
- [ ] Thu hồi, hết hạn, trang link hết hiệu lực
- [ ] Test đầu-cuối trên trình duyệt và điểm Lighthouse

## Success Criteria

- Test rules qua: liệt kê `publicSplits` bị từ chối; đọc đúng đường dẫn được phép; người lạ không sửa được bản chiếu; ghi `claims` sai kiểu, quá dài, sai chỉ số món hoặc sau khi khóa đều bị từ chối; token đã thu hồi không đọc được.
- Hai trình duyệt và một máy Android mở cùng link: một bên chạm nhận món thì hai bên kia thấy trong vòng 2 giây.
- Sau khi chốt, phần mỗi người trên trang web bằng đúng kết quả của `SplitCalculator` trên cùng fixture (test đối chiếu tự động).
- Hóa đơn gốc trong `bills` không đọc được bằng tài khoản ẩn danh của trang web.
- Lighthouse di động: hiệu năng và tiếp cận từ 90 trở lên; gói tải ban đầu dưới 150 KB đã nén.
- Trang trả tiêu đề cấm lập chỉ mục; link hết hạn hiện thông báo rõ ràng thay vì lỗi trắng.
- Tắt App Check giả lập thì yêu cầu ghi bị từ chối trên project prod.

## Risks

| Rủi ro | Dấu hiệu nhận biết | Phản ứng đã định |
|---|---|---|
| Link bị chuyển tiếp cho người ngoài | Xuất hiện người nhận món lạ | Người trả thấy danh sách, xóa được từng người và thu hồi link; bản chiếu không chứa gì ngoài hóa đơn và cách trả |
| Phá hoại bằng cách ghi rác vào `claims` | Số tài liệu `claims` tăng bất thường | App Check, giới hạn kích thước trong rules, giới hạn số người nhận mỗi link, thu hồi |
| Trình duyệt trong app chat chặn lưu trữ | Đăng nhập ẩn danh lỗi trong trình duyệt nhúng | Chế độ chỉ xem không cần đăng nhập; mời mở bằng trình duyệt ngoài để nhận món |
| Phần mỗi người trên web lệch với app | Test đối chiếu thất bại | Web không tự tính; chỉ hiện số app đã ghi |
| Chi phí đọc Firestore do nhiều người nghe trực tiếp | Số lượt đọc mỗi ngày tăng vọt | Ngừng nghe khi tab ẩn; khóa link sau khi chốt |

## Security

- Token do server sinh, 128 bit, không đoán được; không có đường nào liệt kê token.
- Bản chiếu không chứa `uid` của người tham gia, email hay số điện thoại; chỉ có tên như người trả đã nhập và cách trả mà người đó chọn công khai.
- Trang web không dùng dữ liệu người nhập để dựng HTML thô; mọi chuỗi đi qua cơ chế chèn văn bản an toàn.
- Có link báo lạm dụng ở chân trang.

## Next

Phase 9 đưa trang web vào bộ kiểm thử phát hành và vào chính sách quyền riêng tư.
