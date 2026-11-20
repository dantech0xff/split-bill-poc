---
title: "Phase 6: Nhóm, yêu cầu trả tiền và liên kết hóa đơn"
status: pending
phase: 6
priority: P1
effort: "4d"
dependencies: [5]
---

# Phase 6: Nhóm, yêu cầu trả tiền và liên kết hóa đơn

## Overview

Các màn nhóm, yêu cầu trả tiền, cách nhận tiền và liên kết hóa đơn mang bố cục của bản thiết kế. Màn một nhóm mở đầu bằng vị trí của chính người dùng; màn Requests có hai ô tóm tắt đọc cùng nguồn với trang chủ.

## Context

- Cấu trúc hiện tại: [rà màn hình](./reports/scout-screens-and-state.md), mục 1.8 đến 1.12 và 3.4.
- Bằng chứng kèm tệp và dòng cho các lời gọi thẳng thành phần Material và cho ô nhập cách nhận tiền: [phản biện về phạm vi](./reports/red-team-scope.md), [về giả định](./reports/red-team-assumptions.md), [về bảo mật](./reports/red-team-security.md).
- Bảng tham chiếu trong [`boards/`](./boards/README.md): [`Groups`](./boards/Groups.dc.html), [`Group`](./boards/Group.dc.html), [`DarkGroup`](./boards/DarkGroup.dc.html), [`JoinGroup`](./boards/JoinGroup.dc.html), [`Requests`](./boards/Requests.dc.html), [`RequestLink`](./boards/RequestLink.dc.html), [`PaymentMethods`](./boards/PaymentMethods.dc.html), [`BillLink`](./boards/BillLink.dc.html), [`BillLinkClosed`](./boards/BillLinkClosed.dc.html).
- Gói do giai đoạn này sở hữu: `ui/groups/`, `ui/requests/` (trừ `RequestMoneyViewModel.kt`, thuộc giai đoạn 5), `ui/payments/`, `ui/claim/`.
- Thứ dùng lại từ các giai đoạn trước: `SvDialog`, `SvQuietButton`, `SvTextField`, `SvStatBar` và phép kiểm thanh che nội dung của giai đoạn 2; `RequestTotals` cùng bộ giữ theo Activity của giai đoạn 3; cách viết tiền và ngày của giai đoạn 4; các nhãn "You owe", "Friends owe you", "You get back" mà giai đoạn 5 đã thêm.

## Key Insights

- Danh sách nhóm chỉ biết tên, tiền tệ và trạng thái của từng nhóm; bảng `Groups` vì thế không hiện số dư. Chú thích ở `GroupsScreen.kt:52` nói có số dư là chú thích cũ, cần sửa cho khớp.
- Số dư của một nhóm có sẵn trong `GroupState.balances`, dương là được nhận lại, âm là đang nợ, và là `null` khi các số không cộng khớp. Màn nhóm hôm nay đã viết số dư bằng 0 là "settled up".
- Hóa đơn của nhóm đến từ máy chủ và không mang danh mục, nên hàng hóa đơn hiện ô chữ cái của người thêm.
- Hai ô tóm tắt của màn Requests đọc `RequestTotals` từ bộ giữ theo Activity của giai đoạn 3, đúng nguồn mà trang chủ đọc. Giai đoạn này không viết phép cộng nào, nên hai nơi không thể lệch nhau. Tổng còn ở trạng thái `Unknown` thì chưa có gì để hiện; nó không được coi là số 0.
- Mười chín lời gọi thẳng thành phần Material nằm trong các gói của giai đoạn này, trong đó sáu là `AlertDialog`. `GroupDialogsTest` tìm hộp thoại và menu của màn nhóm theo chữ ("More for Minh", "More for this group", "Take out of the group", "Leave the group"), nên `SvDialog` giữ nguyên tiêu đề và nhãn nút.
- Ô nhập trong bảng thêm cách nhận tiền tắt tự sửa chính tả và cắt chữ ở giới hạn độ dài (`PaymentMethodSheet.kt:237-252`). Số tài khoản, IBAN và mã UPI đi qua ô này; mất một trong hai thứ thì bàn phím học các số đó, hoặc máy chủ từ chối chuỗi quá dài. Ô này còn dùng tiền tố và kiểu nhiều dòng, là hai thứ giai đoạn 2 đã cho `SvTextField`. Tắt tự sửa không ngăn bàn phím gợi ý hay học chữ; ô số tài khoản, IBAN và mã UPI dùng `KeyboardType.Password`, vốn là thứ bàn phím coi là nhạy cảm và không học, trong khi chữ vẫn hiện vì `SvTextField` không che chữ. Ô chi tiết nhiều dòng truyền giới hạn 500 ký tự của nó, vì mặc định của `SvTextField` là 80.
<!-- Updated: Review giai đoạn 2 - kiểu bàn phím cho số tài khoản và giới hạn của ô nhiều dòng -->
- `GroupsGalleryTest` chờ nhãn "Payment code for VietQR" trước khi chụp (`GroupsGalleryTest.kt:119`); mã QR vẫn phải có nhãn đó.
- Ở hai màn gốc `Groups` và `Requests`, thanh điều hướng nằm trong ô `bottomBar` của `Scaffold` và danh sách nhận phần đệm của `Scaffold` làm `Modifier.padding`, không làm `contentPadding`, như giai đoạn 3 đã dựng: danh sách kết thúc phía trên thanh. Dựng lại danh sách không được đổi điều đó; phép kiểm thanh che nội dung ở `GroupsGalleryTest` canh việc này.
<!-- Updated: Thực hiện giai đoạn 3 - danh sách kết thúc phía trên thanh, không còn lề lấy từ chiều cao đo được -->

## Requirements

- `Groups`: màn gốc của tab, tiêu đề lớn, nút "New group", mỗi nhóm một thẻ với ô chữ cái, tên, tiền tệ hoặc dòng đang chờ; trạng thái chưa đăng nhập, đang tải và trống giữ nguyên nội dung.
- `Group`: khối đầu trang nói "You get back" hoặc "You owe" với số dư của mình, màu `accent` khi được nhận lại và màu `bar` khi đang nợ; khối không hiện khi số dư là `null`, và dùng chữ "settled up" đang có khi số dư bằng 0. Số dư từng người có thanh so sánh, bên trái cho người nợ và bên phải cho người được nhận, kèm chữ "gets back" hoặc "owes". Phần "Settle up", người, lời xin vào nhóm, hóa đơn và các hộp thoại giữ nguyên hành vi.
- `JoinGroup`, `RequestLink`, `PaymentMethods` và bảng thêm cách nhận tiền theo các bảng.
- `Requests`: màn gốc của tab, hai ô "You owe" và "Friends owe you", rồi hai phần "To pay" và "You asked for"; mỗi yêu cầu là một thẻ có hành động của nó. Hai ô lấy số từ `RequestTotals`, viết tổng bằng tiền tệ của máy và các tiền tệ khác thành dòng nhỏ bên dưới như trang chủ.
- Hai ô chỉ hiện khi tổng ở trạng thái `Loaded` và danh sách có yêu cầu. Trạng thái chưa đăng nhập vẫn mời đăng nhập; trạng thái đang tải và trống giữ nguyên nội dung hôm nay. Cách hiện hai ô ở các trạng thái này và khối đầu trang của nhóm khi số dư bằng 0 là mặc định của người lập plan, vì chưa có bảng; chủ sản phẩm duyệt qua ảnh thật.
- Màn liên kết hóa đơn ở hai trạng thái đang mở và đã khóa chọn theo `BillLink` và `BillLinkClosed`. Ngày "mở tới" vẫn đi qua hàm ngày của giai đoạn 4.
- Màu không phải tín hiệu duy nhất ở bất kỳ đâu: số dư luôn kèm chữ, yêu cầu đã xác nhận có nhãn chữ.
- Mọi ô nhập được chuyển giữ tùy chọn bàn phím và giới hạn độ dài đang có. Ô trong bảng thêm cách nhận tiền giữ `autoCorrectEnabled = false` và giới hạn độ dài, có bài kiểm thử canh cả hai.

Lời gọi thẳng thành phần Material được chuyển trong giai đoạn này (số dòng theo mã ngày 20/11/2026; giai đoạn 3 đã sửa `GroupsScreen.kt` và `RequestsScreen.kt` nên dòng có thể lệch):
- `ui/groups/GroupsScreen.kt`: `OutlinedTextField` `:165`, `:173`, `:183`.
- `ui/groups/GroupScreen.kt`: bốn `AlertDialog` `:189`, `:205`, `:226`, `:478`; `FilledTonalButton` `:309`, `:379`; `OutlinedTextField` `:484`.
- `ui/groups/JoinGroupScreen.kt`: `OutlinedTextField` `:198`.
- `ui/groups/AddToGroupSheet.kt`: `OutlinedTextField` `:167`.
- `ui/requests/RequestsScreen.kt`: `FilledTonalButton` `:161`, `:170`; `AlertDialog` `:350`.
- `ui/payments/PaymentMethodSheet.kt`: `OutlinedTextField` `:237`.
- `ui/claim/ClaimScreen.kt`: `FilledTonalButton` `:270`, `:322`; `AlertDialog` `:465`.
- Ô nhập thành `SvTextField`, hộp thoại thành `SvDialog`, và `FilledTonalButton` thành `SvQuietButton`, trừ nơi bảng vẽ một kiểu nút khác của giai đoạn 2. Tham số nào `SvTextField` còn thiếu thì thêm vào thành phần và trang mẫu, không dựng ô riêng trong màn.

## Files to Create / Modify

Đường dẫn ngắn tính từ `app/src/main/java/com/dantech/splitvibe/` và, với kiểm thử, từ `app/src/test/java/com/dantech/splitvibe/`.

- Modify: `ui/groups/GroupsScreen.kt`, `GroupScreen.kt`, `JoinGroupScreen.kt`, `AddToGroupSheet.kt`.
- Modify: `ui/requests/RequestsScreen.kt`, `RequestLinkScreen.kt`. `RequestsScreen` nhận tổng làm tham số; ba nơi gọi đổi theo: `entry<RequestsKey>` trong `ui/AppNavHost.kt` và `ui/GroupsGalleryTest.kt:111`, `:116`.
- Modify: `ui/payments/PaymentMethodsScreen.kt`, `PaymentMethodSheet.kt`.
- Modify: `ui/claim/ClaimScreen.kt`.
- Modify: `app/src/main/res/values/strings.xml` (các chuỗi `groups_*`, `requests_*`, `payments_*`, `claim_*`).
- Màn nào khác đổi chữ ký thì sửa luôn nơi gọi của nó: `entry<GroupsKey>`, `entry<GroupKey>`, `entry<JoinGroupKey>`, `entry<RequestLinkKey>`, `entry<PaymentMethodsKey>` và `entry<ClaimKey>` trong `ui/AppNavHost.kt`; `ui/GroupsGalleryTest.kt` cho các màn nhóm, yêu cầu và cách nhận tiền; `ui/ClaimGalleryTest.kt` cho `ClaimScreen`; `ui/groups/GroupDialogsTest.kt` cho `GroupScreen`.
- Modify tests: `ui/GroupsGalleryTest.kt`, `ui/ClaimGalleryTest.kt`, `ui/groups/GroupDialogsTest.kt`; `app/src/androidTest/java/com/dantech/splitvibe/e2e/PaymentRequestJourneyTest.kt`.
- Create tests: một bài cho bảng thêm cách nhận tiền (ví dụ `ui/payments/PaymentMethodSheetTest.kt`); một bài so hai ô của Requests với trang chủ (ví dụ `ui/requests/RequestTilesTest.kt`).

## Implementation Steps

1. Dựng lại `GroupsScreen` thành màn gốc theo bảng; sửa chú thích về số dư; giữ lề đáy của danh sách.
2. Dựng lại `GroupScreen`: khối vị trí của mình, thanh số dư bằng `SvStatBar`, các phần còn lại bằng thành phần mới. Giữ nguyên nhãn của các menu mà `GroupDialogsTest` tìm.
3. Dựng lại `JoinGroupScreen` và `AddToGroupSheet`.
4. Dựng lại `RequestsScreen`: hai ô tóm tắt lấy từ `RequestTotals` qua bộ giữ của giai đoạn 3; thẻ yêu cầu với "Pay", "Confirm" và menu như hôm nay. Sửa ba nơi gọi. Viết bài so hai ô với trang chủ trên cùng dữ liệu mẫu, kể cả ngay sau khi đăng nhập và trước khi tab Requests được mở lần nào; cùng bài đó áp phép kiểm thanh che nội dung của giai đoạn 2 cho danh sách của `Requests`.
5. Dựng lại `RequestLinkScreen`, `PaymentMethodsScreen`, `PaymentMethodSheet`; mã QR giữ vùng trắng quanh mã và nhãn hiện có.
6. Dựng lại `ClaimScreen` ở hai trạng thái; `SvBottomBar` của nó (`ui/claim/ClaimScreen.kt:406`) ở lại trong ô `bottomBar` của `Scaffold`.
7. Chuyển mười chín lời gọi thẳng thành phần Material trong danh sách. Viết bài kiểm thử cho bảng thêm cách nhận tiền: gõ quá dài vào ô số tài khoản thì chữ bị cắt ở giới hạn, và tùy chọn bàn phím của ô không bật tự sửa chính tả.
8. Sửa `GroupsGalleryTest`, `ClaimGalleryTest`, `GroupDialogsTest` và `PaymentRequestJourneyTest` theo bố cục mới.
9. Chạy cổng kiểm thử của plan, nhờ một lượt review độc lập, sửa các phát hiện, rồi ghép ảnh sáng và tối gửi chủ sản phẩm so với bảng.

## Todo

- [ ] `Groups` thành màn gốc theo bảng, chú thích về số dư được sửa
- [ ] `Group` với khối vị trí của mình và thanh số dư
- [ ] `JoinGroup` và bảng thêm vào nhóm
- [ ] `Requests` với hai ô đọc `RequestTotals`, ba nơi gọi, bài so với trang chủ
- [ ] `RequestLink`, `PaymentMethods` và bảng của nó
- [ ] Liên kết hóa đơn ở hai trạng thái
- [ ] Mười chín lời gọi thẳng thành phần Material; bài kiểm thử cho ô cách nhận tiền
- [ ] Kiểm thử của các màn này
- [ ] Cổng kiểm thử, review, ảnh gửi chủ sản phẩm

## Success Criteria

- Cổng kiểm thử của plan qua (với `-Pscreenshots=compare`), gồm `GroupDialogsTest`, bài cho bảng thêm cách nhận tiền, bài so hai ô của Requests với trang chủ và `DesignSystemBoundaryTest`.
- Lượt `device-tests.yml` trên commit cuối của giai đoạn: `PaymentRequestJourneyTest` qua trên máy ảo API 34; kết quả được đọc và ghi lại.
- Với cùng dữ liệu mẫu, tổng "Friends owe you" và "You owe" trên màn Requests bằng tổng trên trang chủ.
- Trong `ui/groups/`, `ui/requests/`, `ui/payments/` và `ui/claim/` không còn lời gọi thẳng `OutlinedTextField`, `FilledTonalButton` hay `AlertDialog`.
- Mã QR trên màn yêu cầu vẫn quét được bằng app ngân hàng trong thử tay.
- `git status --porcelain app/src/test/screenshots` rỗng; ảnh gửi chủ sản phẩm lấy từ `app/build/outputs/roborazzi/*_actual.png`.

## Risk Assessment

- Thanh số dư gây hiểu nhầm khi mọi số dư gần bằng nhau hoặc bằng 0: thanh có bề rộng tối thiểu và chữ luôn nói rõ số.
- Thu nhỏ hoặc bo góc mã QR làm mã khó quét: giữ kích thước và vùng trắng hiện tại; tiêu chí thử tay ở trên.
- Ô nhập số tài khoản mất tùy chọn tắt tự sửa hoặc giới hạn độ dài khi chuyển sang `SvTextField`: bài kiểm thử ở bước 7 bắt lỗi này.
- Một hộp thoại đổi chữ khi chuyển sang `SvDialog` làm `GroupDialogsTest` hỏng: giữ nguyên chuỗi; bài đó chạy trong cổng kiểm thử.
- Hàng cuối của `Groups` hoặc `Requests` nằm dưới thanh điều hướng sau khi dựng lại: phép kiểm thanh che nội dung của giai đoạn 2 áp cho danh sách của `Requests`.

## Rollback

Hoàn lại các commit của giai đoạn chỉ dùng được khi giai đoạn 7 chưa bắt đầu; sau đó lỗi của giai đoạn này được sửa bằng commit mới, vì các giai đoạn sau đã dựng trên nó. Không có dữ liệu hay lời gọi máy chủ nào đổi.
