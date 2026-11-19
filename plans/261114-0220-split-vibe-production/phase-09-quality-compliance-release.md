---
title: "Phase 9: Chất lượng, tuân thủ và phát hành"
status: in-progress
phase: 9
priority: P1
effort: "9d"
dependencies: [2, 3, 4, 5, 6, 7, 8]
---

# Phase 9: Chất lượng, tuân thủ và phát hành

## Overview

<!-- Updated: 19/11/2026 - chủ sản phẩm chốt ba điểm khi bắt đầu phase -->
> **Đã chốt ngày 19/11/2026:**
> - Hộp xác nhận xóa tài khoản có thêm ô "Also delete the bills, people and payment methods on this phone", mặc định không chọn. Có chọn thì app xóa mọi dữ liệu cá nhân chỉ nằm trên máy; không chọn thì những dữ liệu đó ở lại.
> - Xóa tài khoản không hủy thuê bao Pro. App chỉ cảnh báo trước, như đã làm ở Phase 8.
> - Việc đo sử dụng dùng số đếm tổng hợp trên server (Workers Analytics Engine của Cloudflare), không kèm mã người dùng. App không có SDK analytics nào, nên không cần chế độ đồng ý.
>
> Quyết định kỹ thuật của người làm: trang web xóa tài khoản dùng nút "Sign in with Google" rồi xóa thẳng. Server không lưu email nào, nên một yêu cầu qua email không chỉ ra được tài khoản nào, mà Play lại đòi xóa được không cần cài lại app.
>
> <!-- Updated: 19/11/2026 - chủ sản phẩm chốt thêm bốn điểm sau các bản review -->
> **Chốt thêm ngày 19/11/2026:**
> - Thời hạn lưu 12 tháng. Số lượt thử của một cài đặt không gọi AI trong 12 tháng thì bị xóa. Bản chụp cách nhận tiền của một yêu cầu đã xác nhận quá 12 tháng cũng bị xóa; số tiền và việc đã trả thì giữ cho sổ nợ của nhóm.
> - Link yêu cầu trả tiền trên web hết hạn sau 90 ngày. Yêu cầu vẫn nằm trong app của hai người.
> - Có màn xin đồng ý đơn giản trước lần đầu app gọi AI: văn bản đọc được từ hóa đơn sẽ được gửi tới Anthropic ở Mỹ để xử lý, kèm link tới chính sách quyền riêng tư, với hai lựa chọn là đồng ý hoặc tự nhập tay. Màn này không chặn tính năng nào khác, và người dùng rút lại được trong Settings. Người tư vấn pháp lý sẽ sửa câu chữ sau.
> - Bản 1.0 phát hành toàn cầu ngay, vì Play không cho chia nấc ở lần đầu đăng app. Việc chia nấc 5%, 20%, 50%, 100% bắt đầu từ bản cập nhật đầu tiên.

Split Vibe 1.0 lên Google Play cho toàn cầu: qua đủ cổng chất lượng, có hồ sơ quyền riêng tư và pháp lý, qua vòng thử kín bắt buộc, phát hành theo từng nấc có theo dõi, và có quy trình vận hành sau ra mắt.

## Context

- Các phần còn treo từ POC được đóng ở các giai đoạn trước: lần gọi API thật (Phase 3), test trên thiết bị chạy bằng Gradle (Phase 1). Giai đoạn này kiểm lại toàn bộ trên bản release.
- Dữ kiện: [báo cáo giao diện và Play](../reports/researcher-261114-0209-android-expressive-ui-and-play-requirements.md), mục 5; [báo cáo backend](../reports/researcher-261114-0209-product-backend-and-payment-links.md), mục 5.
- Một số việc trong giai đoạn này **bắt đầu sớm**, song song với Phase 5 đến Phase 8: vòng thử kín (cần ít nhất 14 ngày), chính sách quyền riêng tư, hồ sơ Play.

## Key Insights

- Tài khoản nhà phát triển cá nhân tạo sau 13/11/2023 phải có ít nhất **12 người thử tham gia liên tục 14 ngày** trước khi xin quyền phát hành. Plan giả định trường hợp này; nếu tài khoản của chủ sản phẩm được miễn thì bước đó bỏ qua. Đây là đường găng của lịch phát hành.
- App nhắm API 36 là đạt yêu cầu hiện hành. Thư viện gốc đã căn 16 KB (kiểm ngày 14/11/2026: 8 trên 8 thư viện đạt); kiểm lại trên AAB cuối.
- Gửi văn bản hóa đơn tới API của model được tính là **thu thập dữ liệu** trong biểu mẫu An toàn dữ liệu, dù chỉ xử lý tạm thời.
- App có tài khoản nên phải có xóa tài khoản trong app **và** một trang web để yêu cầu xóa.
- Phục vụ người dùng ở EU thì nhiều khả năng cần đại diện tại EU (GDPR Điều 27). Lưu dữ liệu người dùng Việt Nam trên đám mây nước ngoài là chuyển dữ liệu xuyên biên giới theo Luật 91/2025 và cần hồ sơ đánh giá tác động. Hai việc này cần người có chuyên môn pháp lý xác nhận; plan chỉ nêu việc phải làm.
- material3 1.5.0 đang ở bản RC. Không phát hành production khi nó chưa ra bản stable.
- Repo công khai, nên luồng phát hành là nơi duy nhất được chạm vào khóa ký và project prod.

## Requirements

<!-- Red Team: cổng phát hành vượt yêu cầu — 2026-11-14 -->
Cổng chất lượng trên bản release (chặn phát hành):
- Unit test, test của proxy và trang web, so ảnh chụp: tất cả xanh trong CI. <!-- Updated: 19/11/2026 - không còn Firestore rules hay Cloud Functions -->
- Test đầu-cuối trên một thiết bị API 34: luồng chia hóa đơn mẫu qua một proxy giả chạy trong test, nhập tay, tạo yêu cầu. <!-- Updated: 19/11/2026 - chưa có proxy dev có key Anthropic; đường AI thật do bộ đánh giá kiểm -->
- Ngân sách hiệu năng của Phase 2 đạt trên máy tham chiếu với bản release có baseline profile.
- Bộ đánh giá AI đạt ngưỡng đã định ở Phase 3 và Phase 7 trên model đang dùng ở prod.
- Kiểm tra tiếp cận: TalkBack đi hết luồng chính, cỡ chữ 200%, độ tương phản.
- Ma trận xóa tài khoản (xem bên dưới) qua hết.

Mục tiêu (đo và ghi vào danh sách kiểm, không chặn phát hành): cùng bộ test đầu-cuối trên API 26, 30 và 36; số đo Lighthouse và kích thước gói của trang web.

<!-- Updated: Validation Session 1 - v1.0 chỉ có tiếng Anh -->
Ngôn ngữ: v1.0 chỉ có tiếng Anh. Mọi chuỗi nằm trong tài nguyên để thêm ngôn ngữ sau này không phải sửa mã; chạy thử ngôn ngữ giả để bắt chữ bị cắt.

Rà soát bảo mật:
- Mô hình mối đe dọa cho cổng AI (đốt chi phí, chèn lệnh qua hóa đơn), rules, trang web công khai, webhook, link mời, sổ nợ và cách nhận tiền.
- Quét phụ thuộc và quét secret trong CI; kiểm tra bản release không chứa key, không bật gỡ lỗi, không cho phép HTTP thường, địa chỉ proxy là `https`.

<!-- Red Team: xóa tài khoản không mở rộng theo dữ liệu mới — 2026-11-14 -->
Ma trận xóa tài khoản, kiểm bằng test trên emulator rồi một lần trên prod với tài khoản thử:

<!-- Updated: 19/11/2026 - theo nơi dữ liệu thật sự nằm sau Validation Session 5, và quyết định của chủ sản phẩm ngày 19/11 -->
| Dữ liệu | Nằm ở | Sau khi xóa tài khoản |
|---|---|---|
| Thiết bị nhận thông báo | D1 (`devices`) | Xóa hết |
| Số lượt AI | `QuotaBook` của tài khoản | Giữ số đã dùng của tháng hiện tại, gắn với mã băm của tài khoản, tới hết tháng để chống lách hạn mức bằng cách xóa rồi đăng nhập lại; xóa mọi thứ khác. Chính sách quyền riêng tư nêu rõ |
| Gói hiệu lực và dấu đã cấp mã người mua | `QuotaBook` | Xóa |
| Hồ sơ người mua | RevenueCat | Xóa trước mọi thứ khác; thuê bao trên Play không bị hủy, app cảnh báo trước |
| Bộ đếm chi tiêu | `AbuseLedger` | Xóa |
| Bộ đếm theo giờ | D1 (`rate_windows`) | Giữ tới lần dọn hằng ngày, chỉ mang mã băm |
| Tư cách thành viên nhóm và hóa đơn nhóm | D1 | Thành "Thành viên đã rời", giữ số tiền, chuyển quyền sở hữu hóa đơn |
| Yêu cầu trả tiền chưa xác nhận | D1 | Xóa bản chụp cách nhận tiền |
| Link chia sẻ và lượt nhận món | D1 | Thu hồi link, xóa lượt nhận |
| Hóa đơn, bản nháp, hóa đơn định kỳ, ảnh trang, danh sách người, cách nhận tiền, tên của người dùng | Chỉ trên máy | Xóa khi người dùng chọn ô trong hộp xác nhận; không chọn thì ở lại |
| Session và mã người mua đã lưu | Chỉ trên máy | Xóa |

Hồ sơ quyền riêng tư và pháp lý (chủ sản phẩm chịu trách nhiệm, người làm chuẩn bị bản nháp):
- Chính sách quyền riêng tư và điều khoản sử dụng trên web. Chính sách nêu rõ: văn bản hóa đơn được gửi tới API của Anthropic để xử lý; ảnh không rời máy; cơ sở dữ liệu D1 nằm ở phía đông Bắc Mỹ (vùng ENAM của Cloudflare), còn bộ đếm theo tài khoản nằm ở trung tâm dữ liệu Cloudflare gần nơi chúng được dùng lần đầu; thời hạn lưu. <!-- Updated: 19/11/2026 - dữ liệu ở Cloudflare, không còn Firestore `nam5` -->
- Biểu mẫu An toàn dữ liệu, phân loại nội dung, khai báo thuê bao.
- Xóa tài khoản trong app và trang web yêu cầu xóa.
- App không có SDK analytics, nên không cần chế độ đồng ý. Số đếm trên server không mang mã người dùng hay IP. <!-- Updated: 19/11/2026 - quyết định của chủ sản phẩm -->
- Thỏa thuận xử lý dữ liệu với Google, Anthropic, Cloudflare và RevenueCat.
- Đánh giá việc cần đại diện tại EU và hồ sơ chuyển dữ liệu xuyên biên giới của Việt Nam.

Phát hành:
- Kênh nội bộ, rồi thử kín (12 người, 14 ngày), rồi thử mở, rồi production theo nấc 5%, 20%, 50%, 100%.
- Điều kiện lên nấc kế tiếp: tỷ lệ người dùng không gặp sự cố từ 99,5% trở lên, tỷ lệ ANR dưới ngưỡng xấu của Play, chi phí AI mỗi người dùng trong ngân sách, không có lỗi mất dữ liệu.
- Hồ sơ cửa hàng: ảnh chụp sinh từ đường dựng ảnh của dự án, mô tả tiếng Anh.

Vận hành:
- Bảng theo dõi: sự cố, phễu sử dụng (quét, phân tích, chia sẻ, đánh dấu đã trả), chi phí AI theo ngày và theo người dùng, số tài khoản ẩn danh mới mỗi ngày. Số đếm do proxy ghi vào Workers Analytics Engine; sự cố lấy từ Android vitals. Bước quét chỉ diễn ra trên máy, nên không đếm được. <!-- Updated: 19/11/2026 - quyết định của chủ sản phẩm -->
- Cảnh báo: tỷ lệ lỗi cổng AI, cầu dao chi tiêu chạm 80%, hàm theo lịch thất bại.
<!-- Updated: Validation Session 5 - Firebase bỏ khỏi Phase 3; AI qua proxy trên Cloudflare -->
- Công tắc trên proxy (có từ Phase 3): tắt AI (app vẫn nhập tay được), phiên bản tối thiểu được hỗ trợ.
- Chọn cách báo lỗi crash. Mặc định là Android vitals của Play Console; thêm một dịch vụ báo lỗi chỉ khi vitals không đủ.
- Dữ liệu dùng chung nằm trong D1, nên cần sao lưu và một lần diễn tập khôi phục: Time Travel của D1 là bản sao lưu liên tục; diễn tập bằng cách xuất D1 ra SQL rồi nạp vào một database tạm. <!-- Updated: 19/11/2026 - Phase 5 và 6 đã đưa dữ liệu dùng chung lên D1 -->
- Sổ tay sự cố và kênh hỗ trợ người dùng.

## Architecture

Không có kiến trúc mới. Giai đoạn này thêm luồng CI phát hành:

<!-- Red Team: CI trên repo công khai để secret trong tầm với của PR — 2026-11-14 -->
```
<!-- Updated: 19/11/2026 - Worker trên Cloudflare thay cho Firebase -->
tag phiên bản ─▶ release.yml trong môi trường GitHub được bảo vệ (chủ sản phẩm duyệt tay)
                 ├─ build AAB prod, ký bằng khóa upload, kiểm căn 16 KB và các kiểm tra của bản release
                 ├─ tải lên kênh nội bộ của Play
                 └─ ghi bookmark Time Travel của D1, chạy migration, triển khai Worker
```
- Tải lên Play bằng Workload Identity Federation (OIDC) thay cho tệp khóa service account tồn tại lâu dài. Cloudflare dùng một API token hẹp quyền, nằm trong secret của môi trường được bảo vệ.
- Luồng này chỉ chạy từ tag trên `main`; pull request không bao giờ kích hoạt nó.

## Hợp đồng chung

<!-- Updated: 19/11/2026 - chốt trước khi chia việc -->
Các phần làm song song dùng chung đúng những điểm dưới đây.

### Trang pháp lý và trang xóa tài khoản

- Worker phục vụ ba trang: `/privacy` và `/terms`, dựng lúc build từ `docs/privacy-policy.md` và `docs/terms.md`; và `/delete-account`.
- `/delete-account`:
  - nói rõ những gì bị xóa và những gì ở lại: hóa đơn nằm trên điện thoại, và thuê bao Pro phải hủy trên Google Play;
  - có nút "Sign in with Google" của Google Identity Services, chỉ nạp trên trang này, rồi một bước xác nhận cuối, rồi kết quả;
  - khi Worker chưa có `GOOGLE_CLIENT_ID`, trang nói việc xóa trên web chưa sẵn sàng và chỉ cách xóa trong app.
- `POST /v1/account/delete-with-google` nhận `{"idToken": …}`, kiểm ID token như `/v1/session`, rồi xóa đúng như `DELETE /v1/account`: tối đa 5 lần mỗi giờ, RevenueCat trước. Đường này không phát session.
  - Trả 204 khi xóa xong.
  - Trả lỗi: 400 `input`, 401 `id_token`, 429 `rate_limited`, 502 `subscription_unavailable`, 503 `sign_in_off`.
- Chủ sản phẩm thêm địa chỉ của trang vào Authorized JavaScript origins của OAuth client.

### Xóa tài khoản trên proxy

- Theo ma trận ở trên. Thay đổi so với hiện tại: `QuotaBook` giữ số đã dùng của tháng hiện tại tới hết tháng, và xóa mọi thứ khác (gói, dấu đã cấp mã người mua, những request id đang nhớ).
- Test ma trận nằm trong `proxy/test/account.test.ts`, gồm cả việc số dư của các thành viên còn lại không đổi.

### Số đếm trên server

- Binding Workers Analytics Engine `METRICS`, dataset `split_vibe_events`. Mỗi điểm dữ liệu chỉ mang tên sự kiện, kết quả và con số (chẳng hạn chi phí tính bằng micro USD). Không có mã tài khoản, mã cài đặt hay IP.
- Các sự kiện được đếm:
  - đọc hóa đơn và chỉnh bằng lời, kèm kết quả và chi phí;
  - lần đầu một cài đặt gọi AI;
  - đăng nhập;
  - tạo link chia sẻ và lượt nhận món;
  - tạo yêu cầu trả tiền, báo đã trả và xác nhận đã trả;
  - làm mới gói và sự kiện webhook;
  - lượt chạy hằng ngày.
- Không có binding thì proxy chạy như cũ. Lệnh `npm run metrics` trong `proxy/` đọc số đếm qua SQL API, bằng API token của chủ sản phẩm.

### App

- Settings có link "Privacy policy" và "Terms of use" tới `/privacy` và `/terms` trên địa chỉ proxy của bản build. Màn Pro có hai link đó cạnh điều khoản gia hạn. Bản build không có địa chỉ proxy thì ẩn các link này.
- Hộp xác nhận xóa tài khoản có ô "Also delete the bills, people and payment methods on this phone", mặc định không chọn.
  - App chỉ xóa dữ liệu trên máy khi server đã xóa xong. Server xóa thất bại thì trên máy không mất gì.
  - Dữ liệu bị xóa: hóa đơn, bản nháp, hóa đơn định kỳ, ảnh trang, danh sách người, cách nhận tiền, bộ nhớ đệm của nhóm và yêu cầu, và tên của người dùng.
  - Các cài đặt khác ở lại, như tiền tệ và việc đã đồng ý cho dịch vụ giọng nói.
- Mọi chuỗi người dùng thấy nằm trong tài nguyên: `strings.xml`, và plurals khi có số đếm. Chữ tiếng Anh giữ nguyên từng chữ, nên mọi ảnh chuẩn hiện có phải khớp như cũ. Bản debug bật ngôn ngữ giả `en-XA` và `ar-XB`, và vài màn chính có ảnh chuẩn ở `en-XA` để bắt chữ bị cắt.

### Test đầu-cuối

- `app/src/androidTest/…/e2e/` chạy với một proxy giả (MockWebServer) trả lời như proxy thật, cho ba luồng: chia hóa đơn mẫu qua AI, nhập tay, và tạo yêu cầu trả tiền.
- Ở API 34, các test này chặn phát hành và chạy trong `device-tests.yml`. Có thêm một lượt chạy tay trên API 26, 30 và 36 để đo, không chặn.
- Test tìm phần tử bằng chữ tiếng Anh hiện trên màn hình, nên không phụ thuộc việc gom chuỗi.

### Phát hành và CI

- `release.yml`:
  - chạy khi đẩy một tag `v*` trỏ vào commit trên `main`, trong môi trường `production` có người duyệt;
  - chạy lại các kiểm tra; dựng AAB prod ký bằng khóa upload, giải mã từ secret của môi trường vào một tệp tạm; `versionCode` lấy từ tag;
  - kiểm căn 16 KB trên các APK tách từ AAB, và chạy các kiểm tra của bản release;
  - tải lên kênh nội bộ của Play bằng Workload Identity Federation;
  - ghi bookmark Time Travel của D1, chạy migration, rồi triển khai Worker bằng API token của Cloudflare.
- Các kiểm tra của bản release, chạy trong CI và trong `release.yml`: không debuggable, không cho HTTP thường, địa chỉ proxy là `https`, không chứa chuỗi giống khóa bí mật.
- Quét secret bằng gitleaks ở mọi push và pull request; dependency review ở pull request; `npm audit` mức high cho `proxy/` và `web/`; Dependabot cho Gradle bên cạnh npm.
- Mọi action bên thứ ba ghim theo SHA của commit. Pull request không bao giờ chạm tới secret.
- Chủ sản phẩm thiết lập: môi trường `production` và người duyệt, secret của khóa upload, Workload Identity Federation cho Play, và token của Cloudflare.

## Files

Tạo mới:
- `.github/workflows/release.yml`
- `app/src/androidTest/java/com/dantech/splitvibe/e2e/` (test đầu-cuối trên thiết bị)
- `proxy/test/account.test.ts` (ma trận xóa tài khoản)
- `docs/privacy-policy.md`, `docs/terms.md` (nguồn của trang web pháp lý), `docs/security-threat-model.md`, `docs/runbook.md`, `docs/release-checklist.md`
- các trang `/privacy`, `/terms` và `/delete-account` trong `web/` <!-- Updated: 19/11/2026 - trang do Worker phục vụ -->

Sửa:
- `app/src/main/res/values/strings.xml` (gom mọi chuỗi đang viết thẳng trong mã Compose; hiện tệp chỉ có 1 chuỗi)
- `README.md` (viết lại cho sản phẩm thay cho POC)
- `app/build.gradle.kts` (cấu hình thiết bị do Gradle quản lý)

## Implementation Steps

1. Tra cứu trùng nhãn hiệu cho tên "Split Vibe" **trước khi tạo app trên Play Console**, vì sau lần tải lên đầu tiên `applicationId` không đổi được. Việc này phải xong trước vòng thử kín và trước Phase 8.
2. Ngay khi Phase 5 xong: đưa bản dựng lên kênh thử kín và bắt đầu đếm 14 ngày với 12 người thử.
3. Gom chuỗi vào tài nguyên, chạy ngôn ngữ giả.
4. Viết test đầu-cuối trên thiết bị (chặn ở API 34; đo thêm ở API 26, 30, 36).
5. Rà soát bảo mật theo mô hình mối đe dọa; sửa mọi phát hiện mức cao trước khi đi tiếp.
6. Viết test ma trận xóa tài khoản; sửa `account.ts` và `account-data.ts` cho tới khi qua.
7. Soạn chính sách quyền riêng tư, điều khoản, trang xóa tài khoản; điền An toàn dữ liệu và phân loại nội dung.
8. Viết luồng CI phát hành với môi trường được bảo vệ và OIDC; diễn tập một lần trên project dev.
9. Dựng bảng theo dõi, cảnh báo, sao lưu; diễn tập khôi phục và diễn tập tắt AI.
10. Kiểm lại 16 KB và API mục tiêu trên AAB cuối; xác nhận material3 đã ở bản stable.
11. Xin quyền production, phát hành theo nấc, theo dõi 7 ngày ở 100%.

## Todo

- [ ] Tra cứu nhãn hiệu trước khi tạo app trên Play Console (chủ sản phẩm)
- [ ] Vòng thử kín 12 người trong 14 ngày (chủ sản phẩm)
- [x] Chuỗi trong tài nguyên, ngôn ngữ giả
- [x] Test đầu-cuối trên thiết bị
- [x] Rà soát bảo mật và sửa phát hiện mức cao
- [x] Ma trận xóa tài khoản qua hết
- [x] Chính sách quyền riêng tư, điều khoản, trang xóa tài khoản
- [ ] An toàn dữ liệu, phân loại nội dung, chế độ đồng ý (bản nháp và màn đồng ý đã xong; điền trên Play Console là việc của chủ sản phẩm)
- [ ] Đánh giá đại diện EU và hồ sơ xuyên biên giới Việt Nam (chủ sản phẩm, với người có chuyên môn pháp lý)
- [ ] Luồng CI phát hành có duyệt tay và OIDC; diễn tập (đã dựng; diễn tập cần project dev của chủ sản phẩm)
- [x] Bảng theo dõi, cảnh báo, sao lưu, sổ tay sự cố
- [ ] Kiểm 16 KB, API mục tiêu, material3 stable trên AAB cuối (material3 1.5.0 chưa có trên Google Maven)
- [ ] Phát hành theo nấc tới 100% và theo dõi 7 ngày (chủ sản phẩm)

## Kết quả thực hiện

<!-- Updated: thực hiện ngày 19/11/2026 trên nhánh feat/phase-09-quality-compliance-release, chưa merge vào main -->
Mọi việc làm được trong repo và trên Cloudflare đã xong, có test và bằng chứng. Tuy vậy, trong chín tiêu chí chưa tiêu chí nào đạt trọn, vì tiêu chí nào cũng còn một phần cần chủ sản phẩm: app trên Play Console và vòng thử kín, máy tham chiếu, khóa Anthropic để chạy hai bộ đánh giá, các giá trị production của proxy, người xét duyệt pháp lý, material3 1.5.0 bản stable, và việc phát hành theo nấc. Thứ tự các bước ở [docs/release-checklist.md](../../docs/release-checklist.md).

| Tiêu chí | Kết quả | Bằng chứng |
|---|---|---|
| Mọi cổng chặn phát hành xanh trên bản dùng để phát hành | Các cổng tự động xanh trên `c7a3707`; ba cổng chờ chủ sản phẩm | Unit test, proxy, web và test đầu-cuối trên Android 14 đều qua (số liệu ở dưới). Mô hình mối đe dọa ở [docs/security-threat-model.md](../../docs/security-threat-model.md); ba phát hiện mức cao của các bản review đã sửa. Còn chờ: đo hiệu năng trên máy tham chiếu, chạy hai bộ đánh giá AI bằng khóa của chủ sản phẩm, và một bản phát hành đã ký |
| Play Console cấp quyền production sau vòng thử kín | Chưa làm | Cần app trên Play Console và 12 người thử trong 14 ngày liền |
| 100% trong 7 ngày, từ 99,5% người dùng không gặp sự cố, ANR dưới ngưỡng xấu của Play | Chưa tới | Đo sau khi phát hành; ngưỡng và cách đọc ở [docs/release-checklist.md](../../docs/release-checklist.md) và [docs/runbook.md](../../docs/runbook.md) |
| An toàn dữ liệu được duyệt; chính sách quyền riêng tư và trang xóa tài khoản công khai | Một phần | Bản nháp An toàn dữ liệu và phân loại nội dung ở [docs/play-console.md](../../docs/play-console.md). `/privacy`, `/terms` và `/delete-account` chạy trên Worker (trả 200, có `noindex`), dựng từ `docs/privacy-policy.md` và `docs/terms.md`. Còn xét duyệt pháp lý, điền chỗ trống trong ngoặc vuông và nộp biểu mẫu |
| Ma trận xóa tài khoản qua trên emulator và một lần trên prod; số dư của người còn lại không đổi | Đạt trong test; chưa chạy trên prod | `proxy/test/account.test.ts` chạy trong runtime của Workers, có kiểm số dư của các thành viên còn lại. `AccountRepositoryTest` kiểm việc xóa vẫn xong sau khi rời Settings hay app bị tắt. Lần trên prod cần đăng nhập Google trên proxy, mà `GOOGLE_CLIENT_ID` còn trống |
| Luồng phát hành không chạy được nếu thiếu duyệt tay; không có tệp khóa tài khoản dịch vụ trong secret | Đã dựng; chưa diễn tập | `release.yml` đi qua hai môi trường cần chủ sản phẩm duyệt, và tải lên Play qua Workload Identity Federation, không có tệp khóa. Trước khi ký, bản phát hành chờ `ci.yml`, test Android 14 và bước kiểm thiết lập Worker; bước này dừng khi `GOOGLE_CLIENT_ID`, `ANDROID_CERT_SHA256` hay `ABUSE_EMAIL` trống. Diễn tập cần project dev của chủ sản phẩm ([docs/release.md](../../docs/release.md)) |
| Diễn tập tắt AI và khôi phục từ bản sao lưu | Chưa diễn tập | Các bước ở [docs/runbook.md](../../docs/runbook.md), mục "Switching AI off" và "The restore drill". Cách app xử lý `ai_off` có unit test |
| `zipalign -c -P 16` đạt; `targetSdk` 36; material3 stable | Một phần | `release.yml` kiểm 16 KB trên mọi APK tách từ AAB; `targetSdk` là 36. material3 vẫn ghim `1.5.0-rc01`: ghi chú phát hành của AndroidX có 1.5.0 từ ngày 18/11, nhưng ngày 19/11 Google Maven chưa có |
| Chi phí AI trung bình mỗi người dùng hoạt động tuần đầu trong ngân sách của Phase 3 | Chưa tới | Phase 3 chỉ đặt trần chi tiêu, chưa đặt ngân sách cho mỗi người dùng; chủ sản phẩm cần đặt con số này trước khi phát hành theo nấc. Bộ đếm Analytics Engine và `npm run metrics` đo được chi phí |

Kiểm tra trên `c7a3707`, commit mã cuối của nhánh:
- **App:** 493 unit test của bản prod, domain 200, hệ thiết kế 3, mọi ảnh chuẩn khớp; ktlint và lint sạch (0 lỗi); bản release và AAB dựng được với R8.
- **Proxy và web:** proxy 508 test vitest và 27 test Node, typecheck sạch; web 235 test và dựng được bốn trang.
- **Thiết bị:** trên Android 14, 13 test qua ở lượt 44478240987, gồm journey mới "từ chối AI rồi nhập tay". Trên API 26, 30 và 36, các journey qua ở lượt 44482956856; job API 30 phải chạy lại một lần vì lần đầu không tải được plugin.
- **Checksum:** Maven Central trả 429 cho runner của GitHub, và Gradle chuyển sang Gradle Plugin Portal. Portal có marker POM riêng cho hai plugin Kotlin, khác byte với bản của Central. Marker của compose đã tin cả hai checksum từ trước, nay thêm cho marker của serialization. Jar và module của hai plugin trên Portal trùng từng byte với bản đã ghim.

Những gì đã làm:
- **App:**
  - hộp xóa tài khoản có lựa chọn xóa luôn dữ liệu trên máy, mặc định không chọn; việc xóa vẫn xong sau khi rời Settings hay app bị tắt, và chỉ câu trả lời 204 của proxy mới được tính là đã xóa;
  - link tới chính sách và điều khoản; mọi chuỗi nằm trong tài nguyên, có số nhiều; hai ngôn ngữ giả en-XA và ar-XB ở bản debug;
  - màn hỏi đồng ý trước lần gọi AI đầu tiên, rút lại được trong Settings;
  - RevenueCat chỉ khởi động với phiên mà proxy đã nhận;
  - mọi con số trong chữ viết theo kiểu tiếng Anh;
  - sửa lỗi văng app trên Android 8 đến 11 do `BigInteger.longValueExact`, lỗi mà test đầu-cuối tìm ra.
- **Proxy và web:**
  - xóa tài khoản trên web bằng Google (`/delete-account`); xóa xong thì mọi phiên cấp trước đó bị từ chối, và lời mời của nhóm bị thu hồi;
  - bộ đếm sự kiện bằng Workers Analytics Engine, không SDK, với `npm run metrics`;
  - giữ 12 tháng: `QuotaBook` của bản cài tự xóa sau 12 tháng không gọi; cách nhận tiền của yêu cầu đã xác nhận bị xóa sau 12 tháng;
  - link yêu cầu trả tiền hết hạn với người ngoài sau 90 ngày;
  - quỹ $5 cho bản cài chưa đăng nhập nằm trong trần chung, và cảnh báo `spend_cap_near` khi một trần chạm 80%;
  - bộ đánh giá đọc hóa đơn với 35 hóa đơn (`npm run eval:read`), khoảng $11 mỗi lượt chạy đủ.
- **Phát hành và CI:**
  - `release.yml`, bước kiểm bản phát hành (`scripts/release-checks.py`) và bước kiểm thiết lập Worker (`scripts/worker-settings.py`);
  - quét secret mọi commit bằng gitleaks, review phụ thuộc cho pull request, kiểm lỗ hổng npm và Gradle, Dependabot cho web;
  - test thiết bị trên Android 14 chặn phát hành, và ma trận API 26, 30 và 36 để đo.
- **Tài liệu:** chính sách quyền riêng tư, điều khoản, sổ tay vận hành, danh sách kiểm tra phát hành, bản nháp Play Console, mô tả trên cửa hàng, mô hình mối đe dọa và hồ sơ tuân thủ.
- **Bốn bản review độc lập**, mọi phát hiện mức cao đã sửa:
  - proxy và web: 1 lỗi trung bình, 7 nhẹ, đã sửa hết ([báo cáo](../reports/code-reviewer-261119-0255-phase-9-proxy-web-review.md), [phần sửa](../reports/fullstack-developer-261119-0318-proxy-web-review-fixes.md));
  - bảo mật: 1 cao, 4 trung bình, 5 nhẹ ([báo cáo](../reports/code-reviewer-261119-0309-security-review.md)). Lỗi mức cao, một bản cài chưa đăng nhập lấp được trần chi tiêu chung, đã sửa bằng quỹ riêng $5. Xóa tài khoản giờ kết thúc mọi phiên cấp trước đó. Ba mục còn lại ghi ở phần giới hạn và ở danh sách kiểm tra phát hành;
  - phát hành, CI và test đầu-cuối: 2 lỗi mức cao, đã sửa hết ([báo cáo](../reports/code-reviewer-261119-0309-phase-9-release-ci-e2e-review.md), [phần sửa](../reports/fullstack-developer-261119-0345-release-ci-review-fixes.md));
  - app: 1 lỗi trung bình, 9 nhẹ, đã sửa hết ([báo cáo](../reports/code-reviewer-261119-0431-phase-9-app-review.md), [phần sửa](../reports/fullstack-developer-261119-0508-app-consent-deletion-review-fixes.md)).

Triển khai lên Cloudflare ngày 19/11/2026:
- **Lần 1, 05:39 UTC, gói dựng từ `ba39008`:**
  - Trước đó lấy bookmark Time Travel của D1: `0000000e-00000000-00005128-2c6bc013d15e8edea9f8c78336cb516b`. Không có migration.
  - Thêm binding `METRICS` (dataset `split_vibe_events`) và `ANDROID_DEV_CERT_SHA256` để trống.
  - Durable Object nhận mã mới ngay. Log và trace được lưu.
- **Lần 2, 08:17 UTC, gói dựng từ `c7a3707`:** mã proxy giống hệt lần 1, chỉ khác trang `/privacy` có phần đồng ý dùng AI.
  - SHA-256 của `index.js`: `110b7e86279d6b055892ab54aee0715ccea69d721f75a706174fefce814e5d17`.
  - Bookmark trước khi triển khai: `0000000f-00000000-00005128-8716ce506ab54d6d228aa4e2c2c750a1`.
  - Binding, secret và lịch chạy hằng ngày giữ nguyên.
- **Thử bản đang chạy:**
  - các trang `/privacy`, `/terms`, `/delete-account`, `/r/` và `/s/` trả 200;
  - `/v1/usage` của bản cài mới trả 3 lượt thử, AI bật;
  - xóa bằng Google trả 503 và đăng nhập trả `sign_in_off`, vì `GOOGLE_CLIENT_ID` còn trống;
  - link yêu cầu không có thật trả 404; mở link khi chưa đăng nhập trả 401;
  - bộ đếm đã ghi hai sự kiện `sign_in` với kết quả `sign_in_off`.
- Worker và KV namespace tạm dùng để chuyển gói lên đều bị xóa sau mỗi lần.

Các mặc định đã chọn, chủ sản phẩm có thể đổi:
- Sau 90 ngày, link yêu cầu trả tiền chỉ còn mở được cho người yêu cầu và cho người trả đã nhận yêu cầu trong app.
- 12 tháng tính là 365 ngày.
- Mọi con số trong chữ, kể cả tiền và ngày, viết theo kiểu tiếng Anh dù máy đặt vùng nào. Trên máy đặt vùng Việt Nam, "250.993 ₫" thành "₫250,993". Tệp CSV vẫn theo quy ước của máy.
- Câu trả lời ở màn đồng ý chỉ lưu trên máy, và vẫn còn khi người dùng chọn xóa dữ liệu trên máy lúc xóa tài khoản.
- Ngưỡng đánh giá đọc hóa đơn (`BAR`): không hóa đơn nào sai tổng hay tiền tệ, không câu trả lời nào làm theo dòng chữ in trên hóa đơn, và ít nhất 90% hóa đơn qua ở mọi lượt chạy.

Giới hạn đã biết:
- Lint không kiểm mức API (NewApi) trên `:core:domain`, vì đó là module Kotlin thuần. Lệnh gọi tới API mới hơn minSdk ở đó chỉ bị bắt khi test đầu-cuối trên API 26 và 30 đi qua đoạn mã ấy.
- Dạng số nhiều vẫn chọn theo quy tắc của ngôn ngữ trên máy: trên máy tiếng Ba Tư, số 0 lấy dạng số ít ("0 bill").
- Có hai trường hợp app chỉ báo được là việc xóa "chưa xác nhận": app bị tắt giữa lúc proxy trả 204 và lúc ghi lại, hoặc tài khoản đã bị lần gửi trước xóa rồi. Khi đó app bảo người dùng đăng nhập và xóa lại.
- Thông báo hóa đơn định kỳ tạo ngay trước lúc xóa vẫn có thể hiện ra; chạm vào thì app báo hóa đơn không còn.
- Khi Maven Central giới hạn lâu, một lượt CI vẫn có thể hỏng ở bước tải plugin (lỗi 429 hoặc "not found"). Chạy lại là qua.
- API của Cloudflare nhận `code_update_strategy: immediate` nhưng không trả lại giá trị này, nên không xác nhận được từ phía API.
- Một phiên bị sao chép khỏi máy vẫn dùng được tới 30 ngày sau khi đăng xuất. Xóa tài khoản thì kết thúc ngay mọi phiên cũ.
- Trên gói Workers Free, một máy gọi dồn có thể làm cả dịch vụ ngừng tới nửa đêm UTC. Việc chuyển sang Workers Paid nằm trong danh sách kiểm tra phát hành.
- Trace của Cloudflare vẫn lấy mẫu toàn bộ, và chính sách quyền riêng tư nói rõ trace ghi những gì.

## Success Criteria

- Mọi cổng chất lượng chặn phát hành đều xanh trên bản dùng để phát hành; kết quả lưu trong `plans/reports/`.
- Play Console cấp quyền production sau vòng thử kín.
- Bản phát hành ở 100% trong 7 ngày với tỷ lệ người dùng không gặp sự cố từ 99,5% trở lên và tỷ lệ ANR dưới ngưỡng xấu của Play.
- Biểu mẫu An toàn dữ liệu được duyệt; chính sách quyền riêng tư và trang xóa tài khoản truy cập được công khai.
- Ma trận xóa tài khoản qua trên emulator và một lần trên prod; số dư của các thành viên nhóm còn lại không đổi sau khi một người xóa tài khoản.
- Luồng phát hành không chạy được nếu thiếu bước duyệt tay; không có tệp khóa tài khoản dịch vụ nào trong secret của repo.
- Diễn tập: bật công tắc tắt AI trên proxy thì app chuyển sang nhập tay ngay ở yêu cầu kế tiếp; nếu có dữ liệu trên server, khôi phục từ bản sao lưu vào môi trường dev thành công.
- `zipalign -c -P 16` trên các APK tách từ AAB cuối báo đạt; `targetSdk` là 36; material3 là bản stable.
- Chi phí AI trung bình mỗi người dùng hoạt động trong tuần đầu nằm trong ngân sách đã đặt ở Phase 3.

## Risks

| Rủi ro | Dấu hiệu nhận biết | Phản ứng đã định |
|---|---|---|
| Không đủ 12 người thử liên tục 14 ngày | Số người tham gia tụt dưới 12 | Tuyển dư 20 người ngay từ đầu; đồng hồ 14 ngày chạy lại nếu thiếu |
| Play từ chối vì khai báo dữ liệu hoặc thanh toán | Thư từ chối khi duyệt | Sửa theo thư và nộp lại; không lách chính sách |
| material3 1.5.0 chưa stable lúc sẵn sàng phát hành | Trang phát hành của AndroidX chưa có 1.5.0 | Lùi ngày phát hành production; tiếp tục thử mở |
| Yêu cầu pháp lý EU hoặc Việt Nam chưa xong | Chưa có xác nhận của người có chuyên môn | Chủ sản phẩm quyết định: lùi ngày hoặc tạm chưa mở ở vùng đó |
| Chi phí AI vọt lên sau ra mắt | Cầu dao chi tiêu chạm trần | Hạ hạn mức trong cấu hình server; bật công tắc tắt AI nếu cần |
| Tên "Split Vibe" trùng nhãn hiệu | Kết quả tra cứu ở bước 1 | Đổi tên hiển thị và package trước khi tạo app trên Play Console |

## Security

- Luồng phát hành cần một bước duyệt tay trước khi chạm vào khóa ký hay project prod.
- Khóa upload nằm trong secret của môi trường được bảo vệ; quyền truy cập Google Cloud và Play đi qua OIDC với quyền tối thiểu.
- Bản release: không gỡ lỗi được, không cho HTTP thường, không log dữ liệu người dùng.

## Next

Sau v1.0: xem danh sách gợi ý trong `plan.md` (thêm ngôn ngữ, hóa đơn không phải chữ Latin, iOS).
