---
title: "Phase 4: Quét, sửa hóa đơn, cách chia, đa tiền tệ, lịch sử"
status: todo
phase: 4
priority: P1
effort: "13d"
dependencies: [2, 3]
---

# Phase 4: Quét, sửa hóa đơn, cách chia, đa tiền tệ, lịch sử

## Overview

Một hóa đơn đi trọn vòng đời trong app: quét (tự cắt viền, nhiều trang, hoặc nhận ảnh từ app khác), sửa bằng tay mọi thứ AI đọc sai, chia theo nhiều cách với nhiều người trả, xem số quy đổi theo tỷ giá ngày, rồi được lưu lại, mở lại và đồng bộ.

Tính năng v1.0 thuộc giai đoạn này: sửa hóa đơn bằng tay; quét tự cắt viền, nhiều trang; đa tiền tệ và tỷ giá; lịch sử, bản nháp, đồng bộ; chia theo phần, phần trăm, số tiền và nhiều người trả.

## Context

- Hiện tại ảnh lấy từ máy ảnh hoặc bộ chọn ảnh hệ thống (`app/src/main/java/com/dantech/splitvibe/ui/split/NewSplitContent.kt:133-136`), chỉ một trang, không cắt viền.
- Kết quả AI đọc không sửa được; người dùng chỉ đổi được ai chia món nào.
- Cách chia hiện có: chia đều và chia theo món (`SplitCalculator.kt:39`), một người trả.
- Hóa đơn khác tiền tệ chỉ được báo chứ không quy đổi (`Bill.kt:52`).
- Mọi thứ mất khi tắt app: trạng thái chỉ nằm trong `SplitBillViewModel` (`app/src/main/java/com/dantech/splitvibe/ui/split/SplitBillViewModel.kt:69`).
- Danh bạ là 12 người giả (`app/src/main/java/com/dantech/splitvibe/data/contacts/ContactRepository.kt:9`), và món trong hóa đơn chưa có mã định danh riêng (`Bill.kt:9`).
- Dữ kiện: [báo cáo backend](../reports/researcher-261114-0209-product-backend-and-payment-links.md), mục 3 (tỷ giá) và mục 5 (chính sách danh bạ của Google Play).

## Key Insights

- Nguồn tỷ giá miễn phí Frankfurter phải dùng **API v2**: kiểm ngày 14/11/2026, v1 trả 30 tiền tệ và không có VND, v2 trả 200 mã và có VND.
- Google Play sẽ không cho xin quyền đọc danh bạ chỉ để chọn người giao dịch (bắt buộc từ 27/01/2027 với app nhắm Android 17). App dùng bộ chọn liên hệ của hệ thống và không xin quyền `READ_CONTACTS`.
- Bộ nhớ đệm offline của Firestore bật sẵn trên Android, nên lịch sử và bản nháp dùng luôn Firestore mà không cần thêm cơ sở dữ liệu cục bộ.
- Firestore gộp các lần ghi theo từng trường, lần ghi sau thắng. Nếu đầu vào và phần mỗi người được ghi riêng lẻ, hai máy sửa offline có thể để lại một tài liệu mà phần chia không còn khớp tổng. Vì vậy hóa đơn luôn được ghi **nguyên cả tài liệu** kèm số phiên bản.
- Ảnh hóa đơn không tải lên server ở v1.0: lời hứa "ảnh không rời máy" được giữ, và không phát sinh chi phí lưu trữ. Hệ quả: ảnh không đồng bộ sang máy khác, chỉ dữ liệu hóa đơn được đồng bộ.
- <!-- Updated: Validation Session 1 - chữ viết --> v1.0 chỉ đọc hóa đơn chữ Latin. Hóa đơn chữ khác được báo rõ là chưa hỗ trợ và người dùng vẫn nhập tay được.

## Requirements

Chức năng:
- **Quét:** bộ quét tài liệu của ML Kit (tự cắt viền, chỉnh nghiêng, nhiều trang). Khi máy không có Google Play services thì lùi về máy ảnh và bộ chọn ảnh hiện có. App nhận ảnh được chia sẻ từ app khác.
- **Sửa:** sửa tên, giá, số lượng món; thêm và xóa món; sửa thuế, phí, tip, giảm giá và tổng; nhập tay cả hóa đơn không cần ảnh. Khi tổng các dòng khác tổng in trên hóa đơn, app chỉ rõ chênh bao nhiêu và đề xuất cách xử lý.
- **Cách chia:** đều, theo món, theo số phần, theo phần trăm, theo số tiền cụ thể; áp cho từng món hoặc cả hóa đơn. Hóa đơn có thể do nhiều người cùng trả.
- **Người tham gia:** nhập tên tự do, chọn từ danh sách người gần đây, hoặc chọn bằng bộ chọn liên hệ hệ thống.
- **Tiền tệ:** phần mỗi người luôn được tính và lưu bằng tiền của hóa đơn. Số quy đổi sang tiền mặc định của người xem dùng tỷ giá ngày, sửa được, có ghi ngày và nguồn.
- **Lịch sử:** danh sách hóa đơn, tìm kiếm, mở lại để sửa, xóa; bản nháp tự lưu; đồng bộ khi đã đăng nhập; dùng được khi mất mạng.

Phi chức năng (bất biến của phần tính tiền, áp cho mọi cách chia):
- Tổng phần của mọi người luôn bằng đúng tổng hóa đơn, tính bằng số nguyên theo đơn vị nhỏ nhất.
- Tổng số tiền những người trả bằng đúng tổng hóa đơn.
- Phần lẻ chia theo quy tắc số dư lớn nhất.
- Bất biến đúng cả trên **tài liệu đã lưu**, không chỉ trong bộ nhớ.

## Architecture

<!-- Red Team: không có định danh người thống nhất — 2026-11-14 -->
Định danh người:
- Mỗi hóa đơn có danh sách `participants` với mã cục bộ trong hóa đơn (`p1`, `p2`, …). Mọi cách chia, khoản đã trả, phần mỗi người và yêu cầu trả tiền đều trỏ tới mã này.
- Một người tham gia có thể kèm `uid` (nếu là người dùng app) và `memberId` (nếu hóa đơn thuộc một nhóm, xem Phase 5). Danh sách người gần đây `users/{uid}/people` chỉ dùng để gợi ý tên, không bao giờ dùng để tính số dư.
- `Contact` được thay bằng `Person` trong `:core:domain`.

<!-- Red Team: hóa đơn có nhiều nơi ghi mà không có quy tắc xung đột — 2026-11-14 -->
Nguồn chuẩn và xung đột:
- Đầu vào (món, phụ phí, người tham gia, cách chia, khoản đã trả) là nguồn chuẩn. Phần mỗi người là kết quả tính, được lưu cùng tài liệu kèm `calcVersion`.
- Mỗi lần ghi là ghi nguyên tài liệu và tăng `revision` đúng 1; rules từ chối lần ghi có `revision` không liền kề. Máy ghi sau trên một bản cũ sẽ bị từ chối khi có mạng; app tải lại và báo "hóa đơn đã được sửa trên máy khác".
- Khi hóa đơn ở trạng thái `final`, phần mỗi người đã lưu là số chính thức. Số dư nhóm ở Phase 5 chỉ đọc số đã lưu và không tính lại hóa đơn cũ bằng mã mới.
- Mỗi món có `itemId` ổn định, để Phase 6 tham chiếu món mà không phụ thuộc thứ tự.

Mở rộng `:core:domain`:
- `Person`, `Participant`; `BillItem` thêm `id`.
- `SplitMode`: `Equal`, `ByItem`, `ByShares`, `ByPercent`, `ByAmount`.
- `Payment`: ai trả bao nhiêu; `Bill` có danh sách `payments`.
- `BillEditor`: các thao tác sửa thuần túy trả về hóa đơn mới cùng kết quả đối chiếu.
- Hàm chia dùng lại `divideEqually` và `divideProportionally` hiện có (`SplitCalculator.kt:83` và `:96`).

OCR nhiều trang: `BillAnalyzer.analyze` và `OcrLayoutEncoder.encode` nhận danh sách trang và đánh số trang trong tài liệu gửi đi; prompt trên server được cập nhật tương ứng và có fixture mới.

Dữ liệu:
- `bills/{billId}`: `ownerUid`, `memberUids`, `participants`, món (có `itemId`), phụ phí, tổng, tiền tệ, `splitModes`, `payments`, `shares`, `calcVersion`, `revision`, `status` (`draft`, `final`), `searchTokens`, `schemaVersion`.
- `users/{uid}/people/{personId}`: người gần đây (chỉ tên, không có số điện thoại nếu người dùng không chọn lưu).
- `fxRates/{yyyy-mm-dd}`: tỷ giá ngày với một tiền tệ gốc cố định và trường nguồn; chỉ server ghi.

Server: hàm theo lịch chạy mỗi ngày, lấy tỷ giá từ Frankfurter v2 và ghi một tài liệu mỗi ngày. Khi nguồn lỗi, app dùng tài liệu gần nhất và ghi rõ ngày; người dùng sửa tỷ giá bằng tay được.

## Files

Tạo mới:
- Trong `core/domain/`: `Person.kt`, `Participant.kt`, `SplitMode.kt`, `Payment.kt`, `BillEditor.kt` cùng test
- `app/src/main/java/com/dantech/splitvibe/scan/DocumentScanner.kt`
- `app/src/main/java/com/dantech/splitvibe/ui/editor/` (màn hình sửa hóa đơn)
- `app/src/main/java/com/dantech/splitvibe/ui/history/` (lịch sử và tìm kiếm)
- `app/src/main/java/com/dantech/splitvibe/data/people/PeopleRepository.kt`, `app/src/main/java/com/dantech/splitvibe/data/fx/FxRepository.kt`
- `app/src/test/java/com/dantech/splitvibe/TestPeople.kt` (danh sách người mẫu chỉ dùng cho test, thay cho danh bạ giả)
- `functions/src/fx-rates.ts` cùng test

<!-- Red Team: danh sách tệp bỏ sót nơi dùng — 2026-11-14 -->
Sửa, liệt kê đủ nơi dùng (đường dẫn theo vị trí hiện tại; phần thuộc `:core:domain` đã chuyển chỗ ở Phase 1):
- `analyze` nhận nhiều trang: `llm/BillAnalyzer.kt:20` (giao kèo), `GatewayBillAnalyzer.kt`, `RecordedBillAnalyzer.kt`, `ui/split/SplitBillViewModel.kt:121` và `:131`
- `OcrLayoutEncoder.encode` nhận nhiều trang: `ocr/OcrLayoutEncoder.kt:63`, `ui/split/SplitBillViewModel.kt:131`, `OcrLayoutEncoderTest.kt`, `MlKitBillTextRecognizerTest.kt`, `RecordedSampleBillTest.kt:28`
- `Contact` thành `Person`: `domain/SplitPlan.kt:3`, `domain/NameMatcher.kt:3`, `ui/home/HomeScreen.kt:29`, `ui/split/ResultContent.kt:55`, `ui/split/SplitSheets.kt:30`, `ui/split/SplitScreen.kt:40`, `ui/AppNavHost.kt:32`, `ui/split/SplitBillViewModel.kt:11`, `SplitPlannerTest.kt:3`, `NameMatcherTest.kt:3`
- Bỏ `SeededContactRepository`: `ui/split/SplitBillViewModel.kt:70` và `:77`, module Koin, `RecordedSampleBillTest.kt:22` và `SplitFlowTest.kt:83` (chuyển sang `TestPeople`), `ScreenGalleryTest.kt`, `README.md:219`
- `SplitCalculator.kt`, `SplitPlan.kt`, `Bill.kt` (cách chia mới, `itemId`, `payments`)
- `app/src/main/java/com/dantech/splitvibe/ui/split/NewSplitContent.kt`, `ResultContent.kt`
- `app/src/main/AndroidManifest.xml` (bộ lọc intent nhận ảnh chia sẻ)
- `functions/src/prompt.ts`, `functions/src/delete-account.ts` (xóa `people` và bản nháp), `firestore.rules`

Xóa:
- `app/src/main/java/com/dantech/splitvibe/data/contacts/ContactRepository.kt`

## Implementation Steps

1. Viết test thuộc tính cho từng cách chia và cho nhiều người trả trước, rồi mở rộng `:core:domain` cho tới khi test qua.
2. Đổi `Contact` thành `Person`, thêm `Participant` và `itemId`; sửa đủ các nơi dùng đã liệt kê; chuyển test sang `TestPeople`.
3. Viết `BillEditor` và màn hình sửa; nối phép đối chiếu sẵn có để hiện chênh lệch.
4. Tích hợp bộ quét tài liệu, nhiều trang, đường lùi và bộ lọc nhận ảnh chia sẻ; đổi `analyze` và `encode` sang danh sách trang ở đủ các nơi đã liệt kê; cập nhật prompt.
5. `PeopleRepository` và bộ chọn liên hệ hệ thống.
6. Viết hàm tỷ giá theo lịch, `FxRepository`, phần hiển thị quy đổi và sửa tỷ giá.
7. Lưu hóa đơn và bản nháp vào Firestore với `revision` và rules tương ứng; màn hình lịch sử, tìm kiếm, mở lại, xóa; xử lý xung đột; khôi phục sau khi tiến trình bị hủy.
8. Dựng mọi màn hình mới bằng hệ thiết kế, thêm ảnh chuẩn, mở rộng `SplitFlowTest`.

## Todo

- [ ] Test thuộc tính cho 5 cách chia và nhiều người trả
- [ ] `Person`, `Participant`, `itemId`; sửa đủ nơi dùng; `TestPeople`
- [ ] `BillEditor` và màn hình sửa, nhập tay không cần ảnh
- [ ] Bộ quét tài liệu nhiều trang, đường lùi, nhận ảnh chia sẻ
- [ ] `analyze` và `encode` nhận nhiều trang ở mọi nơi dùng; prompt và fixture
- [ ] `PeopleRepository` và bộ chọn liên hệ hệ thống, xóa danh bạ giả
- [ ] Hàm tỷ giá theo lịch, `FxRepository`, hiển thị quy đổi, sửa tỷ giá
- [ ] Lưu có `revision`, rules, xử lý xung đột hai máy
- [ ] Lịch sử, tìm kiếm, bản nháp tự lưu, đồng bộ
- [ ] Xóa tài khoản xóa được `people` và bản nháp
- [ ] Ảnh chuẩn cho màn hình mới và test luồng mở rộng

## Success Criteria

- Test thuộc tính: với 2.000 hóa đơn ngẫu nhiên cho mỗi cách chia, tổng các phần bằng tổng hóa đơn và tổng tiền đã trả bằng tổng hóa đơn.
- Sửa một món trong hóa đơn mẫu làm tổng và phần của từng người cập nhật đúng; nhập tay một hóa đơn 3 món không cần ảnh chạy trọn luồng.
- Quét một hóa đơn 2 trang cho ra một hóa đơn duy nhất có đủ món của cả hai trang (kiểm trên máy thật, có fixture ghi lại).
- Chia sẻ một ảnh từ app thư viện ảnh mở đúng màn hình chia hóa đơn với ảnh đó.
- `app/src/main/AndroidManifest.xml` không có `READ_CONTACTS` và không có `READ_MEDIA_IMAGES`.
- Hóa đơn bằng USD với người xem dùng VND hiển thị số quy đổi kèm ngày và nguồn tỷ giá; phần mỗi người đã lưu vẫn bằng USD.
- Tài liệu `fxRates` của ngày hôm nay có VND, USD, EUR, INR, THB.
- **Hai máy sửa offline cùng một hóa đơn:** sau khi cả hai có mạng, tài liệu đã lưu có tổng các phần bằng tổng hóa đơn, và máy ghi sau hiện thông báo xung đột. Test rules: lần ghi có `revision` không liền kề bị từ chối.
- Tạo và sửa hóa đơn ở chế độ máy bay, bật mạng lại thì hóa đơn xuất hiện trên máy thứ hai cùng tài khoản.
- Hủy tiến trình app giữa lúc đang sửa rồi mở lại: bản nháp còn nguyên.
- Mọi test hiện có sau Phase 3 vẫn qua, với `RecordedSampleBillTest` và `SplitFlowTest` dùng `TestPeople`.

## Risks

| Rủi ro | Dấu hiệu nhận biết | Phản ứng đã định |
|---|---|---|
| Bộ quét của ML Kit không có trên máy thiếu Play services | Lỗi khi mở bộ quét | Tự động lùi về máy ảnh và bộ chọn ảnh |
| Hóa đơn nhiều trang làm model nhầm dòng giữa các trang | Bộ đánh giá: số món sai trên fixture nhiều trang | Gửi từng trang có đánh dấu rõ, thêm ví dụ vào prompt |
| Frankfurter ngừng hoặc đổi API | Hàm theo lịch lỗi, tài liệu ngày không được ghi | App dùng tỷ giá gần nhất và ghi rõ ngày; chỉ thêm nguồn thứ hai sau một sự cố thật |
| Cách chia phức tạp gây lệch đơn vị lẻ | Test thuộc tính thất bại | Dừng phát hành tính năng đó cho tới khi test qua; đây là bất biến không được nới |
| Người dùng mất phần sửa khi bị từ chối do xung đột | Phản hồi của người thử | Giữ bản sửa bị từ chối trên máy và cho áp lại lên bản mới |
| Tìm kiếm trên Firestore hạn chế | Tìm theo chuỗi con không ra | v1.0 tìm theo từ đầu và trường `searchTokens`, lọc thêm trên máy |

## Security

<!-- Red Team: client tự thêm người vào memberUids — 2026-11-14 -->
- Rules: chỉ chủ hóa đơn và người có trong `memberUids` đọc được; chỉ chủ ghi được hóa đơn; `fxRates` chỉ server ghi.
- `memberUids` không sửa được từ client. Nó chỉ đổi qua hàm trên server, khi người kia đã chấp nhận (vào nhóm hoặc mở link yêu cầu, xem Phase 5).
- Văn bản người dùng nhập khi sửa hóa đơn bị giới hạn độ dài trong rules.
- Ảnh hóa đơn ở trong bộ nhớ riêng của app và không tải lên.

## Next

Phase 5 thêm nhóm, số dư và trạng thái trả tiền trên nền `participants`, `payments` và phần mỗi người đã lưu. Phase 6 dùng `itemId`. Phase 7 dùng `BillEditor` làm nơi áp các thay đổi do AI đề xuất.
