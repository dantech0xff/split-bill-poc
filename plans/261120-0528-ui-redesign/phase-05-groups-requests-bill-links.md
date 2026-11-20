---
title: "Phase 5: Nhóm, yêu cầu trả tiền và liên kết hóa đơn"
status: pending
phase: 5
priority: P1
effort: "3d"
dependencies: [3]
---

# Phase 5: Nhóm, yêu cầu trả tiền và liên kết hóa đơn

## Overview

Các màn nhóm, yêu cầu trả tiền, cách nhận tiền và liên kết hóa đơn mang bố cục của bản thiết kế. Màn một nhóm mở đầu bằng vị trí của chính người dùng; màn Requests có hai ô tóm tắt.

## Context

- Cấu trúc hiện tại: [rà màn hình](./reports/scout-screens-and-state.md), mục 1.8 đến 1.12 và 3.4.
- Bảng tham chiếu trên canvas: `Groups`, `Group`, `JoinGroup`, `Requests`, `RequestLink`, `PaymentMethods`, `BillLink`, `BillLinkClosed`.
- Gói do giai đoạn này sở hữu: `ui/groups/`, `ui/requests/` (trừ `RequestMoneyViewModel.kt`, thuộc giai đoạn 4), `ui/payments/`, `ui/claim/`.

## Key Insights

- Danh sách nhóm chỉ biết tên, tiền tệ và trạng thái của từng nhóm; bảng `Groups` vì thế không hiện số dư. Chú thích ở `GroupsScreen.kt:52` nói có số dư là chú thích cũ, cần sửa cho khớp.
- Số dư của một nhóm có sẵn trong `GroupState.balances`, dương là được nhận lại, âm là đang nợ, và là `null` khi các số không cộng khớp.
- Hóa đơn của nhóm đến từ máy chủ và không mang danh mục, nên hàng hóa đơn hiện ô chữ cái của người thêm.
- Hai ô tóm tắt của màn Requests phải dùng chung hàm cộng với trang chủ để hai nơi không lệch nhau.
- `GroupsGalleryTest` chờ nhãn "Payment code for VietQR" trước khi chụp; mã QR vẫn phải có nhãn đó.

## Requirements

- `Groups`: màn gốc của tab, tiêu đề lớn, nút "New group", mỗi nhóm một thẻ với ô chữ cái, tên, tiền tệ hoặc dòng đang chờ; trạng thái chưa đăng nhập, đang tải và trống giữ nguyên nội dung.
- `Group`: khối đầu trang nói "You get back" hoặc "You owe" với số dư của mình, màu `accent` khi được nhận lại và màu `bar` khi đang nợ; khối không hiện khi số dư là `null`. Số dư từng người có thanh so sánh, bên trái cho người nợ và bên phải cho người được nhận, kèm chữ "gets back" hoặc "owes". Phần "Settle up", người, lời xin vào nhóm, hóa đơn và các hộp thoại giữ nguyên hành vi.
- `JoinGroup`, `RequestLink`, `PaymentMethods` và bảng thêm cách nhận tiền theo các bảng.
- `Requests`: màn gốc của tab, hai ô "You owe" và "Friends owe you", rồi hai phần "To pay" và "You asked for"; mỗi yêu cầu là một thẻ có hành động của nó. Trạng thái chưa đăng nhập vẫn mời đăng nhập và không hiện hai ô.
- Màn liên kết hóa đơn ở hai trạng thái đang mở và đã khóa chọn theo `BillLink` và `BillLinkClosed`.
- Màu không phải tín hiệu duy nhất ở bất kỳ đâu: số dư luôn kèm chữ, yêu cầu đã xác nhận có nhãn chữ.

## Files to Create / Modify

- Modify: `ui/groups/GroupsScreen.kt`, `GroupScreen.kt`, `JoinGroupScreen.kt`, `AddToGroupSheet.kt`.
- Modify: `ui/requests/RequestsScreen.kt`, `RequestLinkScreen.kt`; `RequestsViewModel.kt` để cấp tóm tắt từ `HomeSummary`.
- Modify: `ui/payments/PaymentMethodsScreen.kt`, `PaymentMethodSheet.kt`.
- Modify: `ui/claim/ClaimScreen.kt`.
- Modify: `app/src/main/res/values/strings.xml` (các chuỗi `groups_*`, `requests_*`, `payments_*`, `claim_*`).
- Modify tests: `ui/GroupsGalleryTest.kt`, `ui/ClaimGalleryTest.kt`, `ui/groups/GroupDialogsTest.kt`; `app/src/androidTest/.../e2e/PaymentRequestJourneyTest.kt`.

## Implementation Steps

1. Dựng lại `GroupsScreen` thành màn gốc; sửa chú thích về số dư.
2. Dựng lại `GroupScreen`: khối vị trí của mình, thanh số dư bằng `SvStatBar`, các phần còn lại bằng thành phần mới. Giữ nguyên nhãn của các menu mà `GroupDialogsTest` tìm ("More for Minh", "More for this group", "Take out of the group", "Leave the group").
3. Dựng lại `JoinGroupScreen` và `AddToGroupSheet`.
4. Dựng lại `RequestsScreen`: hai ô tóm tắt lấy từ `HomeSummary`; thẻ yêu cầu với "Pay", "Confirm" và menu như hôm nay.
5. Dựng lại `RequestLinkScreen`, `PaymentMethodsScreen`, `PaymentMethodSheet`; mã QR giữ vùng trắng quanh mã và nhãn hiện có.
6. Dựng lại `ClaimScreen` ở hai trạng thái.
7. Sửa `GroupsGalleryTest`, `ClaimGalleryTest`, `GroupDialogsTest` và `PaymentRequestJourneyTest` theo bố cục mới.
8. Dựng ảnh các màn của giai đoạn ra thư mục tạm, ghép bảng xem nhanh sáng và tối.

## Todo

- [ ] `Groups` và `Group`
- [ ] `JoinGroup` và bảng thêm vào nhóm
- [ ] `Requests` với hai ô tóm tắt
- [ ] `RequestLink`, `PaymentMethods` và bảng của nó
- [ ] Liên kết hóa đơn ở hai trạng thái
- [ ] Kiểm thử của các màn này
- [ ] Bảng ảnh gửi chủ sản phẩm

## Success Criteria

- `./gradlew :app:testProdDebugUnitTest` qua khi bỏ các bài so ảnh, gồm `GroupDialogsTest`.
- `PaymentRequestJourneyTest` qua trên máy ảo API 34.
- Với cùng dữ liệu mẫu, tổng "Friends owe you" trên màn Requests bằng tổng trên trang chủ.
- Mã QR trên màn yêu cầu vẫn quét được bằng app ngân hàng trong thử tay.

## Risk Assessment

- Thanh số dư gây hiểu nhầm khi mọi số dư gần bằng nhau hoặc bằng 0: thanh có bề rộng tối thiểu và chữ luôn nói rõ số.
- Thu nhỏ hoặc bo góc mã QR làm mã khó quét: giữ kích thước và vùng trắng hiện tại; tiêu chí thử tay ở trên.
- Hai giai đoạn 4 và 5 cùng sửa `strings.xml`: mỗi giai đoạn chỉ sửa các chuỗi có tiền tố của mình; phiên điều phối ghép.

## Rollback

Hoàn lại các commit của giai đoạn. Không có dữ liệu hay lời gọi máy chủ nào đổi.
