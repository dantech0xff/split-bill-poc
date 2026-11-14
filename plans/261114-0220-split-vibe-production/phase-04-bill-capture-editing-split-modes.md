---
title: "Phase 4: Quét, sửa hóa đơn, cách chia, đa tiền tệ, lịch sử"
status: todo
phase: 4
priority: P1
effort: "12d"
dependencies: [2, 3]
---

# Phase 4: Quét, sửa hóa đơn, cách chia, đa tiền tệ, lịch sử

## Overview

Một hóa đơn đi trọn vòng đời trong app: quét (tự cắt viền, nhiều trang, hoặc nhận ảnh từ app khác), sửa bằng tay mọi thứ AI đọc sai, chia theo nhiều cách với nhiều người trả, quy đổi tiền tệ theo tỷ giá ngày, rồi được lưu lại, mở lại và đồng bộ.

Tính năng v1.0 thuộc giai đoạn này: sửa hóa đơn bằng tay; quét tự cắt viền, nhiều trang; đa tiền tệ và tỷ giá; lịch sử, bản nháp, đồng bộ; chia theo phần, phần trăm, số tiền và nhiều người trả.

## Context

- Hiện tại ảnh lấy từ máy ảnh hoặc bộ chọn ảnh hệ thống (`app/src/main/java/com/dantech/splitvibe/ui/split/NewSplitContent.kt:133-136`), chỉ một trang, không cắt viền.
- Kết quả AI đọc không sửa được; người dùng chỉ đổi được ai chia món nào.
- Cách chia hiện có: chia đều và chia theo món (`app/src/main/java/com/dantech/splitvibe/domain/SplitCalculator.kt:39`), một người trả.
- Hóa đơn khác tiền tệ chỉ được báo chứ không quy đổi (`app/src/main/java/com/dantech/splitvibe/domain/Bill.kt:52`).
- Mọi thứ mất khi tắt app: trạng thái chỉ nằm trong `SplitBillViewModel` (`app/src/main/java/com/dantech/splitvibe/ui/split/SplitBillViewModel.kt:69`).
- Danh bạ là 12 người giả (`app/src/main/java/com/dantech/splitvibe/data/contacts/ContactRepository.kt:9`).
- Dữ kiện: [báo cáo backend](../reports/researcher-261114-0209-product-backend-and-payment-links.md), mục 3 (tỷ giá) và mục 5 (chính sách danh bạ của Google Play).

## Key Insights

- Nguồn tỷ giá miễn phí Frankfurter phải dùng **API v2**: kiểm ngày 14/11/2026, v1 trả 30 tiền tệ và không có VND, v2 trả 200 mã và có VND.
- Mỗi hóa đơn phải **chụp lại tỷ giá** lúc tạo, để số dư cũ không trôi theo tỷ giá mới. Người dùng được sửa tỷ giá cho khớp số thực trả.
- Google Play sẽ không cho xin quyền đọc danh bạ chỉ để chọn người giao dịch (bắt buộc từ 27/01/2027 với app nhắm Android 17). App dùng bộ chọn liên hệ của hệ thống và không xin quyền `READ_CONTACTS`.
- Bộ nhớ đệm offline của Firestore bật sẵn trên Android, nên lịch sử và bản nháp dùng luôn Firestore mà không cần thêm cơ sở dữ liệu cục bộ.
- Ảnh hóa đơn không tải lên server ở v1.0: lời hứa "ảnh không rời máy" được giữ, và không phát sinh chi phí lưu trữ. Hệ quả: ảnh không đồng bộ sang máy khác, chỉ dữ liệu hóa đơn được đồng bộ.

## Requirements

Chức năng:
- **Quét:** bộ quét tài liệu của ML Kit (tự cắt viền, chỉnh nghiêng, nhiều trang). Khi máy không có Google Play services thì lùi về máy ảnh và bộ chọn ảnh hiện có. App nhận ảnh được chia sẻ từ app khác.
- **Sửa:** sửa tên, giá, số lượng món; thêm và xóa món; sửa thuế, phí, tip, giảm giá và tổng; nhập tay cả hóa đơn không cần ảnh. Khi tổng các dòng khác tổng in trên hóa đơn, app chỉ rõ chênh bao nhiêu và đề xuất cách xử lý.
- **Cách chia:** đều, theo món, theo số phần, theo phần trăm, theo số tiền cụ thể; áp cho từng món hoặc cả hóa đơn. Hóa đơn có thể do nhiều người cùng trả.
- **Người tham gia:** nhập tên tự do, chọn từ danh sách người gần đây, hoặc chọn bằng bộ chọn liên hệ hệ thống.
- **Tiền tệ:** tỷ giá ngày được chụp vào hóa đơn, sửa được, có ghi ngày và nguồn.
- **Lịch sử:** danh sách hóa đơn, tìm kiếm, mở lại để sửa, xóa; bản nháp tự lưu; đồng bộ khi đã đăng nhập; dùng được khi mất mạng.

Phi chức năng (bất biến của phần tính tiền, áp cho mọi cách chia):
- Tổng phần của mọi người luôn bằng đúng tổng hóa đơn, tính bằng số nguyên theo đơn vị nhỏ nhất.
- Tổng số tiền những người trả bằng đúng tổng hóa đơn.
- Không ai nhận phần âm trừ khi hóa đơn có giảm giá lớn hơn phần của họ; phần lẻ chia theo quy tắc số dư lớn nhất.

## Architecture

Mở rộng `:core:domain`:
- `SplitMode`: `Equal`, `ByItem`, `ByShares`, `ByPercent`, `ByAmount`.
- `Payment`: ai trả bao nhiêu; `Bill` có danh sách `payments`.
- `FxSnapshot`: tiền tệ gốc, tiền tệ đích, tỷ giá, ngày, nguồn, có phải do người dùng sửa không.
- `BillEditor`: các thao tác sửa thuần túy trả về hóa đơn mới cùng kết quả đối chiếu.
- Hàm chia dùng lại `divideEqually` và `divideProportionally` hiện có (`SplitCalculator.kt:83` và `:96`).

OCR nhiều trang: `OcrLayoutEncoder.encode` (`app/src/main/java/com/dantech/splitvibe/ocr/OcrLayoutEncoder.kt:63`) nhận danh sách trang và đánh số trang trong tài liệu gửi đi; prompt trên server được cập nhật tương ứng và có fixture mới.

Dữ liệu:
- `bills/{billId}` thêm `payments`, `splitModes`, `fx`, `status` (`draft`, `final`), `searchTokens`.
- `users/{uid}/people/{personId}`: người gần đây (tên, ghi chú, không có số điện thoại nếu người dùng không chọn lưu).
- `fxRates/{yyyy-mm-dd}`: tỷ giá ngày, chỉ server ghi, mọi người dùng đã đăng nhập đọc được.

Server: hàm theo lịch chạy mỗi ngày, lấy tỷ giá từ Frankfurter v2, ghi một tài liệu mỗi ngày; nguồn dự phòng là Open Exchange Rates gói trả phí.

## Files

Tạo mới:
- `core/domain/src/main/kotlin/.../SplitMode.kt`, `Payment.kt`, `FxSnapshot.kt`, `BillEditor.kt` cùng test
- `app/src/main/java/com/dantech/splitvibe/scan/DocumentScanner.kt`
- `app/src/main/java/com/dantech/splitvibe/ui/editor/` (màn hình sửa hóa đơn)
- `app/src/main/java/com/dantech/splitvibe/ui/history/` (lịch sử và tìm kiếm)
- `app/src/main/java/com/dantech/splitvibe/data/people/PeopleRepository.kt`, `data/fx/FxRepository.kt`
- `functions/src/fx-rates.ts` cùng test

Sửa:
- `core/domain/.../SplitCalculator.kt`, `SplitPlan.kt`, `Bill.kt`, `OcrLayoutEncoder.kt`
- `app/src/main/java/com/dantech/splitvibe/ui/split/SplitBillViewModel.kt`, `NewSplitContent.kt`, `ResultContent.kt`
- `app/src/main/AndroidManifest.xml` (bộ lọc intent nhận ảnh chia sẻ)
- `functions/src/prompt.ts`, `firestore.rules`

Xóa:
- `app/src/main/java/com/dantech/splitvibe/data/contacts/ContactRepository.kt` (danh bạ giả), thay bằng `PeopleRepository`

## Implementation Steps

1. Viết test thuộc tính cho từng cách chia và cho nhiều người trả trước, rồi mở rộng `:core:domain` cho tới khi test qua.
2. Viết `BillEditor` và màn hình sửa; nối phép đối chiếu sẵn có để hiện chênh lệch.
3. Tích hợp bộ quét tài liệu, nhiều trang, đường lùi và bộ lọc nhận ảnh chia sẻ; cập nhật bộ mã hóa OCR và prompt.
4. Thay danh bạ giả bằng `PeopleRepository` và bộ chọn liên hệ hệ thống.
5. Viết hàm tỷ giá theo lịch, `FxRepository`, phần chụp tỷ giá và sửa tỷ giá trong hóa đơn.
6. Lưu hóa đơn và bản nháp vào Firestore; màn hình lịch sử, tìm kiếm, mở lại, xóa; khôi phục sau khi tiến trình bị hủy.
7. Dựng mọi màn hình mới bằng hệ thiết kế, thêm ảnh chuẩn, mở rộng `SplitFlowTest`.

## Todo

- [ ] Test thuộc tính cho 5 cách chia và nhiều người trả
- [ ] `BillEditor` và màn hình sửa, nhập tay không cần ảnh
- [ ] Bộ quét tài liệu nhiều trang, đường lùi, nhận ảnh chia sẻ
- [ ] OCR nhiều trang: bộ mã hóa, prompt, fixture
- [ ] `PeopleRepository` và bộ chọn liên hệ hệ thống, xóa danh bạ giả
- [ ] Hàm tỷ giá theo lịch và `FxRepository`
- [ ] Chụp tỷ giá, sửa tỷ giá, hiển thị ngày và nguồn
- [ ] Lịch sử, tìm kiếm, bản nháp tự lưu, đồng bộ
- [ ] Ảnh chuẩn cho màn hình mới và test luồng mở rộng

## Success Criteria

- Test thuộc tính: với 2.000 hóa đơn ngẫu nhiên cho mỗi cách chia, tổng các phần bằng tổng hóa đơn và tổng tiền đã trả bằng tổng hóa đơn.
- Sửa một món trong hóa đơn mẫu làm tổng và phần của từng người cập nhật đúng; nhập tay một hóa đơn 3 món không cần ảnh chạy trọn luồng.
- Quét một hóa đơn 2 trang cho ra một hóa đơn duy nhất có đủ món của cả hai trang (kiểm trên máy thật, có fixture ghi lại).
- Chia sẻ một ảnh từ app thư viện ảnh mở đúng màn hình chia hóa đơn với ảnh đó.
- `app/src/main/AndroidManifest.xml` không có `READ_CONTACTS` và không có `READ_MEDIA_IMAGES`.
- Hóa đơn bằng USD chia cho người dùng có tiền tệ VND hiển thị số quy đổi kèm ngày và nguồn tỷ giá; sửa tỷ giá làm số quy đổi đổi theo; mở lại hôm sau vẫn giữ tỷ giá đã chụp.
- Tài liệu `fxRates` của ngày hôm nay có VND, USD, EUR, INR, THB.
- Tạo và sửa hóa đơn ở chế độ máy bay, bật mạng lại thì hóa đơn xuất hiện trên máy thứ hai cùng tài khoản.
- Hủy tiến trình app giữa lúc đang sửa rồi mở lại: bản nháp còn nguyên.

## Risks

| Rủi ro | Dấu hiệu nhận biết | Phản ứng đã định |
|---|---|---|
| Bộ quét của ML Kit không có trên máy thiếu Play services | Lỗi khi mở bộ quét | Tự động lùi về máy ảnh và bộ chọn ảnh |
| Hóa đơn nhiều trang làm model nhầm dòng giữa các trang | Bộ đánh giá: số món sai trên fixture nhiều trang | Gửi từng trang có đánh dấu rõ, thêm ví dụ vào prompt |
| Frankfurter ngừng hoặc đổi API | Hàm theo lịch lỗi, tài liệu ngày không được ghi | Chuyển sang nguồn dự phòng; app dùng tỷ giá gần nhất và ghi rõ ngày |
| Cách chia phức tạp gây lệch đơn vị lẻ | Test thuộc tính thất bại | Dừng phát hành tính năng đó cho tới khi test qua; đây là bất biến không được nới |
| Tìm kiếm trên Firestore hạn chế | Tìm theo chuỗi con không ra | v1.0 tìm theo từ đầu và trường `searchTokens`, lọc thêm trên máy |

## Security

- Rules: chỉ chủ hóa đơn và thành viên trong `memberUids` đọc được; chỉ chủ sửa được hóa đơn của mình; `fxRates` chỉ server ghi.
- Văn bản người dùng nhập khi sửa hóa đơn bị giới hạn độ dài trong rules.
- Ảnh hóa đơn ở trong bộ nhớ riêng của app và không tải lên.

## Next

Phase 5 thêm nhóm và số dư trên nền `bills`, `payments` và `PeopleRepository`. Phase 7 dùng `BillEditor` làm nơi áp các thay đổi do AI đề xuất.
