---
title: "Split Vibe: từ POC lên sản phẩm production"
description: "Lộ trình 9 giai đoạn đưa POC chia hóa đơn bằng AI thành app Android production toàn cầu, giao diện Material 3 Expressive, backend Firebase, có gói Pro."
status: pending
priority: P1
effort: "83d"
branch: feat/production-kickoff
tags: [feature, frontend, backend, auth, infra]
blockedBy: []
blocks: []
created: 2026-11-14
---

# Split Vibe: từ POC lên sản phẩm production

## Overview

Lộ trình 9 giai đoạn đưa POC chia hóa đơn bằng AI thành app Android production toàn cầu, giao diện Material 3 Expressive, backend Firebase, có gói Pro. POC đã chứng minh được lõi: đọc hóa đơn trên máy, model hiểu câu lệnh, tiền được tính bằng số nguyên trên máy và luôn cộng đủ. Plan này giữ nguyên lõi đó và xây phần còn thiếu của một sản phẩm thật.

Ước lượng tổng công là 83 ngày làm việc cho một người làm cùng trợ lý AI, sai số khoảng 40%. Con số này chỉ để so sánh các giai đoạn với nhau, không phải cam kết ngày phát hành.

## Quyết định đã chốt với chủ sản phẩm (14/11/2026)

- Hình thái: app độc lập, không giữ và không chuyển tiền.
- Tên: Split Vibe, package `com.dantech.splitvibe` (đã đổi, commit `d66096f`).
- Backend: Firebase. Thị trường: toàn cầu ngay từ bản đầu, tiếng Anh là chính.
- v1.0 gồm đủ 12 tính năng đã chọn và có gói Pro ngay từ đầu.
- Giao diện: Material 3 Expressive, nhiều animation mượt, đổ bóng đẹp, bố cục sạch.

## Goals

| # | Goal | Priority |
|---|------|----------|
| 1 | Không còn API key trên máy; AI đi qua cổng trên server có hạn mức | P1 |
| 2 | Giao diện Expressive, chuyển động và bóng đổ đạt ngân sách hiệu năng đo được | P1 |
| 3 | Đủ 12 tính năng v1.0 và gói Pro | P1 |
| 4 | Tiền luôn cộng đủ ở mọi cách chia, có test thuộc tính bảo vệ | P1 |
| 5 | Phát hành trên Google Play toàn cầu, đạt các cổng chất lượng và tuân thủ | P1 |

## Phases

| # | Phase | Status |
|---|-------|--------|
| 1 | [Nền móng kỹ thuật](./phase-01-start.md) | Pending |
| 2 | [Hệ thiết kế Expressive, chuyển động và đổ bóng](./phase-02-design-system-expressive.md) | Pending |
| 3 | [Backend Firebase và cổng AI](./phase-03-firebase-backend-ai-gateway.md) | Pending |
| 4 | [Quét, sửa hóa đơn, cách chia, đa tiền tệ, lịch sử](./phase-04-bill-capture-editing-split-modes.md) | Pending |
| 5 | [Nhóm, số dư và thu tiền không giữ tiền](./phase-05-people-groups-settle-up.md) | Pending |
| 6 | [Trang web cho người nhận và tự nhận món trực tiếp](./phase-06-web-companion-realtime-claim.md) | Pending |
| 7 | [Chỉnh bằng lời với AI, thống kê, xuất file, hóa đơn định kỳ](./phase-07-ai-assistant-insights.md) | Pending |
| 8 | [Gói Pro](./phase-08-pro-subscription.md) | Pending |
| 9 | [Chất lượng, tuân thủ và phát hành](./phase-09-quality-compliance-release.md) | Pending |

Thứ tự và phụ thuộc: 1 trước tất cả. 2 và 3 chạy song song được sau 1. 4 cần 2 và 3. 5 cần 4. 6 cần 5. 7 cần 4 và chạy song song được với 5 và 6. 8 cần 3 và 7. 9 cần tất cả, nhưng vòng thử kín và hồ sơ pháp lý của nó bắt đầu ngay sau 5.

Ba giai đoạn đầu được viết tới mức tệp và dòng mã. Các giai đoạn 4 đến 9 được viết tới mức giao kèo, dữ liệu và tiêu chí nghiệm thu; mỗi giai đoạn mở đầu bằng một bước rà lại mã hiện có, vì mã sẽ đổi nhiều sau ba giai đoạn đầu.

## Mốc

| Mốc | Sau giai đoạn | Nội dung |
|---|---|---|
| Alpha nội bộ | 1, 2, 3 | Luồng POC với giao diện mới, chạy qua cổng AI, có tài khoản ẩn danh |
| Thử kín | 4, 5 | Hóa đơn trọn vòng đời, nhóm, thu tiền; bắt đầu đếm 14 ngày với 12 người thử |
| Thử mở | 6, 7, 8 | Trang web, trợ lý AI, thống kê, gói Pro |
| v1.0 | 9 | Phát hành theo nấc tới 100% |

## Dependencies

Việc chỉ chủ sản phẩm làm được:
- Tài khoản Google Play Console, project Firebase ở gói Blaze, tài khoản API của Anthropic, tài khoản dịch vụ thuê bao.
- Một tên miền cho trang web và link mời.
- Một máy Android tầm trung làm máy tham chiếu đo hiệu năng.
- 12 người thử trở lên cho vòng thử kín; người thử ở EU và Ấn Độ cho mã QR SEPA và link UPI nếu có.
- Xác nhận pháp lý: chính sách quyền riêng tư, đại diện tại EU, hồ sơ chuyển dữ liệu xuyên biên giới của Việt Nam, tra cứu nhãn hiệu "Split Vibe".
- Chọn bảng màu thương hiệu từ ảnh dựng ở Phase 2.

Báo cáo nghiên cứu làm nền:
- [Giao diện Expressive và yêu cầu của Google Play](../reports/researcher-261114-0209-android-expressive-ui-and-play-requirements.md)
- [Sản phẩm, backend và các đường thanh toán](../reports/researcher-261114-0209-product-backend-and-payment-links.md)

## Success Criteria

- [ ] Bản release không chứa API key hay SDK của Anthropic; mọi lần gọi AI đi qua cổng có App Check và hạn mức.
- [ ] Mọi màn hình dùng hệ thiết kế Expressive và ba hành trình chính đạt ngân sách hiệu năng trên máy tham chiếu.
- [ ] 12 tính năng v1.0 và gói Pro đạt tiêu chí nghiệm thu trong giai đoạn của chúng.
- [ ] Test thuộc tính chứng minh tổng các phần bằng tổng hóa đơn ở cả 5 cách chia, và số dư nhóm luôn cộng bằng 0.
- [ ] App ở 100% trên Google Play trong 7 ngày với tỷ lệ người dùng không gặp sự cố từ 99,5% trở lên.

## Phạm vi không làm ở v1.0

- Không giữ, không chuyển tiền, không lưu thông tin đăng nhập của dịch vụ thanh toán nào.
- Không có bản iOS.
- Không tải ảnh hóa đơn lên server.
- Không có quảng cáo.

## Gợi ý tính năng sau v1.0

1. Bản iOS dùng lại `:core:domain` qua Kotlin Multiplatform.
2. Nhập khoản chi tự động từ thông báo ngân hàng hoặc email hóa đơn điện tử.
3. Nhập dữ liệu từ app chia tiền khác bằng tệp CSV, để người dùng chuyển sang dễ.
4. Vé theo chuyến đi mua một lần, bên cạnh thuê bao.
5. Gợi ý thông minh: nhóm hay dùng, người hay đi cùng, thói quen ("Minh không uống bia").
6. Ảnh hóa đơn lưu trên đám mây có mã hóa, là quyền lợi Pro.
7. Widget và lối tắt quét nhanh; bố cục cho máy gập và máy tính bảng.
8. Bình luận và nhật ký hoạt động trong nhóm.

## Scope Challenge

- Existing code: lõi tính tiền (`SplitCalculator`, `BillReconciler`, `BillCurrency`), bộ mã hóa OCR, prompt và schema, bộ đọc trả lời, 80 unit test và `SplitFlowTest` được giữ lại và mở rộng, không viết lại.
- Requested scope: đổi package (đã xong), plan production với Material Expressive, animation mượt, đổ bóng đẹp, giao diện sạch, kèm gợi ý tính năng. Chủ sản phẩm chọn toàn bộ 12 tính năng gợi ý và gói Pro cho v1.0; plan giao đủ, không cắt.
- Complexity: 9 giai đoạn. Lý do: sáu mảng việc tách bạch (nền móng, thiết kế, backend, tính năng, thanh toán thuê bao, phát hành) và 12 tính năng chia thành bốn nhóm theo phụ thuộc.
- Selected mode: HOLD

## Giả định mang tải

| Giả định | Có thể gãy không | Dấu hiệu | Nếu gãy |
|---|---|---|---|
| material3 1.5.0 ra bản stable trước ngày phát hành | Có | Trang phát hành AndroidX | Lùi ngày production, vẫn thử mở được |
| Một model rẻ hơn Opus đọc hóa đơn đủ tốt | Có | Bộ đánh giá ở Phase 3 | Dùng model cao hơn, hạ hạn mức miễn phí |
| Firebase đủ cho quy mô ban đầu | Ít khả năng gãy trong đời plan | Hóa đơn hằng tháng và độ trễ | Xem lại kiến trúc dữ liệu; `:core:domain` không phụ thuộc Firebase |
| Một người làm kịp phạm vi v1.0 | Có | Giai đoạn trễ quá 40% so với ước lượng | Chủ sản phẩm quyết định: lùi ngày hay chuyển tính năng sang bản sau |
| Tên "Split Vibe" không trùng nhãn hiệu | Có | Kết quả tra cứu | Đổi tên trước lần tải lên Play đầu tiên |

Thiết kế này hết đáp ứng tiêu chí khi app phải giữ tiền hoặc khi cần xử lý hóa đơn không cần mạng; cả hai đều nằm ngoài phạm vi đã chốt.
