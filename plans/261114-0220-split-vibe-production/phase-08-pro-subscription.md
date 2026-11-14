---
title: "Phase 8: Gói Pro"
status: todo
phase: 8
priority: P1
effort: "6d"
dependencies: [3, 7]
---

# Phase 8: Gói Pro

## Overview

Bản miễn phí có hạn mức lượt AI; gói thuê bao Pro mua qua Google Play nâng hạn mức đó. Mọi tính năng khác đều miễn phí. Quyền lợi do server quyết định, nên không thể bật Pro bằng cách sửa app.

## Context

- Quyết định của chủ sản phẩm: có gói Pro ngay từ v1.0, và Pro **chỉ** khác bản miễn phí ở số lượt AI.
- Phase 3 đã có hạn mức theo người dùng và tài liệu `entitlements/{uid}` chỉ server ghi được. Giai đoạn này nối tài liệu đó với việc mua hàng thật.
- Dữ kiện: [báo cáo backend](../reports/researcher-261114-0209-product-backend-and-payment-links.md), mục 6; [báo cáo giao diện và Play](../reports/researcher-261114-0209-android-expressive-ui-and-play-requirements.md), mục 5 (Play Billing).

## Key Insights

- Thứ tốn tiền thật là lượt gọi AI. Đối thủ chính bị chê nhiều nhất vì giới hạn việc nhập liệu, nên Split Vibe chỉ đo lượt AI và để nhập tay, nhóm, thống kê không giới hạn.
- Giá tham chiếu của thị trường: khoảng 20 đến 40 USD mỗi năm. Điểm khởi đầu đề xuất: 2,99 đến 3,99 USD mỗi tháng hoặc 19,99 đến 29,99 USD mỗi năm.
- Google Play thu 15% với thuê bao. Play Billing Library tối thiểu hiện nay là bản 8, bản mới nhất là 9.1.0.
- RevenueCat miễn phí tới 2.500 USD doanh thu theo dõi mỗi tháng, sau đó thu 1%. Nó lo phần xác thực giao dịch, khôi phục và webhook, là phần dễ sai nhất khi tự làm.
- Sự kiện webhook có thể tới trễ, tới sai thứ tự, hoặc bị giả mạo nếu khóa bí mật lộ. Vì vậy nội dung sự kiện chỉ được coi là **tín hiệu**; trạng thái thật luôn được lấy lại từ dịch vụ thuê bao.
- `linkWithCredential` giữ nguyên `uid` khi nâng cấp từ tài khoản ẩn danh. Tuy vậy mua khi còn ẩn danh rồi gỡ app sẽ mất quyền truy cập, nên app yêu cầu liên kết tài khoản trước khi mua.

## Requirements

Chức năng:
- Hai sản phẩm thuê bao: tháng và năm, cùng một quyền lợi `pro`.
- Màn hình giới thiệu Pro dựng bằng hệ thiết kế: quyền lợi, giá theo vùng do Play cung cấp, điều khoản gia hạn, link quản lý thuê bao.
- Mua, khôi phục, đổi gói, hủy (dẫn sang trang quản lý của Play).
- App hiện số lượt AI còn lại và ngày làm mới.
- Khi hết lượt miễn phí, app giải thích và mời nâng cấp; mọi tính năng không dùng AI vẫn chạy.

Phi chức năng:
- Server là nơi duy nhất quyết định hạn mức: `analyzeBill` và `reviseSplit` đọc `entitlements/{uid}`.
- Trạng thái thuê bao trên server cập nhật trong vòng 5 phút sau mỗi sự kiện mua, gia hạn, hủy, hoàn tiền.

<!-- Updated: Validation Session 1 - ranh giới miễn phí và Pro -->
Ranh giới miễn phí và Pro (chủ sản phẩm đã chốt; các con số đổi được trong cấu hình trên server):

| | Ẩn danh | Đã liên kết Google | Pro |
|---|---|---|---|
| Lượt AI | 3 lượt dùng thử, một lần | 10 lượt mỗi tháng | 300 lượt mỗi tháng, dùng hợp lý |
| Mọi tính năng khác | Đầy đủ | Đầy đủ | Đầy đủ |

## Architecture

```
App ── SDK mua hàng ── Google Play Billing
        │
        └─ định danh người mua = tài khoản Google mà proxy đã kiểm (chỉ khi đã đăng nhập Google)
Dịch vụ thuê bao ── webhook ─▶ Cloud Function `onSubscriptionEvent`
                                 1. kiểm khóa bí mật của webhook
                                 2. gọi API của dịch vụ để lấy trạng thái hiện tại của người mua
                                 3. ghi entitlements/{uid} = { tier, expiresAt, source, lastEventAt }
                                    chỉ khi sự kiện mới hơn lastEventAt (trong transaction trên server)
analyzeBill / reviseSplit ── đọc entitlements/{uid} ─▶ chọn hạn mức
```

<!-- Red Team: quyền lợi cũ không hết hạn; webhook tin vào nội dung và thứ tự sự kiện — 2026-11-14 -->
- **Gói hiệu lực** được tính trên server từ `expiresAt` cộng thời gian ân hạn, không chỉ từ `tier`. Một tài liệu ghi `pro` nhưng đã quá hạn thì được coi là miễn phí.
- App không có máy trạng thái quyền lợi riêng: nó hiển thị đúng những gì `entitlements/{uid}` và SDK mua hàng báo. Sau khi mua, app gọi làm mới để không phải chờ webhook.
- Khôi phục giao dịch trên một tài khoản khác: thuê bao **ở lại tài khoản đã mua**, không tự chuyển sang tài khoản mới. Người dùng được báo phải đăng nhập đúng tài khoản.

Quyết định kỹ thuật (người làm đề xuất): dùng RevenueCat. Điều kiện xem lại: khi doanh thu theo dõi vượt 2.500 USD mỗi tháng thì so phí 1% với công tự vận hành.

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
4. Sửa `quota.ts` để tính gói hiệu lực từ `expiresAt`; thêm số lượt còn lại vào trả lời của cổng AI.
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
- Một tài liệu `entitlements` ghi `pro` nhưng `expiresAt` đã qua thời gian ân hạn chỉ được hạn mức miễn phí.
- Sửa `entitlements` từ client bị rules từ chối.
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

- Khóa bí mật của webhook và khóa API của dịch vụ thuê bao nằm trong Secret Manager.
- Lộ khóa webhook không đủ để tự cấp Pro, vì hàm luôn lấy lại trạng thái thật từ dịch vụ thuê bao.
- App không tự tin vào trạng thái mua trên máy cho việc cấp lượt AI.
- Không lưu thông tin thanh toán nào; Play xử lý toàn bộ.

## Next

Phase 9 đưa luồng mua vào hồ sơ Play và vào danh sách kiểm trước phát hành.
