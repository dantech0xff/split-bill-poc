---
title: "Phase 8: Gói Pro"
status: in-progress
phase: 8
priority: P1
effort: "6d"
dependencies: [3, 7]
---

# Phase 8: Gói Pro

## Overview

<!-- Updated: 17/11/2026 - chủ sản phẩm chốt ba điểm khi bắt đầu phase -->
> **Đã chốt ngày 17/11/2026:**
> - Giữ RevenueCat làm dịch vụ thuê bao, như mặc định của plan. Proxy coi webhook của RevenueCat là tín hiệu và luôn hỏi lại trạng thái thật qua REST API của RevenueCat.
> - Chủ sản phẩm tự đặt giá trên Play Console. Hướng dẫn chỉ ghi khoảng giá của plan: 2,99 đến 3,99 USD mỗi tháng, 19,99 đến 29,99 USD mỗi năm.
> - Không có dùng thử miễn phí. Nếu sau này Play có ưu đãi (dùng thử hay giá giới thiệu), app vẫn hiện đúng điều khoản mà không cần phát hành lại.

Bản miễn phí có hạn mức lượt AI; gói thuê bao Pro mua qua Google Play nâng hạn mức đó. Mọi tính năng khác đều miễn phí. Quyền lợi do server quyết định, nên không thể bật Pro bằng cách sửa app.

## Context

- Quyết định của chủ sản phẩm: có gói Pro ngay từ v1.0, và Pro **chỉ** khác bản miễn phí ở số lượt AI.
- Phase 3 đã có hạn mức theo người dùng và tài liệu `entitlements/{uid}` chỉ server ghi được. Giai đoạn này nối tài liệu đó với việc mua hàng thật.
- Dữ kiện: [báo cáo backend](../reports/researcher-261114-0209-product-backend-and-payment-links.md), mục 6; [báo cáo giao diện và Play](../reports/researcher-261114-0209-android-expressive-ui-and-play-requirements.md), mục 5 (Play Billing).

## Key Insights

- Thứ tốn tiền thật là lượt gọi AI. Đối thủ chính bị chê nhiều nhất vì giới hạn việc nhập liệu, nên Split Vibe chỉ đo lượt AI và để nhập tay, nhóm, thống kê không giới hạn.
- Giá tham chiếu của thị trường: khoảng 20 đến 40 USD mỗi năm. Điểm khởi đầu đề xuất: 2,99 đến 3,99 USD mỗi tháng hoặc 19,99 đến 29,99 USD mỗi năm. Chủ sản phẩm tự đặt con số trên Play Console.
- Với model đọc hóa đơn hiện tại, mỗi lần đọc tốn khoảng 0,06 USD, nên một người dùng hết 300 lượt tốn khoảng 18 USD tiền AI mỗi tháng. Giá Pro dựa vào việc đa số không dùng hết, hoặc vào một model rẻ hơn mà bộ đánh giá chọn.
- Google Play thu 15% với thuê bao. Play Billing Library tối thiểu hiện nay là bản 8, bản mới nhất là 9.1.0.
- RevenueCat miễn phí tới 2.500 USD doanh thu theo dõi mỗi tháng, sau đó thu 1%. Nó lo phần xác thực giao dịch, khôi phục và webhook, là phần dễ sai nhất khi tự làm.
- Sự kiện webhook có thể tới trễ, tới sai thứ tự, hoặc bị giả mạo nếu khóa bí mật lộ. Vì vậy nội dung sự kiện chỉ được coi là **tín hiệu**; trạng thái thật luôn được lấy lại từ dịch vụ thuê bao.
<!-- Updated: 17/11/2026 - Firebase đã bỏ ở Validation Session 5; người mua là tài khoản Google mà proxy đã kiểm -->
- Mua khi chưa đăng nhập sẽ gắn giao dịch với một mã ẩn danh trên máy, mất khi gỡ app, nên app yêu cầu đăng nhập Google trước khi mua.

## Requirements

Chức năng:
- Hai sản phẩm thuê bao: tháng và năm, cùng một quyền lợi `pro`.
- Màn hình giới thiệu Pro dựng bằng hệ thiết kế: quyền lợi, giá theo vùng do Play cung cấp, điều khoản gia hạn, link quản lý thuê bao.
- Mua, khôi phục, đổi gói, hủy (dẫn sang trang quản lý của Play).
- App hiện số lượt AI còn lại và ngày làm mới.
- Khi hết lượt miễn phí, app giải thích và mời nâng cấp; mọi tính năng không dùng AI vẫn chạy.

Phi chức năng:
- Server là nơi duy nhất quyết định hạn mức: `POST /v1/bill-analysis` và `POST /v1/split-revision` đọc gói hiệu lực trong `QuotaBook` của tài khoản. <!-- Updated: 17/11/2026 - gói nằm trên proxy -->
- Trạng thái thuê bao trên server cập nhật trong vòng 5 phút sau mỗi sự kiện mua, gia hạn, hủy, hoàn tiền.

<!-- Updated: Validation Session 1 - ranh giới miễn phí và Pro -->
Ranh giới miễn phí và Pro (chủ sản phẩm đã chốt; các con số đổi được trong cấu hình trên server):

| | Ẩn danh | Đã liên kết Google | Pro |
|---|---|---|---|
| Lượt AI | 3 lượt dùng thử, một lần | 10 lượt mỗi tháng | 300 lượt mỗi tháng, dùng hợp lý |
| Mọi tính năng khác | Đầy đủ | Đầy đủ | Đầy đủ |

## Architecture

<!-- Updated: 17/11/2026 - Worker trên Cloudflare thay Cloud Function, gói nằm trong QuotaBook -->
```
App ── SDK của RevenueCat ── Google Play Billing
        │
        └─ định danh người mua = tài khoản Google mà proxy đã kiểm (chỉ khi đã đăng nhập Google)
RevenueCat ── webhook ─▶ Worker `POST /v1/subscription-events`
                           1. kiểm header Authorization của webhook
                           2. gọi REST API của RevenueCat để lấy trạng thái hiện tại của người mua
                           3. ghi gói vào QuotaBook của tài khoản = { proUntil, checkedAt, lastEventAt },
                              bỏ qua sự kiện cũ hơn lastEventAt và trạng thái cũ hơn checkedAt
bill-analysis / split-revision ── QuotaBook đọc gói hiệu lực ─▶ chọn hạn mức
```

<!-- Red Team: quyền lợi cũ không hết hạn; webhook tin vào nội dung và thứ tự sự kiện — 2026-11-14 -->
- **Gói hiệu lực** được tính trên server từ `expiresAt` cộng thời gian ân hạn, không chỉ từ `tier`. Một tài liệu ghi `pro` nhưng đã quá hạn thì được coi là miễn phí.
- App không có máy trạng thái quyền lợi riêng: nó hiển thị đúng những gì proxy (`usage.plan`) và SDK mua hàng báo. Sau khi mua, app gọi làm mới để không phải chờ webhook.
- Khôi phục giao dịch trên một tài khoản khác: thuê bao **ở lại tài khoản đã mua**, không tự chuyển sang tài khoản mới. Người dùng được báo phải đăng nhập đúng tài khoản.

Quyết định kỹ thuật (người làm đề xuất, chủ sản phẩm giữ ngày 17/11/2026): dùng RevenueCat. Điều kiện xem lại: khi doanh thu theo dõi vượt 2.500 USD mỗi tháng thì so phí 1% với công tự vận hành.

## Hợp đồng chung

<!-- Updated: 17/11/2026 - chốt trước khi chia việc cho proxy và app -->
Proxy và app làm song song, nên hai phần dùng chung đúng các định dạng dưới đây.

### Người mua

- Mã người dùng trong RevenueCat (app user ID) là mã tài khoản của proxy: 64 ký tự hex thường, tức băm có muối của `sub` Google mà proxy đã kiểm. Mã này không đến tay người dùng khác (trạng thái nhóm chỉ có `hasAccount`). Proxy gửi nó cho app trong trường `purchaser`; app không tự suy ra.
- App chỉ cấu hình SDK của RevenueCat khi đã đăng nhập Google và đã biết `purchaser`, luôn với đúng mã đó, không bao giờ dùng mã ẩn danh. Cài đặt chưa đăng nhập không gửi gì tới RevenueCat.
- Trong RevenueCat: quyền lợi `pro`; offering hiện hành có hai gói `$rc_monthly` và `$rc_annual`. Trên Play Console: một thuê bao `pro` với hai base plan `monthly` và `annual`. Project của RevenueCat đặt restore behavior là giữ giao dịch ở mã người dùng đã mua.

### Gói hiệu lực trên proxy

- Gói của một tài khoản nằm trong `QuotaBook` của tài khoản đó, và chỉ proxy ghi. Không đường nào nhận gói từ app.
- Proxy lưu `proUntil`: lúc quyền lợi `pro` hết hiệu lực theo trạng thái mới nhất lấy từ RevenueCat, đã tính thời gian ân hạn thanh toán của Play; null khi không có quyền lợi đang hiệu lực. Quyền lợi không có ngày hết (tặng trọn đời từ bảng điều khiển của RevenueCat) không bao giờ hết.
- Lúc `now`, tài khoản là Pro khi `proUntil` khác null và `now < proUntil + proGraceMs` (`proGraceMs` là 24 giờ, trong `policy.ts`). Phần ân hạn này chỉ để chờ sự kiện gia hạn đến trễ: trạng thái mới từ RevenueCat nói quyền lợi đã hết thì hạ gói ngay.
- Mỗi lần ghi kèm `checkedAt`, giờ RevenueCat trả lời. Lần ghi có `checkedAt` cũ hơn lần đã lưu bị bỏ qua, nên hai lần hỏi chạy cùng lúc không ghi đè trạng thái mới bằng trạng thái cũ. Webhook còn lưu `lastEventAt`, giờ của sự kiện mới nhất đã xử lý.
- Hạn mức Pro là 300 lượt mỗi tháng (`proCalls`), dùng chung bộ đếm theo tháng UTC với 10 lượt miễn phí. Nâng hay hạ gói giữa tháng giữ nguyên số đã dùng.

### Đường của proxy

- `usage` (trong `/v1/usage`, `/v1/session`, và mọi dòng, mọi lỗi có mang `usage`) có thêm `plan`: `"trial"` cho 3 lượt của cài đặt chưa đăng nhập, `"free"` cho 10 lượt mỗi tháng, `"pro"` cho 300 lượt mỗi tháng. Gặp proxy cũ không có trường này, app suy ra `"trial"` từ `period: "trial"`, còn lại là `"free"`.
- `POST /v1/session` và `GET /v1/usage` (khi có session) trả thêm `purchaser`.
- `POST /v1/plan`, cần session, thân `{}`: proxy hỏi RevenueCat trạng thái hiện tại của tài khoản, lưu lại, rồi trả `{ purchaser, usage, aiEnabled }`. Mỗi tài khoản gọi được 30 lần mỗi giờ (`planRefreshesPerHour`); quá thì 429 `rate_limited` kèm `Retry-After`. Chưa có secret của RevenueCat thì 503 `not_configured`. Không hỏi được RevenueCat thì 502 `subscription_unavailable`.
- `POST /v1/subscription-events`, webhook của RevenueCat:
  - header `Authorization` phải đúng bằng secret `REVENUECAT_WEBHOOK_AUTH`, so trong thời gian không đổi; sai hay thiếu thì 401 `webhook_auth` và không làm gì; chưa có secret thì 503 `not_configured`;
  - nội dung sự kiện chỉ là tín hiệu: proxy lấy các mã tài khoản trong sự kiện (`app_user_id`, `original_app_user_id`, `aliases`, `transferred_from`, `transferred_to`, chỉ những mã đúng dạng 64 hex), bỏ qua tài khoản đã xử lý một sự kiện mới hơn, rồi hỏi RevenueCat trạng thái thật của từng tài khoản còn lại và ghi như `POST /v1/plan`;
  - sự kiện `TEST` trả 200 mà không làm gì; hỏi RevenueCat thất bại thì trả 503 để RevenueCat gửi lại.
- `DELETE /v1/account`: khi có secret của RevenueCat, xóa khách hàng trong RevenueCat trước tiên (không có khách hàng đó cũng coi là xong); không xóa được thì 502 `subscription_unavailable` và chưa xóa gì khác. Xóa tài khoản không hủy thuê bao trên Play.
- Secret mới của Worker: `REVENUECAT_SECRET_KEY` (khóa bí mật của REST API) và `REVENUECAT_WEBHOOK_AUTH` (giá trị header Authorization đặt trong cấu hình webhook của RevenueCat). Thiếu hai secret này thì không ai thành Pro, còn mọi thứ khác chạy như cũ.

### App

- Khóa SDK công khai của RevenueCat lấy từ `splitvibe.revenueCatApiKey` trong `local.properties`, thành `BuildConfig.REVENUECAT_API_KEY`. Bản build không có khóa thì không bán Pro: màn Pro nói Pro chưa có trong bản này, mọi thứ khác như cũ.
- Màn Pro (`ui/paywall/`) dựng bằng hệ thiết kế, mở từ Settings và từ lời mời nâng cấp:
  - quyền lợi: 300 lượt AI mỗi tháng thay vì 10, mọi tính năng khác vẫn miễn phí;
  - hai gói với giá và kỳ hạn Play trả về qua RevenueCat; có ưu đãi (dùng thử hay giá giới thiệu) thì nói rõ, ví dụ "Free for 7 days, then $3.99 a month";
  - điều khoản gia hạn và cách hủy theo chính sách của Play, link quản lý thuê bao của Play, nút khôi phục giao dịch;
  - người đang Pro thấy gói hiện tại, ngày gia hạn hay ngày hết, và đổi được giữa gói tháng và gói năm;
  - chưa đăng nhập thì nút chính là đăng nhập Google: chỉ mua được sau khi đăng nhập.
- Khi mở màn Pro, và sau khi mua, khôi phục hay đổi gói xong, app gọi `POST /v1/plan` rồi hiện số lượt proxy trả.
- Mua hay khôi phục một thuê bao thuộc tài khoản Split Vibe khác: app nói thuê bao đó thuộc tài khoản khác và người dùng phải đăng nhập đúng tài khoản đã mua.
- Hết lượt (`quota_exhausted`): trong bản có bán Pro, người chưa là Pro thấy lời mời kèm nút mở màn Pro, và người chưa đăng nhập được nói thêm rằng đăng nhập Google cho 10 lượt mỗi tháng; người Pro chỉ thấy ngày lượt quay lại. Bản không bán Pro giữ cách cũ, tức mời đăng nhập.
- Settings hiện gói (Free hay Pro) cạnh số lượt còn lại, và có nút mở màn Pro.
- Hộp xác nhận xóa tài khoản nói rằng xóa tài khoản không hủy Pro, và phải hủy trên Google Play trước.

## Files

Tạo mới:
<!-- Updated: Validation Session 5 - Firebase bỏ khỏi Phase 3; AI qua proxy trên Cloudflare -->
- `proxy/src/subscription-webhook.ts` cùng test
- `app/src/main/java/com/dantech/splitvibe/billing/BillingRepository.kt`
- `app/src/main/java/com/dantech/splitvibe/ui/paywall/`

Sửa:
- `proxy/src/quota-book.ts` (hạn mức theo gói hiệu lực)
- Đường `DELETE /v1/account` của proxy (xóa gói hiệu lực và yêu cầu dịch vụ thuê bao xóa hồ sơ người mua)
- `app/build.gradle.kts`, `gradle/libs.versions.toml` (SDK mua hàng)
- `app/src/main/java/com/dantech/splitvibe/ui/settings/SettingsScreen.kt` (trạng thái gói, quản lý thuê bao)
- Gói hiệu lực nằm trong một Durable Object chỉ proxy ghi; client không ghi được

## Implementation Steps

1. Tra cứu nhãn hiệu cho tên "Split Vibe" phải xong trước bước này, vì tạo app trên Play Console cố định `applicationId` (xem Dependencies trong `plan.md`).
2. Tạo hai sản phẩm thuê bao trong Play Console và quyền lợi `pro` trong dịch vụ thuê bao; đặt giá theo điểm khởi đầu ở trên; tắt việc tự chuyển thuê bao sang tài khoản khác.
3. Viết webhook theo ba bước ở trên; test với sự kiện giả mạo, lặp lại và sai thứ tự.
4. Sửa `quota-book.ts` để tính gói hiệu lực từ `proUntil`; thêm số lượt còn lại vào trả lời của cổng AI.
5. `BillingRepository`, màn hình Pro, luồng mua, khôi phục, đổi gói; yêu cầu liên kết tài khoản trước khi mua.
6. Thông báo hết lượt và điểm mời nâng cấp trong luồng chia hóa đơn.
7. Thử bằng tài khoản thử nghiệm giấy phép trên kênh nội bộ: mua, gia hạn nhanh, hủy, hết hạn, hoàn tiền, khôi phục trên tài khoản khác.

## Todo

- [ ] Sản phẩm thuê bao tháng và năm trên Play Console
- [ ] Webhook coi sự kiện là tín hiệu, lấy lại trạng thái, bỏ qua sự kiện cũ
- [ ] Gói hiệu lực tính từ `expiresAt`; số lượt còn lại trong trả lời
- [ ] `BillingRepository`, màn hình Pro, mua, khôi phục, đổi gói
- [ ] Liên kết tài khoản trước khi mua
- [ ] Thông báo hết lượt và lời mời nâng cấp
- [ ] Xóa tài khoản xóa hồ sơ người mua
- [ ] Thử đủ vòng đời thuê bao bằng tài khoản thử nghiệm

## Success Criteria

- Trên kênh nội bộ với tài khoản thử nghiệm: mua xong thì hạn mức thành 300 trong vòng 5 phút; hủy rồi hết hạn thì trở về 10; khôi phục trên máy thứ hai cùng tài khoản hoạt động; khôi phục trên một tài khoản khác không chuyển gói sang.
- Test webhook: sự kiện không có khóa hợp lệ bị từ chối; sự kiện "hết hạn" cũ tới sau sự kiện "gia hạn" không hạ gói; sự kiện lặp lại không đổi gì.
- Gói đã lưu là Pro nhưng `proUntil` đã qua thời gian ân hạn chỉ được hạn mức miễn phí. <!-- Updated: 17/11/2026 - gói nằm trong QuotaBook, không còn Firestore rules -->
- Không đường nào của proxy cho app ghi gói hiệu lực; webhook không có header Authorization đúng bị từ chối.
- Người dùng đã liên kết ở lượt thứ 11 trong tháng nhận thông báo hết lượt kèm lời mời nâng cấp; nhập tay và mọi tính năng khác vẫn chạy.
- Màn hình Pro hiện giá do Play trả về theo vùng, điều khoản gia hạn và link quản lý thuê bao.
- Ảnh chuẩn cho màn hình Pro ở sáng và tối.

## Risks

| Rủi ro | Dấu hiệu nhận biết | Phản ứng đã định |
|---|---|---|
| Webhook tới trễ hoặc mất | Người đã mua vẫn bị giới hạn | App gọi làm mới quyền lợi sau khi mua và khi mở màn hình gói; chỉ thêm hàm đối soát theo lịch nếu đo được tỷ lệ mất đáng kể |
| Vi phạm chính sách thanh toán của Play | Bị từ chối khi duyệt | Chỉ bán qua Play Billing; hiện đủ điều khoản và cách hủy |
| Hạn mức Pro bị lạm dụng | Một tài khoản dùng sát 300 lượt đều đặn | Trần dùng hợp lý trên server, theo dõi chi phí theo người dùng |
| Giá chưa hợp thị trường | Tỷ lệ chuyển đổi thấp sau ra mắt | Đổi giá và thử ưu đãi qua Play Console, không cần phát hành app |

## Security

- Khóa bí mật của webhook và khóa API của dịch vụ thuê bao là secret của Worker. <!-- Updated: 17/11/2026 - không còn Secret Manager của Firebase -->
- Lộ khóa webhook không đủ để tự cấp Pro, vì hàm luôn lấy lại trạng thái thật từ dịch vụ thuê bao.
- App không tự tin vào trạng thái mua trên máy cho việc cấp lượt AI.
- Không lưu thông tin thanh toán nào; Play xử lý toàn bộ.

## Next

Phase 9 đưa luồng mua vào hồ sơ Play và vào danh sách kiểm trước phát hành.
