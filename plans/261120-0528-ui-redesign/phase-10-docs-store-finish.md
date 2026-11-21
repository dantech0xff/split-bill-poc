---
title: "Phase 10: Tài liệu, ảnh cửa hàng và hoàn tất"
status: pending
phase: 10
priority: P2
effort: "2d"
dependencies: [9]
---

# Phase 10: Tài liệu, ảnh cửa hàng và hoàn tất

## Overview

Tài liệu mô tả giao diện mới, ba tài liệu pháp lý được đối chiếu với hành vi mới của app, bảng ảnh cho Google Play và ảnh trong README được thay, hồ sơ khởi động được ghi từ commit cuối cùng của mã app, và nhánh sẵn sàng để chủ sản phẩm cho merge. Giai đoạn này không sửa và không soát lại trang web: toàn bộ việc đó thuộc giai đoạn 9, ở đây chỉ còn việc cập nhật `docs/web.md`.

## Context

- Tài liệu có nói về giao diện, bảng ảnh cửa hàng và hồ sơ khởi động: [rà kiểm thử, CI, web và tài liệu](./reports/scout-tests-ci-web-docs.md), mục 4, 6 và 7.
- Cái giá về minh bạch khi gỡ đoạn chữ đã gửi, và các quy tắc soát ảnh cửa hàng: [phản biện về bảo mật](./reports/red-team-security.md).
- Thứ tự ghi hồ sơ khởi động và giới hạn của việc hoàn lại: [phản biện về kiểu hỏng](./reports/red-team-failure-modes.md).
- Những gì tài liệu phải phản ánh: mục "Quyết định của chủ sản phẩm" và mục "Cổng kiểm thử" của [plan](./plan.md).
- Giai đoạn 9 đã xong, nên `docs/web.md` mô tả được trang web mới.

## Key Insights

- App đã đổi hành vi ở bốn điểm mà tài liệu pháp lý có thể đang mô tả khác: app không còn cho xem đoạn chữ đã gửi đi; danh sách yêu cầu trả tiền được tải khi mở app, không chỉ khi mở màn Requests; chữ "Claude" chỉ còn ở màn xin đồng ý và mục đồng ý trong Cài đặt; số tiền và ngày viết theo vùng của máy.
- Ba chỗ mà bản phản biện chỉ ra, `docs/privacy-policy.md:56`, `docs/privacy-policy.md:216` và `docs/compliance.md:60`, hôm nay nói về những gì được gửi và những gì xem được trong app (nhóm, yêu cầu, liên kết). Chúng không hứa cho xem đoạn chữ đã gửi, nên có thể không phải sửa; việc của giai đoạn này là đọc trọn ba tài liệu rồi mới kết luận.
- Hai tài liệu còn mô tả cách viết số cũ: phần "Texts and languages" của `docs/architecture.md` nói số được viết kiểu tiếng Anh bất kể vùng của máy, và phần "The end-to-end journeys" của `docs/testing.md` có câu "which is in English whatever the phone's language".
- Bảy ảnh của bảng ảnh cho Google Play lấy từ ảnh tham chiếu, hai trong số đó được cắt tay từ ảnh dài (`docs/store-listing.md:85-98`). Ảnh giữ cỡ 824 px; ảnh 1080 px là việc chủ sản phẩm quyết sau.
- Giao diện mới cho thấy nhiều dữ liệu cá nhân và dữ liệu thanh toán hơn. Trên các bảng đã duyệt, `RequestLink`, `PaymentMethods` và `BillLinkClosed` có số tài khoản mẫu, `RequestLink` có mã thanh toán, `Settings` và `SettingsStates` có địa chỉ thư điện tử, `BillLink` và `BillLinkClosed` có địa chỉ `proxy.example`. Bài chụp màn yêu cầu và màn cách nhận tiền dùng dữ liệu mẫu có dạng một tài khoản thật, gồm mã ngân hàng và số tài khoản, nên mã VietQR trong ảnh có thể quét được (`ui/GroupsGalleryTest.kt:119`, `:135-137`).
- Ảnh số 1 của bảng hôm nay, `03-photo-and-instruction.png`, cho đọc rõ địa chỉ và số điện thoại in trên hóa đơn mẫu (đã xem ảnh ngày 20/11/2026). Theo quy tắc mới, ảnh của màn này chỉ dùng được khi hai dòng đó không còn đọc được.
- `docs/screenshots/new-split.png` trong README không khớp ảnh tham chiếu nào hiện có.
- Việc `profile` của `device-tests.yml` không chạy trong lượt chọn `api-levels` (`device-tests.yml:97`), và một lần đẩy mới trên cùng nhánh hủy lượt mặc định đang chạy (`device-tests.yml:42-46`). Mọi sửa đổi mã app sau lúc ghi làm hồ sơ cũ đi, nên hồ sơ được ghi sau lượt review cuối, khi mã app không còn đổi.
- `ci.yml` chỉ chạy cho pull request và cho `main` (`ci.yml:8-12`). Trước khi merge, bằng chứng duy nhất là các lệnh của nó chạy tại chỗ.
- Trang web chỉ tới người dùng khi Worker được triển khai lại, việc chỉ xảy ra trong quy trình phát hành (`release.yml:354-358`). Bản phát hành đầu tiên sau khi merge vì thế cũng đưa các trang web mới lên, kể cả khi bản đó được cắt vì một lý do khác.

## Requirements

Tài liệu viết lại hoặc cập nhật:

- `docs/design-system.md`: bảng màu, ba họ phông, hình khối, mức nổi, `SvPalette` (cả `moneyInBar`), mọi thành phần (cả `SvBesideOrBelow`, lớp nền `SvScreenLayer` và tham số `autofill` của `SvTextField` cho ô không được tự điền) và quy tắc màu nút mới. <!-- Updated: thêm theo lượt sửa của giai đoạn 6 --> Bảng thành phần (`docs/design-system.md:148-164`) khớp với thư mục `components/`. Tài liệu nêu nguồn gốc của phông: kho, commit ghim, và tệp `core/designsystem/font-checksums.sha256` giữ mã băm; nó không chép lại từng mã băm.
- `docs/architecture.md`: điều hướng là một ngăn xếp với bốn màn gốc dưới thanh điều hướng; danh sách khóa ở `docs/architecture.md:157-159` không còn `RecurringKey`; phần "Texts and languages" nói số tiền và ngày theo vùng của máy; phần về ảnh tham chiếu (`docs/architecture.md:582-603`) khớp với bộ ảnh mới.
- `docs/testing.md`: cổng kiểm thử chạy tại chỗ, chế độ `-Pscreenshots=compare` và lúc nào dùng nó, hai lượt chạy của `device-tests.yml` và cách đọc kết luận của từng việc, và câu về cách viết tiền trong các hành trình. Thêm bốn chỗ mà review giai đoạn 8 chỉ ra: vòng bốn tab của `AppLaunchTest`, các lớp chạy kiểm tra trợ năng, `KeyboardOnDeviceTest` và `ScreenshotsOnDeviceTest` trong bảng hành trình, và ảnh mà artifact `e2e-api-<mức>` giữ lại. <!-- Updated: thêm theo review giai đoạn 8 -->
- `docs/web.md`: trang web như giai đoạn 9 để lại; nếu giai đoạn 9 đã cập nhật tệp này thì chỉ đối chiếu lại.
- `README.md`: ảnh ở `README.md:30`, đoạn về ảnh tham chiếu (`README.md:394-397`) và các lệnh.
- `docs/store-listing.md`: bảng bảy ảnh mới với tên tệp, kích thước và nội dung.
- `docs/play-console.md`: câu về giá lấy từ ảnh tham chiếu (`docs/play-console.md:201`) còn đúng với ảnh mới.

Tài liệu đọc và xác nhận, chỉ sửa chỗ sai:

- `docs/privacy-policy.md`, `docs/compliance.md` (bắt đầu từ `docs/compliance.md:60`) và mục "Data safety" của `docs/play-console.md` được đọc trọn và đối chiếu với bốn điểm hành vi mới ở mục Key Insights. Chỉ câu sai mới được sửa, và kết luận cho từng tài liệu được ghi vào báo cáo, kể cả khi không sửa gì.
- Chủ sản phẩm đã quyết rằng chính sách quyền riêng tư giữ nguyên từng chữ và tiếp tục nêu rõ Claude và Anthropic. Vì vậy một câu sai tìm thấy trong `docs/privacy-policy.md` không được tự sửa: câu đó, hành vi mới và các phương án được nêu cho chủ sản phẩm, và việc sửa chờ quyết định. Câu nào bị sửa trong `docs/compliance.md` hoặc `docs/play-console.md` được nêu riêng trong báo cáo.

Ảnh cửa hàng:

- Bảy ảnh được chọn lại từ ảnh tham chiếu mới và giữ cỡ 824 px; hai ảnh dài được cắt về 824 × 1830 như tài liệu mô tả.
- Hai quy tắc ở `docs/store-listing.md:111-115` được giữ nguyên từng chữ khi bảng được viết lại:
  - "Before publishing, check that the sample data is fictional: the sample bill names a restaurant, an address and a phone number in Ho Chi Minh City."
  - "Left out on purpose: `28-bill-link.png` and `29-bill-link-settling.png` show the test address `https://proxy.example/s/#AAAA…`, a closing date of 1 January 2100 and an example account number, which do not belong on a store page without new recordings."
- Một quy tắc mới được thêm ngay sau hai quy tắc đó: không ảnh nào có mã QR, địa chỉ thư điện tử, số tài khoản, hay địa chỉ hoặc số điện thoại của hóa đơn mẫu. Trong tài liệu, quy tắc được viết bằng tiếng Anh như phần còn lại: "No image with a QR code, an email address, an account number, or the sample bill's address or phone number."
- README dùng một ảnh lấy từ ảnh tham chiếu hiện hành.

Hoàn tất:

- Thứ tự bắt buộc: review độc lập trên toàn bộ nhánh, sửa các phát hiện, ghi hồ sơ khởi động từ commit cuối cùng của mã app, chạy tại chỗ mọi lệnh của `ci.yml`, viết nhật ký, rồi dừng chờ chủ sản phẩm nói "ok merge".
- Không mở pull request khi chủ sản phẩm chưa yêu cầu.
- Nhật ký và báo cáo cuối ghi rõ rằng bản phát hành đầu tiên sau khi merge cũng triển khai Worker cùng các trang web mới.
- Bản ghi thiết kế lại và plan này phản ánh kết quả thật.

## Files to Create / Modify

- Modify: `docs/design-system.md`, `docs/architecture.md`, `docs/testing.md`, `docs/web.md`, `docs/store-listing.md`, `docs/play-console.md`, `README.md`.
- Modify, chỉ khi lượt đối chiếu tìm ra câu sai: `docs/compliance.md` và mục "Data safety" của `docs/play-console.md`. `docs/privacy-policy.md` chỉ được sửa sau khi chủ sản phẩm quyết.
- Modify: `docs/screenshots/new-split.png`.
- Modify: `app/src/prodRelease/generated/baselineProfiles/startup-prof.txt` và `baseline-prof.txt`.
- Modify: `plans/reports/brainstorm-261120-0148-ui-redesign.md`, `plans/261120-0528-ui-redesign/plan.md` và mười tệp giai đoạn (trạng thái và kết quả).
- Create: một mục nhật ký trong `plans/journals/` qua `ak journal create`.
- Không sửa ở giai đoạn này: mọi tệp dưới `web/` và `proxy/`.

## Implementation Steps

1. Viết lại `docs/design-system.md` theo mã sau giai đoạn 8. Đối chiếu bảng thành phần với thư mục `components/`, và phần về phông với `core/designsystem/font-checksums.sha256`.
2. Cập nhật `docs/architecture.md`, `docs/testing.md`, `docs/web.md`, `README.md` và `docs/play-console.md`. Với mỗi câu nói về giao diện hoặc lệnh, đối chiếu với mã, bài kiểm thử hoặc quy trình CI.
3. Đọc trọn `docs/privacy-policy.md`, `docs/compliance.md` và mục "Data safety" của `docs/play-console.md`, đối chiếu với bốn điểm hành vi mới, và ghi kết luận cho từng tài liệu. Câu sai trong chính sách quyền riêng tư được nêu cho chủ sản phẩm kèm các phương án; câu sai trong hai tài liệu kia được sửa và nêu trong báo cáo.
4. Chọn lại bảy ảnh cho Google Play từ ảnh tham chiếu mới và soát từng ảnh theo ba quy tắc. Ghi lại bảng trong `docs/store-listing.md`, giữ nguyên hai quy tắc cũ và thêm quy tắc mới, rồi gửi bảy ảnh cho chủ sản phẩm duyệt. Thay `docs/screenshots/new-split.png` bằng một ảnh tham chiếu hiện hành.
5. Cho một lượt review độc lập trên toàn bộ thay đổi của nhánh so với `main`, sửa các phát hiện và chạy lại các kiểm tra bị ảnh hưởng.
6. Ghi hồ sơ khởi động. Đẩy commit cuối của mã app, gọi `gh workflow run device-tests.yml --ref feat/ui-redesign`, và không đẩy gì cho tới khi lượt đó xong. Khi việc `profile` xanh, lấy `headSha` bằng `gh run view <mã lượt chạy> --json headSha,jobs`; lệnh `git diff --stat <headSha> HEAD -- app/src/main core baseline-profile app/build.gradle.kts gradle/libs.versions.toml` phải không in gì. Tải hồ sơ bằng `gh run download <mã lượt chạy> -n baseline-profile`, chép hai tệp vào `app/src/prodRelease/generated/baselineProfiles/` và commit. Kết luận của việc `android-14` trong cùng lượt cũng được đọc và ghi lại. Sau commit này không commit nào được đổi mã app; nếu buộc phải đổi, ghi lại hồ sơ.
7. Chạy tại chỗ mọi lệnh của `ci.yml` trên commit mang hồ sơ khởi động, ở chế độ so mặc định: việc `checks`, tức các lệnh ở mục "Cổng kiểm thử" của plan kèm bước "No Anthropic code in the packages", và việc `proxy` (`ci.yml:111-165`). Ghi kết quả vào plan.
8. Cập nhật trạng thái và kết quả trong `plan.md`, mười tệp giai đoạn và bản ghi thiết kế lại. Viết nhật ký bằng `ak journal create`, trong đó có câu về việc Worker được triển khai cùng bản phát hành đầu tiên sau khi merge. Các commit của bước này chỉ đụng `plans/`.
9. Dừng lại chờ chủ sản phẩm nói "ok merge". Không mở pull request khi chưa được yêu cầu. Sau khi nhánh được merge, đọc lượt `ci.yml` trên `main`.

## Todo

- [ ] `docs/design-system.md` viết lại, có nguồn gốc của phông
- [ ] `docs/architecture.md`, `docs/testing.md`, `docs/web.md`, `README.md` và `docs/play-console.md` cập nhật
- [ ] Ba tài liệu pháp lý đọc trọn, kết luận ghi cho từng tài liệu
- [ ] Bảy ảnh cho Google Play soát theo ba quy tắc, bảng ghi lại, chủ sản phẩm duyệt; ảnh README thay
- [ ] Review độc lập trên toàn bộ nhánh và sửa phát hiện
- [ ] Hồ sơ khởi động ghi từ commit cuối của mã app, `headSha` đã kiểm
- [ ] Mọi lệnh của `ci.yml` qua tại chỗ
- [ ] Plan, tệp giai đoạn và bản ghi thiết kế lại cập nhật; nhật ký viết xong
- [ ] Dừng chờ "ok merge", không mở pull request; đọc CI trên `main` sau khi merge

## Success Criteria

- Mọi lệnh ghi trong tài liệu chạy được, và mọi tệp ảnh mà tài liệu nhắc tới đều tồn tại.
- Báo cáo có kết luận cho từng tài liệu trong ba tài liệu pháp lý, và mọi câu sai đã được nêu cho chủ sản phẩm.
- Không ảnh nào trong bảy ảnh vi phạm ba quy tắc của `docs/store-listing.md`, và chủ sản phẩm đã duyệt bảy ảnh.
- Hai tệp hồ sơ khởi động đã commit đến từ một lượt chạy mà `headSha` mang đúng mã app cuối cùng, và sau commit đó không commit nào đổi mã app.
- Mọi lệnh của `ci.yml` qua khi chạy tại chỗ trên commit mang hồ sơ khởi động, và các commit sau đó chỉ đụng `plans/`.
- Nhật ký tồn tại và nêu việc Worker được triển khai cùng bản phát hành đầu tiên sau khi merge.
- Không pull request nào được mở khi chưa có yêu cầu. Sau khi chủ sản phẩm nói "ok merge" và nhánh được merge, CI trên `main` xanh ở cả hai việc.

## Risk Assessment

- Hồ sơ khởi động ghi từ mã không phải mã cuối, vì một lần đẩy hủy lượt ghi hoặc một sửa đổi đến sau lúc ghi: dấu hiệu là lượt có trạng thái `cancelled`, hoặc lệnh `git diff` của bước 6 in ra tệp. Ứng phó: ghi lại hồ sơ, không dùng hồ sơ của một lượt cũ hơn.
- Một câu trong tài liệu pháp lý bị sửa quá tay: chỉ câu sai với hành vi mới mới được đụng tới, chính sách quyền riêng tư chờ chủ sản phẩm quyết, và phần nêu tên Claude và Anthropic không đổi.
- Quy tắc mới loại ảnh bước chụp hóa đơn, là ảnh mở đầu bảng hôm nay: bảng mở đầu bằng một ảnh khác, và thay đổi này được nêu rõ khi gửi bảy ảnh cho chủ sản phẩm duyệt.
- Quy tắc "Left out on purpose" gọi tên hai tệp ảnh. Nếu một giai đoạn trước đã đổi tên hai bài chụp đó, câu giữ nguyên từng chữ sẽ trỏ tới tệp không còn: nêu cho chủ sản phẩm thay vì tự sửa câu.
- Tài liệu nói điều mã không làm: mỗi câu về giao diện hoặc lệnh được đối chiếu với nguồn ở bước 2, và lệnh nào ghi trong tài liệu cũng được chạy thử.
- Trang web đang chạy khác app mới cho tới lần triển khai Worker kế tiếp: ghi rõ trong nhật ký; triển khai sớm hơn là quyết định của chủ sản phẩm.
- Lệnh của `ci.yml` qua tại chỗ nhưng hỏng trên `main`, vì máy sạch của GitHub không có bộ đệm của máy tại chỗ: đọc lượt chạy trên `main` ngay sau khi merge và sửa bằng commit mới.

## Rollback

Đây là giai đoạn cuối, nên mốc không phải là giai đoạn kế tiếp mà là lúc nhánh được merge: trước đó hoàn lại các commit của giai đoạn là đủ, sau đó lỗi được sửa bằng commit mới. Commit hồ sơ khởi động không được hoàn lại riêng, vì việc đó đưa về hồ sơ của mã cũ; khi mã app đổi sau lúc ghi, hồ sơ được ghi lại. Giai đoạn này không triển khai gì, nên trang web đang chạy và bản trên Google Play không đổi cho tới bản phát hành kế tiếp.
