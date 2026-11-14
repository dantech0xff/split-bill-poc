---
title: "Phase 5: Nhóm, số dư và thu tiền không giữ tiền"
status: todo
phase: 5
priority: P1
effort: "12d"
dependencies: [4]
---

# Phase 5: Nhóm, số dư và thu tiền không giữ tiền

## Overview

Người dùng lập nhóm (bạn cùng nhà, chuyến đi), thấy số dư cộng dồn qua nhiều hóa đơn và cách trả ít lần chuyển nhất. Khi đòi tiền, mỗi người nhận một yêu cầu kèm link hoặc mã QR đã điền sẵn số tiền theo cách nhận tiền của người đòi, có ảnh tóm tắt đẹp để gửi vào nhóm chat. App theo dõi ai đã trả và nhắc người chưa trả. App không bao giờ giữ hay chuyển tiền.

Tính năng v1.0 thuộc giai đoạn này: nhóm, số dư, tối giản nợ; link và QR trả tiền điền sẵn; thẻ chia sẻ dạng ảnh; theo dõi đã trả và nhắc nợ.

## Context

- Hiện tại "gửi yêu cầu" chỉ là màn hình minh họa: `app/src/main/java/com/dantech/splitvibe/ui/split/SplitBillViewModel.kt:164` chỉ đổi trạng thái trên máy, không gửi gì đi.
- Chưa có khái niệm nhóm hay số dư; mỗi hóa đơn đứng riêng.
- Dữ kiện: [báo cáo backend](../reports/researcher-261114-0209-product-backend-and-payment-links.md), mục 1 (đối thủ) và mục 2 (các đường thanh toán).

## Key Insights

- Không có đường thanh toán nào cho bên thứ ba điền sẵn ở mọi nơi. Chia làm ba nhóm:
  - **Có định dạng công bố, app tự sinh được:** PayPal.Me (số tiền và tiền tệ trong link), UPI (`upi://pay`, chỉ dạng người trả chủ động vì Ấn Độ đã cấm yêu cầu thu giữa cá nhân từ 01/10/2025), PromptPay và VietQR (mã QR theo chuẩn EMVCo), mã QR SEPA "BCD" (chỉ EUR).
  - **Định dạng do cộng đồng ghi lại `[UNVERIFIED]`:** Venmo và Cash App. Coi là "cố gắng tốt nhất", luôn kèm thông tin để người trả tự nhập.
  - **Không điền sẵn được:** Revolut, Wise (link do người dùng tạo trong app của họ) và Zelle (không có định dạng công khai). App cho dán link hoặc hiện thông tin kèm nút sao chép.
- Đặc tả chính thức của VietQR và PromptPay không công khai; bố cục trường lấy từ các bản cài đặt mã nguồn mở. Vì vậy mã QR phải được quét thử bằng app ngân hàng thật trước khi phát hành.
- Đối thủ chính giới hạn số khoản chi mỗi ngày ở bản miễn phí, và đó là nguồn phàn nàn lớn nhất. Split Vibe không giới hạn việc nhập liệu; chỉ đo lượt AI.
- Thông báo đẩy chỉ tới được người có cài app. Với người chưa cài, lời nhắc đi qua chính người đòi: app nhắc họ và chuẩn bị sẵn tin nhắn để chia sẻ.

## Requirements

Chức năng:
- **Nhóm:** tạo nhóm, mời bằng link, thành viên là người dùng app hoặc "chỗ giữ tên" cho người chưa cài; người mới vào nhận lại chỗ giữ tên của mình.
- **Số dư:** cộng dồn theo nhóm, quy về tiền tệ của nhóm bằng tỷ giá đã chụp trong từng hóa đơn; gợi ý cách trả tối giản.
- **Cách nhận tiền:** hồ sơ lưu một hoặc nhiều cách; mỗi cách sinh ra link, mã QR, hoặc thông tin để sao chép.
- **Yêu cầu trả tiền:** mỗi người một tin gồm số tiền, bảng kê ngắn, link hoặc QR; gửi qua bảng chia sẻ của Android.
- **Thẻ chia sẻ:** ảnh tóm tắt hóa đơn và phần mỗi người, dựng từ giao diện thật.
- **Theo dõi:** trạng thái chưa trả hoặc đã trả cho từng người; người đòi hoặc người trả đều đánh dấu được; ghi lại khoản thanh toán vào số dư nhóm.
- **Nhắc nợ:** thông báo đẩy cho người có app khi có yêu cầu mới; nhắc lại sau số ngày người dùng chọn; nhắc người đòi khi còn người chưa trả.

Phi chức năng:
- Tổng số dư của một nhóm luôn bằng 0.
- Sau khi áp các lần chuyển được gợi ý, mọi số dư về 0, và số lần chuyển không quá số thành viên trừ 1.
- App không lưu thông tin đăng nhập hay token của bất kỳ dịch vụ thanh toán nào.

## Architecture

`:core:domain`:
- `Balances`: tính số dư từ hóa đơn và các khoản đã thanh toán.
- `DebtSimplifier`: thuật toán tham lam ghép người nợ nhiều nhất với người được nợ nhiều nhất.
- `PaymentMethod` (kiểu đóng): `PayPalMe`, `Upi`, `PromptPay`, `VietQr`, `SepaQr`, `PastedLink`, `PlainDetails`.
- `PaymentRequestBuilder`: từ một cách nhận tiền, số tiền, tiền tệ và ghi chú, trả về link, nội dung QR, hoặc cả hai.
- `EmvQr`: bộ mã hóa TLV theo EMVCo kèm CRC-16, dùng chung cho VietQR và PromptPay.

Vết mở rộng (ranh giới `PaymentMethod`): biến thể kế tiếp có thật là một đường thanh toán mới (ví dụ QRIS của Indonesia). Phần giữ ổn định là `PaymentRequestBuilder` và giao diện chọn cách nhận tiền. Phần phải thêm là một kiểu con, hàm sinh và test của nó. Không dựng cơ chế plugin hay bảng đăng ký.

Dữ liệu:
- `groups/{gid}`: tên, tiền tệ, `memberUids`; `groups/{gid}/members/{mid}`: tên hiển thị, `uid` nếu có.
- `groups/{gid}/settlements/{sid}`: ai trả ai, bao nhiêu, khi nào, ai xác nhận.
- `bills/{billId}` thêm `groupId` và `requests` (trạng thái từng người).
- `users/{uid}` thêm `paymentMethods`; `users/{uid}/devices/{deviceId}`: token thông báo.

Server:
- Hàm kích hoạt khi có yêu cầu mới gửi thông báo đẩy cho thành viên có app.
- Hàm theo lịch gửi lời nhắc đến hạn.
- Link mời nhóm dạng `https://<tên miền>/g/{token}` mở app qua App Links.

## Files

Tạo mới:
- `core/domain/src/main/kotlin/.../Balances.kt`, `DebtSimplifier.kt`, `PaymentMethod.kt`, `PaymentRequestBuilder.kt`, `EmvQr.kt` cùng test
- `app/src/main/java/com/dantech/splitvibe/ui/groups/`, `ui/settle/`, `ui/profile/`
- `app/src/main/java/com/dantech/splitvibe/share/ShareCardRenderer.kt`
- `app/src/main/java/com/dantech/splitvibe/notifications/`
- `functions/src/notify-request.ts`, `functions/src/reminders.ts`, `functions/src/group-invite.ts`

Sửa:
- `app/src/main/java/com/dantech/splitvibe/ui/split/SplitBillViewModel.kt` (`sendRequests` tạo yêu cầu thật), `SentContent.kt`
- `app/src/main/AndroidManifest.xml` (quyền thông báo, App Links)
- `firestore.rules`, `firestore.indexes.json`

## Implementation Steps

1. Viết test thuộc tính cho `Balances` và `DebtSimplifier`, rồi cài đặt.
2. Viết `EmvQr` và các hàm sinh cho từng cách nhận tiền, dùng véc-tơ kiểm thử lấy từ các bản cài đặt mã nguồn mở nêu trong báo cáo.
3. Màn hình hồ sơ để khai cách nhận tiền; màn hình yêu cầu trả tiền với link và mã QR.
4. `ShareCardRenderer`: dựng thẻ từ thành phần của hệ thiết kế thành ảnh, chia sẻ qua `FileProvider`.
5. Nhóm: tạo, mời, vào nhóm, nhận chỗ giữ tên, màn hình số dư và gợi ý trả.
6. Theo dõi đã trả, ghi khoản thanh toán; thông báo đẩy và lời nhắc.
7. Quét thử từng loại mã QR và mở thử từng loại link bằng app thật theo ma trận kiểm tay; ghi kết quả vào `plans/reports/`.

## Todo

- [ ] `Balances` và `DebtSimplifier` với test thuộc tính
- [ ] `EmvQr` và hàm sinh cho PayPal.Me, UPI, PromptPay, VietQR, SEPA
- [ ] Hồ sơ cách nhận tiền, kể cả link dán và thông tin sao chép
- [ ] Yêu cầu trả tiền từng người qua bảng chia sẻ
- [ ] Thẻ chia sẻ dạng ảnh
- [ ] Nhóm, link mời, chỗ giữ tên, số dư, gợi ý trả tối giản
- [ ] Đánh dấu đã trả và ghi khoản thanh toán
- [ ] Thông báo đẩy và lời nhắc
- [ ] Ma trận kiểm tay với app thanh toán thật

## Success Criteria

- Test thuộc tính: với 2.000 nhóm ngẫu nhiên, tổng số dư bằng 0; sau khi áp các lần chuyển gợi ý mọi số dư bằng 0; số lần chuyển không quá số thành viên trừ 1.
- Test chuỗi mẫu: nội dung QR của VietQR, PromptPay và SEPA khớp từng ký tự với véc-tơ kiểm thử, kể cả CRC.
- Kiểm tay, ghi vào báo cáo: link PayPal.Me mở đúng số tiền và tiền tệ; mã VietQR quét được bằng ít nhất hai app ngân hàng Việt Nam với đúng số tiền và nội dung; mã SEPA và link UPI được một người thử ở khu vực tương ứng xác nhận, hoặc ghi rõ là chưa kiểm.
- Với Venmo, Cash App, Revolut, Wise, Zelle: yêu cầu luôn hiện thông tin đầy đủ để người trả tự nhập, dù link có mở được hay không.
- Một nhóm 4 người, 5 hóa đơn, 2 tiền tệ cho số dư khớp với bảng tính tay trong fixture.
- Người có app nhận thông báo đẩy trong vòng 1 phút sau khi có yêu cầu; lời nhắc tới đúng ngày đã chọn.
- Từ chối quyền thông báo không làm hỏng luồng nào.
- Ảnh chuẩn cho thẻ chia sẻ và các màn hình mới; `SplitFlowTest` mở rộng tới bước tạo yêu cầu.

## Risks

| Rủi ro | Dấu hiệu nhận biết | Phản ứng đã định |
|---|---|---|
| Mã QR sai chuẩn nên app ngân hàng không đọc | Kiểm tay thất bại | Không phát hành cách nhận tiền đó cho tới khi quét được; hiện thông tin để sao chép thay thế |
| Dịch vụ đổi định dạng link | Người dùng báo link không mở | Mỗi yêu cầu luôn kèm thông tin dạng chữ; cập nhật hàm sinh qua bản vá |
| Thành viên nhóm thấy dữ liệu không thuộc về mình | Test rules thất bại | Rules kiểm tư cách thành viên trên mọi đường dẫn con của nhóm |
| Lời nhắc bị coi là làm phiền | Tỷ lệ tắt thông báo cao | Mặc định chỉ nhắc một lần; người dùng tự chọn tần suất |
| Người chưa cài app không nhận được gì | Phản hồi của người dùng | Luồng chính là tin nhắn chia sẻ; trang web ở Phase 6 bổ sung |

## Security

- Link mời nhóm chứa token ngẫu nhiên 128 bit, thu hồi được, có hạn dùng.
- Cách nhận tiền chỉ hiện cho những người nhận yêu cầu từ chính người đó.
- Hàm gửi thông báo chỉ chạy từ trigger của server; client không gửi thông báo trực tiếp cho người khác.
- Nội dung ghi chú trong link và QR được lọc theo quy định của từng đường thanh toán (độ dài, ký tự).

## Next

Phase 6 đưa yêu cầu trả tiền lên trang web để người chưa cài app xem và tự nhận món.
