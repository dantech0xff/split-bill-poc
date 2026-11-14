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

Bản miễn phí có hạn mức lượt AI mỗi tháng; gói thuê bao Pro mua qua Google Play nâng hạn mức đó. Quyền lợi do server quyết định, nên không thể bật Pro bằng cách sửa app.

## Context

- Quyết định của chủ sản phẩm: có gói Pro ngay từ v1.0.
- Phase 3 đã có tài liệu hạn mức theo người dùng và trường `users.plan` chỉ server ghi được. Giai đoạn này nối trường đó với việc mua hàng thật.
- Dữ kiện: [báo cáo backend](../reports/researcher-261114-0209-product-backend-and-payment-links.md), mục 6; [báo cáo giao diện và Play](../reports/researcher-261114-0209-android-expressive-ui-and-play-requirements.md), mục 5 (Play Billing).

## Key Insights

- Thứ tốn tiền thật là lượt gọi AI. Đối thủ chính bị chê nhiều nhất vì giới hạn việc nhập liệu, nên Split Vibe chỉ đo lượt AI và để nhập tay không giới hạn.
- Giá tham chiếu của thị trường: khoảng 20 đến 40 USD mỗi năm. Điểm khởi đầu đề xuất: 2,99 đến 3,99 USD mỗi tháng hoặc 19,99 đến 29,99 USD mỗi năm.
- Google Play thu 15% với thuê bao. Play Billing Library tối thiểu hiện nay là bản 8, bản mới nhất là 9.1.0.
- RevenueCat miễn phí tới 2.500 USD doanh thu theo dõi mỗi tháng, sau đó thu 1%. Nó lo phần xác thực giao dịch, khôi phục và webhook, là phần dễ sai nhất khi tự làm.
- `linkWithCredential` giữ nguyên `uid` khi nâng cấp từ tài khoản ẩn danh, nên thuê bao gắn với `uid` không bị mất. Tuy vậy mua khi còn ẩn danh rồi gỡ app sẽ mất quyền truy cập, nên app yêu cầu liên kết tài khoản trước khi mua.

## Requirements

Chức năng:
- Hai sản phẩm thuê bao: tháng và năm, cùng một quyền lợi `pro`.
- Màn hình giới thiệu Pro dựng bằng hệ thiết kế: quyền lợi, giá theo vùng do Play cung cấp, điều khoản gia hạn, link quản lý thuê bao.
- Mua, khôi phục, đổi gói, hủy (dẫn sang trang quản lý của Play).
- App hiện số lượt AI còn lại và ngày làm mới.
- Khi hết lượt miễn phí, app giải thích và mời nâng cấp; mọi tính năng không dùng AI vẫn chạy.

Phi chức năng:
- Server là nơi duy nhất quyết định hạn mức: `analyzeBill` và `reviseSplit` đọc `users.plan` do webhook ghi.
- Trạng thái thuê bao trên server cập nhật trong vòng 5 phút sau mỗi sự kiện mua, gia hạn, hủy, hoàn tiền.

Ranh giới miễn phí và Pro (giá trị mặc định, đổi được qua Remote Config; ranh giới cuối cùng theo quyết định trong Validation Log của `plan.md`):
- Miễn phí: 10 lượt đọc hóa đơn bằng AI mỗi tháng, mọi tính năng khác.
- Pro: 300 lượt mỗi tháng theo nguyên tắc dùng hợp lý.

## Architecture

```
App ── SDK mua hàng ── Google Play Billing
        │
        └─ định danh người mua = uid của Firebase
Dịch vụ thuê bao ── webhook ─▶ Cloud Function `onSubscriptionEvent`
                                 ├─ kiểm chữ ký hoặc khóa bí mật của webhook
                                 └─ ghi users/{uid}.plan = { tier, expiresAt, source }
analyzeBill / reviseSplit ── đọc users/{uid}.plan ─▶ chọn hạn mức
```

Quyết định kỹ thuật (người làm đề xuất): dùng RevenueCat. Điều kiện xem lại: khi doanh thu theo dõi vượt 2.500 USD mỗi tháng thì so phí 1% với công tự vận hành.

Máy trạng thái quyền lợi trong `:core:domain`: `Free`, `Pro(expiresAt)`, `GracePeriod`, `Expired`; app chỉ dùng nó để hiển thị.

## Files

Tạo mới:
- `functions/src/subscription-webhook.ts` cùng test
- `core/domain/src/main/kotlin/.../Entitlement.kt` cùng test
- `app/src/main/java/com/dantech/splitvibe/billing/BillingRepository.kt`
- `app/src/main/java/com/dantech/splitvibe/ui/paywall/`

Sửa:
- `functions/src/quota.ts` (hạn mức theo `plan`)
- `app/build.gradle.kts`, `gradle/libs.versions.toml` (SDK mua hàng)
- `app/src/main/java/com/dantech/splitvibe/ui/settings/SettingsScreen.kt` (trạng thái gói, quản lý thuê bao)
- `firestore.rules` (client không ghi được `plan`)

## Implementation Steps

1. Tạo hai sản phẩm thuê bao trong Play Console và quyền lợi `pro` trong dịch vụ thuê bao; đặt giá theo điểm khởi đầu ở trên.
2. Viết webhook, kiểm xác thực, ghi `users.plan`; test với các sự kiện mẫu.
3. Sửa `quota.ts` để chọn hạn mức theo `plan`; thêm trường số lượt còn lại vào trả lời của cổng AI.
4. `BillingRepository`, màn hình Pro, luồng mua, khôi phục, đổi gói; yêu cầu liên kết tài khoản trước khi mua.
5. Thông báo hết lượt và điểm mời nâng cấp trong luồng chia hóa đơn.
6. Thử bằng tài khoản thử nghiệm giấy phép trên kênh nội bộ: mua, gia hạn nhanh, hủy, hết hạn, hoàn tiền.

## Todo

- [ ] Sản phẩm thuê bao tháng và năm trên Play Console
- [ ] Webhook ghi `users.plan` có kiểm xác thực
- [ ] Hạn mức theo gói trên server, số lượt còn lại trong trả lời
- [ ] `BillingRepository`, màn hình Pro, mua, khôi phục, đổi gói
- [ ] Liên kết tài khoản trước khi mua
- [ ] Thông báo hết lượt và lời mời nâng cấp
- [ ] Thử đủ vòng đời thuê bao bằng tài khoản thử nghiệm

## Success Criteria

- Trên kênh nội bộ với tài khoản thử nghiệm: mua xong thì `users.plan.tier` thành `pro` trong vòng 5 phút và hạn mức đổi theo; hủy rồi hết hạn thì trở về `free`; khôi phục trên máy thứ hai hoạt động.
- Sửa `plan` từ client bị rules từ chối; gọi webhook không có xác thực hợp lệ bị từ chối.
- Người dùng miễn phí ở lượt thứ 11 trong tháng nhận thông báo hết lượt kèm lời mời nâng cấp; nhập tay và mọi tính năng khác vẫn chạy.
- Màn hình Pro hiện giá do Play trả về theo vùng, điều khoản gia hạn và link quản lý thuê bao.
- Test máy trạng thái quyền lợi qua hết, kể cả thời gian ân hạn.
- Ảnh chuẩn cho màn hình Pro ở sáng và tối.

## Risks

| Rủi ro | Dấu hiệu nhận biết | Phản ứng đã định |
|---|---|---|
| Webhook tới trễ hoặc mất | Người đã mua vẫn bị giới hạn | App gọi làm mới quyền lợi sau khi mua; hàm đối soát theo lịch mỗi ngày |
| Vi phạm chính sách thanh toán của Play | Bị từ chối khi duyệt | Chỉ bán qua Play Billing; hiện đủ điều khoản và cách hủy |
| Hạn mức Pro bị lạm dụng | Một tài khoản dùng sát 300 lượt đều đặn | Trần dùng hợp lý trên server, theo dõi chi phí theo người dùng |
| Giá chưa hợp thị trường | Tỷ lệ chuyển đổi thấp sau ra mắt | Đổi giá và thử ưu đãi qua Play Console, không cần phát hành app |

## Security

- Khóa bí mật của webhook và khóa API của dịch vụ thuê bao nằm trong Secret Manager.
- App không tự tin vào trạng thái mua trên máy cho việc cấp lượt AI.
- Không lưu thông tin thanh toán nào; Play xử lý toàn bộ.

## Next

Phase 9 đưa luồng mua vào bộ kiểm thử phát hành và khai báo trong hồ sơ Play.
