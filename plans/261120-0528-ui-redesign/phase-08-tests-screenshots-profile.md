---
title: "Phase 8: Kiểm thử, ảnh tham chiếu và hồ sơ khởi động"
status: pending
phase: 8
priority: P1
effort: "3d"
dependencies: [7]
---

# Phase 8: Kiểm thử, ảnh tham chiếu và hồ sơ khởi động

## Overview

Toàn bộ giao diện mới được kiểm một lượt cuối. Ảnh tham chiếu được ghi lại một lần, và từ đây kiểm thử chạy ở chế độ so mặc định như CI. Giao diện tối, chữ cỡ 200%, ngôn ngữ giả và máy đặt vùng Việt Nam được soát trên ảnh. Kiểm thử trên máy ảo chạy bằng hai lượt riêng, hai phông chữ được xem bằng mắt trên máy ảo API 26, và giá trị token màu được chốt. Hồ sơ khởi động đi kèm bản phát hành được ghi ở giai đoạn 10; giai đoạn này chỉ chứng minh rằng việc ghi vẫn chạy được với các hành trình mới.

## Context

- Mọi thứ kiểm tra giao diện và những gì sẽ đổi: [rà kiểm thử, CI, web và tài liệu](./reports/scout-tests-ci-web-docs.md), mục 1 đến 4.
- Cổng kiểm thử và chế độ `-Pscreenshots=compare` mà các giai đoạn 1 đến 7 dùng: mục "Cổng kiểm thử" của [plan](./plan.md).
- Vì sao một lượt chạy trên máy ảo không đủ, và vì sao hồ sơ khởi động được ghi sau cùng: [phản biện về kiểu hỏng](./reports/red-team-failure-modes.md) và [phản biện về bảo mật](./reports/red-team-security.md), phần nói về `device-tests.yml`.
- Bảng tham chiếu: cả 37 bảng trong [`boards/`](./boards/README.md), để so với ảnh thật.

## Key Insights

- 97 ảnh tham chiếu nằm trong `app/src/test/screenshots/`, và mọi ảnh màn hình đều đổi. Sáu ảnh không còn bài kiểm thử nào ghi: `07-pick-people` và `08-item-sharers`, mỗi cái có bản tối, đã thừa từ trước; `09-sent-to-claude` và bản tối của nó mất bài ghi khi giai đoạn 5 gỡ phần chữ đã gửi.
- Chế độ so chỉ nhìn những ảnh mà bài kiểm thử chụp, nên nó không bao giờ báo một ảnh tham chiếu không còn ai ghi. Cách chắc chắn để thấy ảnh thừa là ghi vào một thư mục đã dọn trống.
- Gradle không chạy lại một việc kiểm thử khi đầu vào của nó không đổi. Lượt so thứ hai trên cùng mã vì thế phải được ép chạy lại, ví dụ bằng tùy chọn `--rerun`; nếu không, nó không so gì cả.
- Chỉ năm màn có bảng tối: `DarkHome`, `DarkNewSplit`, `DarkResult`, `DarkAskForMoney` và `DarkGroup`. Giao diện tối của các màn còn lại đến từ token, nên ảnh tối của từng màn là nơi duy nhất thấy lỗi tương phản do bố cục.
- Dưới Robolectric 4.17, bài chụp không nêu ngôn ngữ luôn chạy với vùng `en-US`, bất kể máy dựng đặt vùng nào, và một qualifier như `vi-rVN` đổi luôn vùng mặc định của Java, là thứ hàm định dạng của giai đoạn 4 đọc. Điều này đã được kiểm ngày 20/11/2026 bằng `javap` trên gói trong bộ đệm Gradle. Ảnh tham chiếu vì thế không phụ thuộc máy ghi.
- Chỉ hai lớp kiểm thử trên máy ảo bật kiểm tra trợ năng, `AppLaunchTest` và `SplitFlowOnDeviceTest`, và chỉ từ API 34. Ở API 26, 30 và 36 chỉ có các hành trình của gói `e2e` chạy.
- Một lượt `device-tests.yml` không cho đủ kết quả. Khi chọn `api-levels`, việc chạy trên Android 14 bị bỏ qua (`device-tests.yml:53`) và việc `profile` cũng vậy (`device-tests.yml:97`). Ma trận ba mức API có `continue-on-error: true` (`device-tests.yml:152`), nên lượt đó xanh ngay cả khi mọi hành trình hỏng. Một lượt xanh không chứng minh gì; kết luận của từng việc mới là kết quả.
- Lượt mặc định dùng chung nhóm với lượt do một lần đẩy sinh ra trên cùng nhánh, và lượt mới hủy lượt đang chạy (`device-tests.yml:42-46`). Lượt chọn `api-levels` có nhóm riêng nên không lần đẩy nào hủy nó, nhưng kết quả của nó chỉ nói về commit mà nó chạy trên. Lượt mặc định phải gọi tay vì commit cuối của giai đoạn có thể chỉ đổi ảnh và kiểm thử dưới `app/src/test/`, là đường dẫn không kích hoạt quy trình (`device-tests.yml:26-36`).
- Hồ sơ khởi động đã commit mô tả mã cũ, và bản phát hành mang theo tệp đã commit. Mọi sửa đổi mã app sau lúc ghi làm hồ sơ cũ đi, nên hồ sơ đi kèm bản phát hành được ghi ở giai đoạn 10, như thay đổi cuối cùng của mã app.

## Requirements

- Bài `SplitFlowOnDeviceTest.aPartTypedIntoTheLastRowStaysAboveTheBarAndTheKeyboard` (gõ vào ô trong hàng khi bàn phím mở) bị bỏ qua ở mọi lượt trên máy ảo hiện nay: máy ảo Android 14, 11 và 16 dùng ảnh hệ thống ATD, vốn không có bàn phím nào, còn ảnh mặc định của Android 8.0, có bàn phím, chỉ chạy gói `e2e`. Giai đoạn này cho bài đó chạy được ở ít nhất một bản, ví dụ chuyển nó vào gói `e2e` để lượt chạy tay ở Android 8.0 chạy nó, và ghi lại kết quả.
<!-- Updated: Thực hiện giai đoạn 5 - bài gõ khi bàn phím mở bị bỏ qua vì ảnh ATD không có bàn phím -->
- Toàn bộ ảnh tham chiếu được ghi lại một lần, trong giai đoạn này, bằng `-Pscreenshots=record`; sau lượt đó một sửa đổi chỉ ghi lại những ảnh mà nó làm đổi. Từ đây kiểm thử chạy ở chế độ so mặc định như CI và không còn dùng `-Pscreenshots=compare`.
- Mỗi màn hình của app có ảnh sáng, và các màn đang có bản tối tiếp tục có bản tối. Bốn màn chính (trang chủ, màn thêm hóa đơn, màn kết quả, màn đòi tiền) và màn xin đồng ý có ảnh chữ cỡ 200%. Sáu ảnh ngôn ngữ giả `en-XA` vẫn còn. Trang mẫu thành phần có ảnh sáng, tối và cỡ chữ 200% (`catalog-large-text`, bài chụp có từ giai đoạn 2).
- Các trạng thái sau có ảnh, và trạng thái nào chưa có bài chụp thì được thêm ở đây: trang chủ lần đầu (bảng `HomeEmpty`), trang chủ khi chưa đăng nhập, màn hóa đơn với thẻ "Recurring" đang mở, hộp thoại xóa tài khoản (bảng `DeleteAccount`), và bốn màn chính trên máy đặt vùng Việt Nam (`vi-VN`). Không có ảnh màn giấy phép phông, vì app không có màn đó.
- Trên ảnh `vi-VN`, số tiền có dạng "902.664 ₫" và ngày có dạng "13 Nov"; trên các ảnh chụp với vùng Mỹ mặc định là "₫902,664" và "Nov 13". Ảnh trang chủ `vi-VN` có ít nhất một hóa đơn, để thấy cả số tiền lẫn ngày.
- Không còn ảnh nào trong thư mục mà không bài kiểm thử nào ghi.
- Ở cỡ chữ 200%, không chữ nào bị cắt trên bốn màn chính. Thanh điều hướng chỉ hiện biểu tượng khi nhãn không vừa một dòng, và số tiền chính co cỡ hoặc xuống dòng thứ hai chứ không bị cắt.
- Mọi cặp chữ và nền thấy trên ảnh tối đạt 4,5:1.
- Chủ sản phẩm duyệt các bảng ảnh so với `boards/`, và duyệt qua ảnh thật các trạng thái không có bảng riêng: trang chủ khi chưa đăng nhập, mà khối chính cũng là khối chính của trang chủ khi chưa tải xong yêu cầu, và các ảnh cỡ chữ 200%.
- Kiểm thử trên máy ảo chạy bằng hai lượt gọi tay của `device-tests.yml` trên commit cuối của giai đoạn. Ở lượt mặc định, việc `android-14`, có kiểm tra trợ năng, và việc `profile` đều xanh. Ở lượt chọn `api-levels`, kết luận của từng việc API 26, 30 và 36 được đọc riêng và ghi lại; trạng thái của cả lượt không được dùng làm kết quả.
- Kiểm tra trợ năng trong `AppLaunchTest` đi qua cả bốn màn gốc của thanh điều hướng.
- Hồ sơ khởi động không được commit ở giai đoạn này.
- Hai phông chữ được xem bằng mắt trên máy ảo API 26: tiêu đề lớn và số tiền vẽ bằng Bricolage Grotesque, phần chữ còn lại bằng Be Vietnam Pro, dấu tiếng Việt và ký hiệu "₫" vẽ đúng.
- Cuối giai đoạn, giá trị token màu được chốt. Sau đó đổi một giá trị trong `SvColors.kt` hoặc `SvPalette.kt` là một yêu cầu thay đổi, vì ảnh tham chiếu và trang web của giai đoạn 9 đều dựng trên chúng.
- Kích thước gói tăng thêm so với `main` được đo và ghi lại.

## Files to Create / Modify

- Modify: `app/src/test/screenshots/*.png`. Mọi ảnh được ghi lại; sáu tệp bị xóa là `07-pick-people.png`, `07-pick-people-dark.png`, `08-item-sharers.png`, `08-item-sharers-dark.png`, `09-sent-to-claude.png` và `09-sent-to-claude-dark.png`.
- Modify: `ui/ScreenGalleryTest.kt` để chụp trang chủ lần đầu, trang chủ khi chưa đăng nhập, hộp thoại xóa tài khoản mở từ Cài đặt khi đã đăng nhập, và bốn màn chính với qualifier `vi-rVN-w412dp-h915dp-xhdpi` (hậu tố tên tệp `-vi-vn`).
- Modify: `ui/SpendingGalleryTest.kt` để chụp màn hóa đơn với thẻ "Recurring" đang mở, nếu giai đoạn 7 chưa để lại bài chụp đó.
- Modify: `app/src/androidTest/java/com/dantech/splitvibe/AppLaunchTest.kt` để kiểm tra trợ năng đi qua bốn tab.
- Modify, chỉ khi lượt soát ảnh tối tìm ra lỗi tương phản: `tokens/SvColors.kt`, `tokens/SvPalette.kt` hoặc thành phần `Sv…` liên quan trong `:core:designsystem`, kèm `web/src/tokens.css` khi một vai màu của Material đổi.
- Modify, chỉ khi việc `profile` hỏng vì một nhãn đã đổi: `baseline-profile/src/main/java/com/dantech/splitvibe/baselineprofile/Journeys.kt`.
- Không sửa ở giai đoạn này: `app/src/prodRelease/generated/baselineProfiles/startup-prof.txt` và `baseline-prof.txt`.

## Implementation Steps

1. Thêm các bài chụp còn thiếu theo mục Requirements. Ở những màn có tổng hóa đơn, bài chụp `vi-VN` khẳng định trước khi chụp rằng tổng được viết kiểu Việt Nam, tức có "902.664" đi cùng "₫" và không còn "₫902,664", để một ảnh kiểu Mỹ không bị ghi nhầm làm ảnh tham chiếu.
2. Ghi lại một lần. Xóa mọi tệp `*.png` trong `app/src/test/screenshots/` của cây làm việc, rồi chạy `./gradlew :app:testProdDebugUnitTest -Pscreenshots=record`. Sau đó đọc `git status --porcelain app/src/test/screenshots`: sáu tệp nêu ở mục Files phải nằm trong số tệp bị xóa. Mỗi tệp bị xóa khác phải có lý do đã biết, tức một bài chụp được đổi tên hoặc gỡ có chủ ý ở giai đoạn trước; nếu không, đó là một bài chụp bị mất và phải tìm ra trước khi commit.
3. Chạy chế độ so mặc định hai lần: `./gradlew :core:domain:test :core:designsystem:testDebugUnitTest :app:testProdDebugUnitTest`, rồi `./gradlew :app:testProdDebugUnitTest --rerun`. Cả hai lượt phải qua. Commit ảnh cùng các bài chụp mới.
4. Soát ảnh đã ghi: từng ảnh tối (chữ trên thẻ, nhãn, thanh nổi, biểu đồ), các ảnh cỡ chữ 200%, sáu ảnh `en-XA`, và các ảnh `vi-VN` (dạng số tiền và ngày, không có ký tự lạ cạnh ký hiệu tiền). Lỗi được sửa trong token hoặc thành phần, không sửa trong màn hình. Gom mọi sửa rồi mới ghi lại một lượt; sau lượt đó `git status` chỉ được báo những ảnh mà các sửa đổi nhắm tới. Khi một vai màu của Material đổi, ghi lại `web/src/tokens.css` bằng `./gradlew :core:designsystem:testDebugUnitTest -PwebTokens=write` và chạy thêm các lệnh của việc `proxy` trong `ci.yml`.
5. Ghép ảnh thành các bảng xem nhanh theo nhóm màn, sáng và tối, rồi gửi chủ sản phẩm so với `boards/`. Sửa mọi lệch mà chủ sản phẩm chỉ ra và ghi lại ảnh của màn đó.
6. Sửa `AppLaunchTest` để đi qua bốn tab của thanh điều hướng, tìm tab bằng vai `Tab` cùng nhãn, cho kiểm tra trợ năng chạy trên từng màn gốc.
7. Thử tay trên máy ảo. Trên máy ảo API 26 với ảnh hệ thống `default`, như ma trận của `device-tests.yml` dùng: mở trang chủ và màn kết quả của hóa đơn mẫu, chụp màn hình, rồi so hai phông với ảnh tham chiếu của cùng màn. Trên máy ảo Android 14 trở lên: hiệu ứng nở từ nút giữa, vuốt lùi có xem trước từ một màn gốc về Home, bảng trượt kéo theo tay, và app chạy được khi tắt chuyển động.
8. Chạy cổng kiểm thử của plan ở chế độ so mặc định, cho một lượt review độc lập, sửa các phát hiện, rồi chạy lại cổng trên commit cuối.
9. Chốt token màu: ghi mã commit chốt vào báo cáo của giai đoạn.
10. Đẩy commit cuối của giai đoạn, rồi gọi tay hai lượt: `gh workflow run device-tests.yml --ref feat/ui-redesign` và `gh workflow run device-tests.yml --ref feat/ui-redesign -f api-levels=true`. Không đẩy gì lên nhánh cho tới khi cả hai lượt xong. Với mỗi lượt, đọc `gh run view <mã lượt chạy> --json headSha,jobs`: `headSha` phải là commit cuối, và kết luận được đọc theo từng việc. Một hành trình hỏng ở API 26, 30 hoặc 36 được tìm nguyên nhân trong báo cáo `e2e-api-<mức>`: lỗi do giao diện mới thì sửa, chạy lại cổng kiểm thử, rồi chạy lại cả hai lượt trên commit mới; lỗi của máy ảo thì ghi lại kèm bằng chứng. Hồ sơ mà việc `profile` ghi ra không được commit.
11. Đo cỡ tệp `app-prod-release-unsigned.apk` ở commit cuối của giai đoạn và ở commit mà nhánh tách khỏi `main`, rồi viết báo cáo của giai đoạn: commit chốt token, số chênh cỡ gói, kết luận của từng việc trên máy ảo và kết quả xem phông.

## Todo

- [x] Các bài chụp còn thiếu, có lời khẳng định cho ảnh `vi-VN`
- [x] Ghi lại một lần vào thư mục đã dọn trống, sáu ảnh thừa bị xóa, không bài chụp nào bị mất
- [x] Chế độ so mặc định qua hai lần, lần hai ép chạy lại; ảnh được commit
- [x] Ảnh tối, cỡ chữ 200%, `en-XA` và `vi-VN` đã soát; các sửa gom vào một lượt ghi lại
- [ ] Chủ sản phẩm duyệt các bảng ảnh và các trạng thái không có bảng riêng
- [x] `AppLaunchTest` đi qua bốn tab
- [ ] Hai phông xem bằng mắt trên API 26, các chuyển động thử tay
- [ ] Cổng kiểm thử ở chế độ so mặc định, review độc lập, sửa phát hiện
- [ ] Token màu được chốt, mã commit ghi trong báo cáo
- [ ] Hai lượt `device-tests.yml` trên commit cuối, kết luận đọc theo từng việc
- [ ] Cỡ gói được đo, báo cáo của giai đoạn được viết

## Success Criteria

- Cổng kiểm thử của plan qua trên commit cuối của giai đoạn ở chế độ so mặc định, không còn `-Pscreenshots=compare`, và lượt so ép chạy lại cũng qua.
- `git status --porcelain app/src/test/screenshots` rỗng sau các lượt so. Thư mục không còn sáu ảnh thừa và có đủ các ảnh mới của mục Requirements.
- Lượt mặc định của `device-tests.yml` trên commit cuối có việc `android-14` và việc `profile` kết luận `success`. Lượt chọn `api-levels` trên cùng commit có kết luận của từng việc API 26, 30 và 36 ghi trong báo cáo, kèm nguyên nhân của việc nào hỏng.
- Chủ sản phẩm xác nhận các bảng ảnh khớp với `boards/` và duyệt các trạng thái không có bảng riêng.
- Báo cáo của giai đoạn nêu commit chốt token màu, số chênh cỡ gói và kết quả xem hai phông trên API 26.
- Không commit nào của giai đoạn đụng tới `app/src/prodRelease/generated/baselineProfiles/`.

## Risk Assessment

- Ảnh không ổn định giữa hai lần chạy vì phông hoặc chuyển động: dấu hiệu là lượt so hỏng ngay sau khi ghi. Ứng phó: dừng đồng hồ như `ScreenGalleryTest` đang làm cho màn đang đọc, hoặc tắt chuyển động của thành phần gây lệch trong bài chụp.
- Một sửa màu sau lượt ghi làm đổi hàng chục ảnh: bước 4 gom mọi sửa vào một lượt ghi lại. Nếu chủ sản phẩm cho giai đoạn 9 chạy song song, mỗi sửa đổi token ở đây được báo cho người làm giai đoạn đó, vì `web/src/tokens.css` đổi theo.
- Ảnh `vi-VN` mang kiểu Mỹ hoặc có ký tự lạ cạnh "₫": lời khẳng định ở bước 1 và lượt soát ở bước 4 bắt được. Lỗi thuộc về hàm định dạng của giai đoạn 4 hoặc về phông, và được sửa ở đó, không sửa trong màn hình.
- Máy ảo API 26 vẽ phông khác: việc API 26 của lượt đo chỉ cho biết app không sập, vì một phông không nạp được có thể lặng lẽ nhường chỗ cho phông hệ thống. Bước 7 là để thấy điều đó bằng mắt, và phông sai được sửa trong `:core:designsystem` trước khi giai đoạn kết thúc.
- Máy của người thực hiện không chạy được máy ảo vì thiếu ảo hóa phần cứng: bước 7 không được đánh dấu là xong. Báo chủ sản phẩm, kèm danh sách màn cần xem, để việc xem được làm trên một máy có máy ảo.
- Việc `profile` hỏng vì hành trình còn bám một nhãn đã đổi: sửa `Journeys.kt` trong giai đoạn này và chạy lại lượt mặc định.
- Một lần đẩy trong lúc lượt mặc định đang chạy hủy lượt đó: dấu hiệu là lượt có trạng thái `cancelled`. Ứng phó: gọi lại lượt trên commit cuối và không đẩy cho tới khi xong.
- Ngân sách khung hình trên máy tham chiếu là việc của chủ sản phẩm theo `docs/release-checklist.md`; giai đoạn này không đo được.

## Rollback

Hoàn lại các commit của giai đoạn chỉ dùng được khi giai đoạn 9 chưa bắt đầu; sau đó lỗi được sửa bằng commit mới. Ảnh tham chiếu phải được hoàn lại cùng với mã mà chúng chụp. Hoàn lại cả giai đoạn đưa thư mục về bộ ảnh cũ, là bộ không khớp với giao diện mới, nên từ lúc đó kiểm thử lại phải chạy với `-Pscreenshots=compare` cho tới khi ảnh được ghi lại.
