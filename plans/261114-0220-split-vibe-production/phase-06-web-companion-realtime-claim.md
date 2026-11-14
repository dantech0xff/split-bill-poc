---
title: "Phase 6: Trang web cho người nhận và tự nhận món trực tiếp"
status: todo
phase: 6
priority: P1
effort: "10d"
dependencies: [5]
---

# Phase 6: Trang web cho người nhận và tự nhận món trực tiếp

## Overview

Người nhận yêu cầu mở một link và thấy phần của mình cùng cách trả, không cần cài app. Cả bàn có thể mở cùng một link (hoặc app) và mỗi người tự chạm vào món mình ăn; mọi người thấy cập nhật trực tiếp, rồi người trả tiền chốt cách chia.

Tính năng v1.0 thuộc giai đoạn này: trang web trong "thẻ chia sẻ và trang web"; mỗi người tự nhận món.

## Context

- Đây là bề mặt mới hoàn toàn: repo chưa có mã web nào và chưa có dữ liệu công khai nào.
- Đây cũng là bề mặt đầu tiên mà người **chưa đăng nhập bằng tài khoản thật** được đọc và ghi dữ liệu, nên phần bảo mật là trọng tâm của giai đoạn.
- Phase 4 đã cho mỗi món một `itemId` ổn định; Phase 5 đã có yêu cầu trả tiền mang bản chụp của một cách nhận tiền.
- Dữ kiện: [báo cáo backend](../reports/researcher-261114-0209-product-backend-and-payment-links.md), mục 4.2 (App Check) và 4.5 (rules).

## Key Insights

- Rules của Firestore không lọc được theo tham số trên URL, nên dùng mẫu "đường dẫn là chìa khóa": tài liệu công khai nằm ở `publicSplits/{token}`, cho phép đọc **một** tài liệu theo đúng đường dẫn và cấm liệt kê.
- Rules không đếm được số tài liệu trong một collection con và không bảo đảm được token do server sinh nếu client được tạo tài liệu. Vì vậy **mọi lần ghi** vào bản chiếu và vào danh sách nhận món đều đi qua hàm trên server; client chỉ đọc.
- Chính sách TTL của Firestore xóa trễ (có thể hơn một ngày) và không xóa collection con. Vì vậy hạn dùng phải được kiểm ngay trong rules, và việc dọn dẹp do một hàm theo lịch làm.
- Tài liệu công khai chỉ là một **bản chiếu** của hóa đơn: đúng những gì người nhận cần thấy. Hóa đơn gốc trong `bills` không bao giờ mở cho người lạ.
- Trang web không có mã tính tiền của Kotlin, nên phần mỗi người do app của người chốt tính rồi gửi lên. Trang web chỉ hiển thị.

## Requirements

Chức năng:
- **Xem:** trang `…/s/{token}` hiện hóa đơn, phần của từng người, cách trả của người đòi (link, QR hoặc thông tin), bằng ngôn ngữ của trình duyệt nếu có, mặc định tiếng Anh.
- **Tự nhận món:** người mở link nhập tên, chạm vào các món của mình; một món có thể có nhiều người nhận; danh sách cập nhật trực tiếp cho mọi người đang mở, trên web lẫn trong app.
- **Chốt:** người trả tiền xem ai đã nhận gì, ghép từng người nhận với một người tham gia của hóa đơn, sửa nếu cần, rồi chốt. Việc nhận món khóa lại và trang hiện phần phải trả của từng người.
- **Thu hồi:** người tạo link tắt được link bất cứ lúc nào; link hết hiệu lực đúng thời điểm hết hạn.

Phi chức năng:
- Trang dùng cùng token màu, chữ và hình khối với app.
- Công cụ tìm kiếm không lập chỉ mục các trang chia sẻ.
- <!-- Red Team: cổng phát hành vượt yêu cầu — 2026-11-14 --> Mục tiêu (đo và báo cáo, không chặn phát hành): điểm hiệu năng và tiếp cận của Lighthouse ở chế độ di động từ 90 trở lên; gói tải ban đầu dưới 150 KB đã nén.

## Architecture

```
web/  (ứng dụng một trang tĩnh, TypeScript, Firebase JS SDK)
  /s/{token}        xem bản chiếu, tự nhận món, xem phần phải trả
Firebase Hosting ── phục vụ web/ với tiêu đề bảo mật, kèm tệp xác minh App Links cho link mời nhóm
Hàm trên server (callable)
  createShare, revokeShare      chủ hóa đơn gọi từ app
  claimItems                    người mở link gọi từ web hoặc app
  lockShare, finalizeShare      chủ hóa đơn gọi từ app khi chốt
Firestore
  publicSplits/{token}                 bản chiếu, chỉ server ghi, ai có đường dẫn và còn hạn thì đọc được
  publicSplits/{token}/claims/{cid}    món đã nhận của một người, chỉ server ghi
  shareTokens/{token}                  ánh xạ riêng tư sang chủ và hóa đơn, chỉ server đọc và ghi
```

<!-- Red Team: bản chiếu công khai không thực thi được các cam kết của chính nó — 2026-11-14 -->
Bản chiếu `publicSplits/{token}` gồm: tên quán, ngày, tiền tệ, các món (kèm `itemId`) và giá, phụ phí, tổng, tên người tham gia như đã nhập, phần mỗi người (sau khi chốt), bản chụp cách trả của người đòi, `billRevision`, `status` (`open`, `locking`, `locked`, `revoked`), `expiresAt`. Bản chiếu **không** chứa `uid` nào; ai là chủ nằm trong `shareTokens/{token}`.

Luật truy cập:
- `get` được phép khi `expiresAt` còn sau thời điểm yêu cầu và `status` khác `revoked`. `list` bị cấm. Client không tạo, sửa hay xóa được gì dưới `publicSplits`.
- `claimItems` kiểm: link đang `open`, tên dài tối đa 40 ký tự, `itemId` có trong bản chiếu, mỗi link tối đa 30 người nhận, mỗi tài khoản một tài liệu nhận món, có giới hạn tần suất.
- App Check bắt buộc cho các hàm này; token của trang web (reCAPTCHA Enterprise) chỉ được nhận ở các hàm của giai đoạn này, không gọi được hàm AI của Phase 3.

Giữ bản chiếu khớp với hóa đơn:
- Món được tham chiếu bằng `itemId`, không bằng thứ tự.
- Khi chủ sửa hóa đơn lúc link đang `open`, app gọi `createShare` lại để server chiếu lại với `billRevision` mới; các lượt nhận trỏ tới món đã bị xóa thì bị bỏ, và trang báo "hóa đơn vừa được cập nhật".

Chốt hai bước, để không lượt nhận nào bị sót:
1. `lockShare` đặt `status` thành `locking` (từ lúc này `claimItems` bị từ chối) rồi trả về toàn bộ lượt nhận đọc từ server.
2. App ghép người nhận với người tham gia, tính phần mỗi người bằng `:core:domain`, rồi gọi `finalizeShare`. Hàm này ghi phần mỗi người vào bản chiếu, đặt `locked`, và ghi cách chia cuối cùng vào hóa đơn gốc, để hai nơi không lệch nhau.

Dọn dẹp: một hàm theo lịch xóa bản chiếu đã hết hạn hoặc bị thu hồi cùng toàn bộ `claims` của nó và mục tương ứng trong `shareTokens`.

Khung web: ứng dụng một trang tĩnh bằng TypeScript với một thư viện giao diện nhỏ, chốt cụ thể ở bước rà lại đầu giai đoạn. Điều kiện chọn: không cần server dựng trang.

## Files

Tạo mới:
- `web/` (`package.json`, `index.html`, `src/main.ts`, `src/split-view.ts`, `src/claim.ts`, `src/tokens.css`, `tests/`)
- `functions/src/share.ts` (`createShare`, `revokeShare`, `claimItems`, `lockShare`, `finalizeShare`), `functions/src/share-cleanup.ts`
- `app/src/main/java/com/dantech/splitvibe/data/share/PublicSplitRepository.kt`
- `app/src/main/java/com/dantech/splitvibe/ui/claim/` (màn hình theo dõi nhận món và chốt)
- `firestore-tests/public-splits.test.ts`

Sửa:
- `firebase.json` (Hosting, đường dẫn `/s/**`, tiêu đề cấm lập chỉ mục, `Referrer-Policy: no-referrer`, Content-Security-Policy chặt), `firestore.rules`
- `functions/src/analyze-bill.ts` và `quota.ts` (danh sách app được phép gọi hàm AI không gồm app web)
- `functions/src/delete-account.ts` (thu hồi mọi link của người đó, xóa lượt nhận món của họ)
- `core/designsystem` (xuất token ra `web/src/tokens.css`)
- `app/src/main/java/com/dantech/splitvibe/ui/settle/` (thêm link web vào yêu cầu trả tiền)

## Implementation Steps

1. Viết test rules và test hàm trước: đọc theo đường dẫn, cấm liệt kê, hết hạn, thu hồi, client không ghi được, giới hạn của `claimItems`, khóa hai bước.
2. Viết `createShare` (token 128 bit ngẫu nhiên do server sinh) và `revokeShare`; `PublicSplitRepository` trong app.
3. Dựng trang xem trên `web/` với token thiết kế xuất từ app và các tiêu đề bảo mật; triển khai lên Hosting của project dev.
4. Thêm đăng nhập ẩn danh và App Check trên web; viết `claimItems`; dựng chế độ tự nhận món với trình nghe trực tiếp.
5. Màn hình trong app: theo dõi ai nhận gì, ghép người, sửa, chốt bằng `lockShare` rồi `finalizeShare`.
6. Chiếu lại khi hóa đơn bị sửa; hàm dọn dẹp theo lịch; trang báo cho link không còn hiệu lực.
7. Mở rộng `delete-account.ts`.
8. Test đầu-cuối bằng trình duyệt tự động ở khung nhìn điện thoại; đo Lighthouse và kích thước gói.

## Todo

- [ ] Test rules và test hàm cho `publicSplits` và `claims`
- [ ] `createShare`, `revokeShare`, ánh xạ riêng tư `shareTokens`
- [ ] Trang xem trên Hosting với token thiết kế và tiêu đề bảo mật
- [ ] Đăng nhập ẩn danh và App Check trên web; `claimItems` có giới hạn
- [ ] Tự nhận món trực tiếp trên web và trong app
- [ ] Chốt hai bước, ghi cách chia cuối vào hóa đơn gốc
- [ ] Chiếu lại khi hóa đơn bị sửa
- [ ] Dọn dẹp theo lịch; trang link hết hiệu lực
- [ ] Xóa tài khoản thu hồi link và xóa lượt nhận
- [ ] Test đầu-cuối trên trình duyệt; số đo Lighthouse và kích thước gói

## Success Criteria

- Test rules qua: liệt kê `publicSplits` bị từ chối; đọc đúng đường dẫn được phép khi còn hạn; link **đã hết hạn** và link đã thu hồi đều không đọc được, dù tài liệu chưa bị xóa; client không tạo, sửa, xóa được bản chiếu hay lượt nhận.
- Test hàm qua: `claimItems` từ chối tên quá dài, `itemId` lạ, người nhận thứ 31, và mọi yêu cầu sau `lockShare`.
- Một lượt nhận gửi ngay trước `lockShare` có mặt trong kết quả `lockShare` trả về; một lượt gửi ngay sau bị từ chối (test chạy hai việc song song).
- Hai trình duyệt và một máy Android mở cùng link: một bên chạm nhận món thì hai bên kia thấy trong vòng 2 giây.
- Sau khi chốt, phần mỗi người trên trang web, trong hóa đơn gốc và kết quả của `SplitCalculator` trên cùng fixture bằng nhau (test đối chiếu tự động).
- Chủ xóa một món khi link đang mở: các lượt nhận món đó biến mất, các lượt nhận món khác vẫn trỏ đúng món.
- Bản chiếu không chứa trường `uid` nào; hóa đơn gốc trong `bills` không đọc được bằng tài khoản ẩn danh của trang web.
- Token App Check của trang web gọi `analyzeBill` thì bị từ chối.
- Trang trả tiêu đề cấm lập chỉ mục, `Referrer-Policy: no-referrer` và Content-Security-Policy; link thanh toán mở trong thẻ mới không kèm nguồn giới thiệu.
- Sau khi hàm dọn dẹp chạy, bản chiếu hết hạn không còn tài liệu `claims` nào.
- Báo cáo có số đo Lighthouse và kích thước gói so với mục tiêu.

## Risks

| Rủi ro | Dấu hiệu nhận biết | Phản ứng đã định |
|---|---|---|
| Link bị chuyển tiếp cho người ngoài | Xuất hiện người nhận món lạ | Người trả thấy danh sách, xóa được từng người và thu hồi link; tên người nhận được ghi rõ là tự nhập |
| Phá hoại bằng cách gửi nhiều lượt nhận | Số lần `claimItems` bị từ chối tăng vọt | Giới hạn 30 người mỗi link, giới hạn tần suất, App Check, thu hồi |
| Trình duyệt trong app chat chặn lưu trữ | Đăng nhập ẩn danh lỗi trong trình duyệt nhúng | Chế độ chỉ xem không cần đăng nhập; mời mở bằng trình duyệt ngoài để nhận món |
| Chi phí do nhiều người nghe trực tiếp và nhiều lần gọi hàm | Số lượt đọc và số lần gọi mỗi ngày tăng vọt | Ngừng nghe khi tab ẩn; khóa link sau khi chốt; `maxInstances` |
| Mạo danh bằng cách nhập tên người khác | Phản hồi của người dùng | Lượt nhận không tự thành cách chia; người trả ghép và duyệt khi chốt |

## Security

- Token do server sinh, 128 bit, không đoán được; không có đường nào liệt kê token.
- Bản chiếu không chứa `uid`, email hay số điện thoại; chỉ có tên như người trả đã nhập và bản chụp của một cách trả mà người đó chọn công khai.
- Trang web không dùng dữ liệu người nhập để dựng HTML thô; mọi chuỗi đi qua cơ chế chèn văn bản an toàn. Link thanh toán chỉ là `https:` đã qua kiểm tra ở Phase 5.
- Đường dẫn có token không lọt sang trang thanh toán nhờ `Referrer-Policy: no-referrer`.
- Có link báo lạm dụng ở chân trang.

## Next

Phase 9 đưa trang web vào bộ kiểm thử phát hành và vào chính sách quyền riêng tư.
