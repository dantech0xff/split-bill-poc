---
title: "Phase 5: Nhóm, số dư và thu tiền không giữ tiền"
status: todo
phase: 5
priority: P1
effort: "14d"
dependencies: [4]
---

# Phase 5: Nhóm, số dư và thu tiền không giữ tiền

## Overview

Người dùng lập nhóm (bạn cùng nhà, chuyến đi), thấy số dư cộng dồn qua nhiều hóa đơn và cách trả ít lần chuyển nhất. Khi đòi tiền, mỗi người nhận một yêu cầu kèm link hoặc mã QR đã điền sẵn số tiền theo cách nhận tiền của người đòi, có ảnh tóm tắt đẹp để gửi vào nhóm chat. App theo dõi ai đã trả và nhắc người chưa trả. App không bao giờ giữ hay chuyển tiền.

Tính năng v1.0 thuộc giai đoạn này: nhóm, số dư, tối giản nợ; link và QR trả tiền điền sẵn; thẻ chia sẻ dạng ảnh; theo dõi đã trả và nhắc nợ.

## Context

- Hiện tại "gửi yêu cầu" chỉ là màn hình minh họa: `app/src/main/java/com/dantech/splitvibe/ui/split/SplitBillViewModel.kt:164` chỉ đổi trạng thái trên máy, không gửi gì đi.
- Chưa có khái niệm nhóm hay số dư; mỗi hóa đơn đứng riêng.
- Phase 4 đã cho mỗi hóa đơn danh sách `participants` với mã cục bộ và phần mỗi người đã lưu bằng tiền của hóa đơn.
- Dữ kiện: [báo cáo backend](../reports/researcher-261114-0209-product-backend-and-payment-links.md), mục 1 (đối thủ) và mục 2 (các đường thanh toán).

## Key Insights

- Không có đường thanh toán nào cho bên thứ ba điền sẵn ở mọi nơi. Chia làm ba nhóm:
  - **Có định dạng công bố, app tự sinh được:** PayPal.Me (số tiền và tiền tệ trong link), UPI (`upi://pay`, chỉ dạng người trả chủ động vì Ấn Độ đã cấm yêu cầu thu giữa cá nhân từ 01/10/2025), PromptPay và VietQR (mã QR theo chuẩn EMVCo), mã QR SEPA "BCD" (chỉ EUR).
  - **Định dạng do cộng đồng ghi lại `[UNVERIFIED]`:** Venmo và Cash App. Coi là "cố gắng tốt nhất", luôn kèm thông tin để người trả tự nhập.
  - **Không điền sẵn được:** Revolut, Wise (link do người dùng tạo trong app của họ) và Zelle (không có định dạng công khai). App cho dán link hoặc hiện thông tin kèm nút sao chép.
- Đặc tả chính thức của VietQR và PromptPay không công khai; bố cục trường lấy từ các bản cài đặt mã nguồn mở. Vì vậy mã QR phải được quét thử bằng app ngân hàng thật trước khi phát hành.
- App không giữ tiền nhưng **dẫn đường cho tiền**: nếu ai đó thay được cách nhận tiền hiển thị trên một yêu cầu, tiền thật sẽ đi sai chỗ. Vì vậy sổ nợ, tư cách thành viên và cách nhận tiền phải được bảo vệ như dữ liệu tài chính.
- Đối thủ chính giới hạn số khoản chi mỗi ngày ở bản miễn phí, và đó là nguồn phàn nàn lớn nhất. Split Vibe không giới hạn việc nhập liệu; chỉ đo lượt AI.
- Thông báo đẩy chỉ tới được người có cài app. Với người chưa cài, lời nhắc đi qua chính người đòi: app nhắc họ và chuẩn bị sẵn tin nhắn để chia sẻ.

## Requirements

Chức năng:
- **Nhóm:** tạo nhóm, mời bằng link, thành viên là người dùng app hoặc "chỗ giữ tên" cho người chưa cài. Người mới vào xin nhận một chỗ giữ tên; người tạo nhóm duyệt.
- **Số dư:** cộng dồn theo nhóm bằng tiền của nhóm; gợi ý cách trả tối giản.
- **Cách nhận tiền:** hồ sơ lưu một hoặc nhiều cách; mỗi cách sinh ra link, mã QR, hoặc thông tin để sao chép.
- **Yêu cầu trả tiền:** mỗi người một tin gồm số tiền, bảng kê ngắn, link hoặc QR; gửi qua bảng chia sẻ của Android.
- **Thẻ chia sẻ:** ảnh tóm tắt hóa đơn và phần mỗi người, dựng từ giao diện thật.
- **Theo dõi:** người trả bấm "tôi đã trả" thì yêu cầu chuyển sang "báo đã trả"; chỉ khi người đòi xác nhận thì số dư mới đổi.
- **Nhắc nợ:** thông báo đẩy cho người có app khi có yêu cầu mới; nhắc lại sau số ngày người dùng chọn; nhắc người đòi khi còn người chưa trả.

Phi chức năng:
- Tổng số dư của một nhóm luôn bằng 0, kể cả khi nhóm có hóa đơn nhiều tiền tệ.
- Sau khi áp các lần chuyển được gợi ý, mọi số dư về 0, và số lần chuyển không quá số thành viên trừ 1.
- App không lưu thông tin đăng nhập hay token của bất kỳ dịch vụ thanh toán nào.

## Architecture

<!-- Red Team: không có định danh người thống nhất — 2026-11-14 -->
Định danh trong nhóm:
- Thành viên là `groups/{gid}/members/{mid}`; `mid` không bao giờ đổi. Chỗ giữ tên là một thành viên chưa có `uid`. Khi một người nhận chỗ giữ tên, chỉ trường `uid` của thành viên đó được gán, nên không hóa đơn nào phải sửa.
- Trong hóa đơn của nhóm, mọi người tham gia đều phải mang `memberId`. Số dư được tính theo `memberId`, không theo tên hay danh sách người gần đây.
- Đưa một hóa đơn lẻ vào nhóm là một bước rõ ràng: người dùng ghép từng người tham gia với một thành viên, rồi app ghi một lần.

<!-- Red Team: tỷ giá không khớp tiền của nhóm, làm tròn phá bất biến — 2026-11-14 -->
Tiền tệ của nhóm:
- Khi hóa đơn được đưa vào nhóm, app chụp tỷ giá từ tiền của hóa đơn sang tiền của nhóm (lấy từ `fxRates` của ngày hóa đơn, người dùng sửa được) và lưu vào hóa đơn.
- Quy đổi **tổng hóa đơn một lần**, rồi chia số đã quy đổi theo tỷ lệ phần mỗi người bằng quy tắc số dư lớn nhất. Cách này bảo đảm các phần đã quy đổi cộng lại đúng bằng tổng đã quy đổi.
- Số dư = tổng các phần đã quy đổi trừ tổng các khoản thanh toán đã xác nhận. Sửa hóa đơn hay tỷ giá sau khi đã thanh toán không sửa các khoản thanh toán; phần chênh hiện ra thành số dư mới và được ghi vào nhật ký nhóm.

<!-- Red Team: sổ nợ giả mạo được, client tự thêm thành viên — 2026-11-14 -->
Sổ nợ và tư cách thành viên:
- Trạng thái trả tiền nằm ở `bills/{billId}/requests/{participantId}`, không nằm trong tài liệu hóa đơn. Người trả chỉ ghi được `reportedPaidAt` trên yêu cầu của chính mình; chỉ người đòi ghi được `confirmedPaidAt`.
- `groups/{gid}/settlements/{sid}` chỉ thêm, không sửa, không xóa; được tạo khi người đòi xác nhận.
- `memberUids` của nhóm và của hóa đơn chỉ đổi qua hàm trên server: `joinGroup` (đổi link mời), `claimPlaceholder` (cần người tạo nhóm duyệt), `acceptRequest` (người nhận mở link yêu cầu trong app).

`:core:domain`:
- `Balances`: tính số dư theo `memberId` từ phần đã lưu của các hóa đơn và các khoản thanh toán đã xác nhận.
- `DebtSimplifier`: thuật toán tham lam ghép người nợ nhiều nhất với người được nợ nhiều nhất.
- `GroupConversion`: quy đổi một hóa đơn sang tiền của nhóm theo cách nêu trên.
- `PaymentMethod` (kiểu đóng): `PayPalMe`, `Upi`, `PromptPay`, `VietQr`, `SepaQr`, `PastedLink`, `PlainDetails`.
- `PaymentRequestBuilder`: từ một cách nhận tiền, số tiền, tiền tệ và ghi chú, trả về link, nội dung QR, hoặc cả hai.
- `EmvQr`: bộ mã hóa TLV theo EMVCo kèm CRC-16, dùng chung cho VietQR và PromptPay.

Vết mở rộng (ranh giới `PaymentMethod`): biến thể kế tiếp có thật là một đường thanh toán mới (ví dụ QRIS của Indonesia). Phần giữ ổn định là `PaymentRequestBuilder` và giao diện chọn cách nhận tiền. Phần phải thêm là một kiểu con, hàm sinh và test của nó. Không dựng cơ chế plugin hay bảng đăng ký.

<!-- Red Team: thông tin nhận tiền bị lộ, link dán là kênh chèn mã — 2026-11-14 -->
Dữ liệu:
- `groups/{gid}`: tên, tiền tệ, `memberUids`, người tạo; `groups/{gid}/members/{mid}`: tên hiển thị, `uid` nếu có.
- `groups/{gid}/settlements/{sid}`: ai trả ai, bao nhiêu, khi nào, ai xác nhận.
- `bills/{billId}` thêm `groupId` và tỷ giá sang tiền nhóm; `bills/{billId}/requests/{participantId}`: số tiền, **bản chụp của đúng một cách nhận tiền** người đòi đã chọn cho yêu cầu đó, `reportedPaidAt`, `confirmedPaidAt`.
- `users/{uid}/paymentMethods/{methodId}`: chỉ chủ tài khoản đọc và ghi. Người khác chỉ thấy bản chụp trong yêu cầu gửi cho họ.
- `users/{uid}/devices/{deviceId}`: token thông báo.

Quy tắc cho `PastedLink`: chỉ nhận địa chỉ `https:` có tên miền nằm trong danh sách các dịch vụ thanh toán do server cấu hình; app hiện rõ tên miền và hỏi lại trước khi mở.

Server:
- Hàm kích hoạt khi có yêu cầu mới gửi thông báo đẩy. Chỉ gửi cho người đã ở chung một nhóm với người gửi hoặc đã chấp nhận yêu cầu. Nội dung thông báo dựng từ mẫu cố định, không chèn tên quán hay link do người dùng nhập. Có giới hạn số yêu cầu mỗi người gửi trong một giờ.
- Hàm theo lịch gửi lời nhắc đến hạn.
- Link mời nhóm dạng `https://<tên miền>/g/{token}` mở app qua App Links.

## Files

Tạo mới:
- Trong `core/domain/`: `Balances.kt`, `DebtSimplifier.kt`, `GroupConversion.kt`, `PaymentMethod.kt`, `PaymentRequestBuilder.kt`, `EmvQr.kt` cùng test
- `app/src/main/java/com/dantech/splitvibe/ui/groups/`, `ui/settle/`, `ui/profile/`
- `app/src/main/java/com/dantech/splitvibe/share/ShareCardRenderer.kt`
- `app/src/main/java/com/dantech/splitvibe/notifications/`
- `functions/src/notify-request.ts`, `functions/src/reminders.ts`, `functions/src/group-membership.ts` (`joinGroup`, `claimPlaceholder`, `acceptRequest`)

Sửa:
- `app/src/main/java/com/dantech/splitvibe/ui/split/SplitBillViewModel.kt` (`sendRequests` tạo yêu cầu thật), `SentContent.kt`
- `app/src/main/AndroidManifest.xml` (quyền thông báo, App Links)
- `functions/src/delete-account.ts` (xem "Xóa tài khoản" bên dưới), `firestore.rules`, `firestore.indexes.json`

<!-- Red Team: xóa tài khoản không mở rộng theo dữ liệu mới — 2026-11-14 -->
Xóa tài khoản đối với dữ liệu chung:
- Thành viên bị xóa trở lại thành chỗ giữ tên: giữ `mid`, tên hiển thị thành "Thành viên đã rời", bỏ `uid`. Số tiền trong hóa đơn và các khoản thanh toán được giữ nguyên để số dư của người khác không đổi.
- Hóa đơn nhóm do người đó tạo được chuyển quyền sở hữu cho người tạo nhóm, hoặc thành viên lâu nhất còn lại.
- Xóa `paymentMethods`, `devices`, các bản chụp cách nhận tiền trong những yêu cầu chưa xác nhận, và mọi collection con của `users/{uid}`.

## Implementation Steps

1. Viết test thuộc tính cho `Balances`, `GroupConversion` và `DebtSimplifier` (có nhóm nhiều tiền tệ, hóa đơn do nhiều thành viên tạo, và sửa hóa đơn sau khi đã thanh toán), rồi cài đặt.
2. Viết `EmvQr` và các hàm sinh cho từng cách nhận tiền, dùng véc-tơ kiểm thử lấy từ các bản cài đặt mã nguồn mở nêu trong báo cáo.
3. Màn hình hồ sơ để khai cách nhận tiền (kể cả kiểm tra `PastedLink`); màn hình yêu cầu trả tiền với link và mã QR.
4. `ShareCardRenderer`: dựng thẻ từ thành phần của hệ thiết kế thành ảnh, chia sẻ qua `FileProvider`.
5. Nhóm: các hàm `joinGroup`, `claimPlaceholder`, `acceptRequest` và rules; màn hình tạo, mời, duyệt, số dư và gợi ý trả.
6. Yêu cầu trả tiền: báo đã trả, xác nhận, ghi khoản thanh toán; thông báo đẩy theo mẫu và lời nhắc.
7. Mở rộng `delete-account.ts` theo chính sách ở trên, kèm test trên emulator.
8. Quét thử từng loại mã QR và mở thử từng loại link bằng app thật theo ma trận kiểm tay; ghi kết quả vào `plans/reports/`.

## Todo

- [ ] `Balances`, `GroupConversion`, `DebtSimplifier` với test thuộc tính nhiều tiền tệ
- [ ] `EmvQr` và hàm sinh cho PayPal.Me, UPI, PromptPay, VietQR, SEPA
- [ ] Hồ sơ cách nhận tiền; kiểm tra `PastedLink`
- [ ] Yêu cầu trả tiền từng người qua bảng chia sẻ, kèm bản chụp một cách nhận tiền
- [ ] Thẻ chia sẻ dạng ảnh
- [ ] Nhóm: `joinGroup`, `claimPlaceholder` có duyệt, `acceptRequest`, rules
- [ ] Báo đã trả, xác nhận, khoản thanh toán chỉ thêm
- [ ] Thông báo đẩy theo mẫu, giới hạn tần suất, lời nhắc
- [ ] Xóa tài khoản với dữ liệu nhóm
- [ ] Ma trận kiểm tay với app thanh toán thật

## Success Criteria

- Test thuộc tính với 2.000 nhóm ngẫu nhiên, trong đó có nhóm nhiều tiền tệ và hóa đơn do nhiều thành viên tạo: tổng số dư bằng 0; sau khi áp các lần chuyển gợi ý mọi số dư bằng 0; số lần chuyển không quá số thành viên trừ 1.
- Một nhóm 4 người dùng THB, 5 hóa đơn bằng 3 tiền tệ do 2 thành viên khác nhau tạo, cho số dư khớp bảng tính tay trong fixture; sửa một hóa đơn sau khi đã thanh toán làm lộ đúng phần chênh.
- Test chuỗi mẫu: nội dung QR của VietQR, PromptPay và SEPA khớp từng ký tự với véc-tơ kiểm thử, kể cả CRC.
- Test rules và hàm:
  - người trả không ghi được `confirmedPaidAt`, không sửa được yêu cầu của người khác;
  - `settlements` không sửa và không xóa được;
  - client không đổi được `memberUids`;
  - nhận chỗ giữ tên khi chưa được duyệt bị từ chối;
  - người ngoài không đọc được `paymentMethods`.
- `PastedLink` có dạng `javascript:`, `intent:`, `http:` hoặc tên miền ngoài danh sách bị từ chối.
- Người không ở chung nhóm và chưa chấp nhận yêu cầu thì không nhận thông báo; vượt giới hạn tần suất thì yêu cầu bị từ chối.
- Kiểm tay, ghi vào báo cáo: link PayPal.Me mở đúng số tiền và tiền tệ; mã VietQR quét được bằng ít nhất hai app ngân hàng Việt Nam với đúng số tiền và nội dung; mã SEPA và link UPI được một người thử ở khu vực tương ứng xác nhận, hoặc ghi rõ là chưa kiểm.
- Với Venmo, Cash App, Revolut, Wise, Zelle: yêu cầu luôn hiện thông tin đầy đủ để người trả tự nhập, dù link có mở được hay không.
- Sau khi một thành viên xóa tài khoản, số dư của các thành viên còn lại không đổi (test trên emulator).
- Người có app nhận thông báo đẩy trong vòng 1 phút sau khi có yêu cầu; từ chối quyền thông báo không làm hỏng luồng nào.
- Ảnh chuẩn cho thẻ chia sẻ và các màn hình mới; `SplitFlowTest` mở rộng tới bước tạo yêu cầu.

## Risks

| Rủi ro | Dấu hiệu nhận biết | Phản ứng đã định |
|---|---|---|
| Mã QR sai chuẩn nên app ngân hàng không đọc | Kiểm tay thất bại | Không phát hành cách nhận tiền đó cho tới khi quét được; hiện thông tin để sao chép thay thế |
| Dịch vụ đổi định dạng link | Người dùng báo link không mở | Mỗi yêu cầu luôn kèm thông tin dạng chữ; cập nhật hàm sinh qua bản vá |
| Người đòi không bao giờ xác nhận nên số dư treo | Yêu cầu ở trạng thái "báo đã trả" quá lâu | Nhắc người đòi; hiện rõ trạng thái cho cả hai bên |
| Lời nhắc bị coi là làm phiền | Tỷ lệ tắt thông báo cao | Mặc định chỉ nhắc một lần; người dùng tự chọn tần suất |
| Người chưa cài app không nhận được gì | Phản hồi của người dùng | Luồng chính là tin nhắn chia sẻ; trang web ở Phase 6 bổ sung |

## Security

- Link mời nhóm chứa token ngẫu nhiên 128 bit do server sinh, thu hồi được, có hạn dùng.
- Người vào bằng link mời không tự chiếm được chỗ giữ tên; người tạo nhóm được báo khi có yêu cầu nhận một chỗ đang có số dư dương.
- Cách nhận tiền nằm trong collection riêng chỉ chủ đọc được; yêu cầu chỉ mang bản chụp của một cách.
- Hàm gửi thông báo chỉ chạy từ trigger của server, theo mẫu cố định và có giới hạn tần suất.
- Nội dung ghi chú trong link và QR được lọc theo quy định của từng đường thanh toán (độ dài, ký tự).

## Next

Phase 6 đưa yêu cầu trả tiền lên trang web để người chưa cài app xem và tự nhận món.
