---
title: "Split Vibe: từ POC lên sản phẩm production"
description: "Lộ trình 9 giai đoạn đưa POC chia hóa đơn bằng AI thành app Android production toàn cầu, giao diện Material 3 Expressive, AI qua proxy trên Cloudflare, có gói Pro."
status: in-progress
priority: P1
effort: "90d"
branch: feat/production-kickoff
tags: [feature, frontend, backend, auth, infra]
blockedBy: []
blocks: []
created: 2026-11-14
---

# Split Vibe: từ POC lên sản phẩm production

## Overview

Lộ trình 9 giai đoạn đưa POC chia hóa đơn bằng AI thành app Android production toàn cầu, giao diện Material 3 Expressive, AI qua proxy trên Cloudflare, có gói Pro. POC đã chứng minh được lõi: đọc hóa đơn trên máy, model hiểu câu lệnh, tiền được tính bằng số nguyên trên máy và luôn cộng đủ. Plan này giữ nguyên lõi đó và xây phần còn thiếu của một sản phẩm thật.

Ước lượng tổng công là 90 ngày làm việc cho một người làm cùng trợ lý AI, sai số khoảng 40%. Con số này chỉ để so sánh các giai đoạn với nhau, không phải cam kết ngày phát hành.

## Quyết định đã chốt với chủ sản phẩm (14/11/2026)

- Hình thái: app độc lập, không giữ và không chuyển tiền.
- Tên: Split Vibe, package `com.dantech.splitvibe` (đã đổi, commit `d66096f`).
<!-- Updated: Validation Session 5 - AI qua proxy trên Cloudflare; Firebase bỏ khỏi Phase 3 -->
- AI: gọi qua một proxy trên Cloudflare (Worker), làm theo cách của skim-browser. Backend cho dữ liệu dùng chung (nhóm, trả nợ, trang web) chốt khi bắt đầu Phase 5 và Phase 6. Thị trường: toàn cầu ngay từ bản đầu.
- v1.0 gồm đủ 12 tính năng đã chọn và có gói Pro ngay từ đầu. Pro chỉ khác ở số lượt AI: 3 lượt dùng thử khi chưa đăng nhập, 10 lượt mỗi tháng khi đã đăng nhập Google, 300 lượt mỗi tháng với Pro.
- v1.0 chỉ có giao diện tiếng Anh và chỉ đọc hóa đơn chữ Latin.
- Giao diện: Material 3 Expressive, nhiều animation mượt, đổ bóng đẹp, bố cục sạch.
- Kỹ thuật: Koin cho DI, Navigation 3 cho điều hướng.
- Repo giữ công khai để tham khảo, theo giấy phép PolyForm Strict 1.0.0 (tệp `LICENSE`): không dùng cho mục đích thương mại, không phân phối lại, không sửa đổi.

Mặc định do người lập plan đặt, chủ sản phẩm đổi được: chọn model theo số liệu của bộ đánh giá (rẻ nhất đạt ngưỡng, leo thang khi lệch); dùng RevenueCat cho thuê bao; giả định tài khoản Play thuộc loại phải qua vòng thử kín.

## Goals

| # | Goal | Priority |
|---|------|----------|
| 1 | Không còn API key trên máy; AI đi qua cổng trên server có hạn mức không lách được | P1 |
| 2 | Giao diện Expressive, chuyển động và bóng đổ đạt ngân sách hiệu năng đo được | P1 |
| 3 | Đủ 12 tính năng v1.0 và gói Pro | P1 |
| 4 | Tiền luôn cộng đủ ở mọi cách chia và trong mọi tài liệu đã lưu, có test bảo vệ | P1 |
| 5 | Phát hành trên Google Play toàn cầu, đạt các cổng chất lượng và tuân thủ | P1 |

## Phases

| # | Phase | Status |
|---|-------|--------|
| 1 | [Nền móng kỹ thuật](./phase-01-start.md) | In Progress |
| 2 | [Hệ thiết kế Expressive, chuyển động và đổ bóng](./phase-02-design-system-expressive.md) | In Progress |
| 3 | [Proxy AI trên Cloudflare](./phase-03-cloudflare-ai-proxy.md) | Pending |
| 4 | [Quét, sửa hóa đơn, cách chia, đa tiền tệ, lịch sử](./phase-04-bill-capture-editing-split-modes.md) | Pending |
| 5 | [Nhóm, số dư và thu tiền không giữ tiền](./phase-05-people-groups-settle-up.md) | Pending |
| 6 | [Trang web cho người nhận và tự nhận món trực tiếp](./phase-06-web-companion-realtime-claim.md) | Pending |
| 7 | [Chỉnh bằng lời với AI, thống kê, xuất file, hóa đơn định kỳ](./phase-07-ai-assistant-insights.md) | Pending |
| 8 | [Gói Pro](./phase-08-pro-subscription.md) | Pending |
| 9 | [Chất lượng, tuân thủ và phát hành](./phase-09-quality-compliance-release.md) | Pending |

Thứ tự và phụ thuộc: 1 trước tất cả. 2 và 3 chạy song song được sau 1. 4 cần 2 và 3. 5 cần 4. 6 cần 5. 7 cần 4 và chạy song song được với 5 và 6. 8 cần 3 và 7. 9 cần tất cả, nhưng việc tra nhãn hiệu, vòng thử kín và hồ sơ pháp lý của nó bắt đầu sớm.

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
- **Tra cứu nhãn hiệu "Split Vibe" trước khi tạo app trên Play Console.** Vòng thử kín và việc tạo sản phẩm thuê bao đều cần app đã tồn tại trên Play, và sau lần tải lên đầu tiên `applicationId` không đổi được. Chủ sản phẩm quyết định ngày 14/11 tạm giữ tên này và tập trung phát triển trước; việc tra cứu vẫn phải xong trước bước tạo app trên Play.
- Tài khoản Google Play Console, tài khoản Cloudflare (Workers và Durable Objects), OAuth client của Google cho đăng nhập, tài khoản API của Anthropic, tài khoản dịch vụ thuê bao.
- Một tên miền cho trang web và link mời.
- Một máy Android tầm trung làm máy tham chiếu đo hiệu năng.
- 12 người thử trở lên cho vòng thử kín; người thử ở EU và Ấn Độ cho mã QR SEPA và link UPI nếu có.
- Xác nhận pháp lý: chính sách quyền riêng tư, đại diện tại EU, hồ sơ chuyển dữ liệu xuyên biên giới của Việt Nam.
- Chọn bảng màu thương hiệu từ ảnh dựng ở Phase 2.

Báo cáo nghiên cứu làm nền:
- [Giao diện Expressive và yêu cầu của Google Play](../reports/researcher-261114-0209-android-expressive-ui-and-play-requirements.md)
- [Sản phẩm, backend và các đường thanh toán](../reports/researcher-261114-0209-product-backend-and-payment-links.md)

## Success Criteria

- [ ] App không chứa API key hay SDK của Anthropic ở mọi biến thể build; mọi lần gọi AI đi qua proxy có hạn mức, giới hạn theo IP và trần chi phí.
- [ ] Mọi màn hình dùng hệ thiết kế Expressive và ba hành trình chính đạt ngân sách hiệu năng trên máy tham chiếu.
- [ ] 12 tính năng v1.0 và gói Pro đạt tiêu chí nghiệm thu trong giai đoạn của chúng.
- [ ] Test thuộc tính chứng minh tổng các phần bằng tổng hóa đơn ở cả 5 cách chia, và số dư nhóm luôn cộng bằng 0 kể cả với nhiều tiền tệ.
- [ ] App ở 100% trên Google Play trong 7 ngày với tỷ lệ người dùng không gặp sự cố từ 99,5% trở lên.

## Phạm vi không làm ở v1.0

- Không giữ, không chuyển tiền, không lưu thông tin đăng nhập của dịch vụ thanh toán nào.
- Không có bản iOS.
- Không tải ảnh hóa đơn lên server.
- Không có quảng cáo.
- Không có giao diện ngoài tiếng Anh; không đọc hóa đơn chữ không phải Latin.

## Gợi ý tính năng sau v1.0

1. Thêm ngôn ngữ giao diện, bắt đầu từ tiếng Việt.
2. Đọc hóa đơn chữ Trung, Nhật, Hàn, Hindi bằng các bộ chữ của ML Kit tải theo nhu cầu.
3. Bản iOS dùng lại `:core:domain` qua Kotlin Multiplatform.
4. Nhập khoản chi tự động từ thông báo ngân hàng hoặc email hóa đơn điện tử.
5. Nhập dữ liệu từ app chia tiền khác bằng tệp CSV, để người dùng chuyển sang dễ.
6. Vé theo chuyến đi mua một lần, bên cạnh thuê bao.
7. Gợi ý thông minh: nhóm hay dùng, người hay đi cùng, thói quen ("Minh không uống bia").
8. Ảnh hóa đơn lưu trên đám mây có mã hóa; widget và lối tắt quét nhanh; bố cục cho máy gập.

## Scope Challenge

- Existing code: lõi tính tiền (`SplitCalculator`, `BillReconciler`, `BillCurrency`), bộ mã hóa OCR, bộ đọc trả lời, phần lớn unit test và `SplitFlowTest` được giữ lại và mở rộng, không viết lại. Prompt và schema chuyển lên server; phần gọi thẳng API từ máy bị xóa.
- Requested scope: đổi package (đã xong), plan production với Material Expressive, animation mượt, đổ bóng đẹp, giao diện sạch, kèm gợi ý tính năng. Chủ sản phẩm chọn toàn bộ 12 tính năng gợi ý và gói Pro cho v1.0; plan giao đủ, không cắt.
- Complexity: 9 giai đoạn. Lý do: sáu mảng việc tách bạch (nền móng, thiết kế, backend, tính năng, thanh toán thuê bao, phát hành) và 12 tính năng chia thành bốn nhóm theo phụ thuộc.
- Selected mode: HOLD

## Giả định mang tải

| Giả định | Có thể gãy không | Dấu hiệu | Nếu gãy |
|---|---|---|---|
| material3 1.5.0 ra bản stable trước ngày phát hành | Có | Trang phát hành AndroidX | Lùi ngày production, vẫn thử mở được |
| Một model rẻ hơn Opus đọc hóa đơn đủ tốt | Có | Bộ đánh giá ở Phase 3 | Dùng model cao hơn, hạ hạn mức miễn phí |
| Lượt dùng thử ẩn danh không bị vét hàng loạt | Có | Số tài khoản ẩn danh mới mỗi ngày | Thêm tín hiệu theo thiết bị hoặc bỏ lượt dùng thử ẩn danh |
| Cloudflare Workers và Durable Objects đủ cho proxy AI ở quy mô ban đầu | Ít khả năng gãy trong đời plan | Hóa đơn hằng tháng, độ trễ và lỗi vượt giới hạn của Worker | Xem lại kiến trúc proxy; `:core:domain` không phụ thuộc backend nào |
| Một người làm kịp phạm vi v1.0 | Có | Giai đoạn trễ quá 40% so với ước lượng | Chủ sản phẩm quyết định: lùi ngày hay chuyển tính năng sang bản sau |
| Tên "Split Vibe" không trùng nhãn hiệu | Có | Kết quả tra cứu | Đổi tên trước khi tạo app trên Play Console |

Thiết kế này hết đáp ứng tiêu chí khi app phải giữ tiền hoặc khi cần xử lý hóa đơn không cần mạng; cả hai đều nằm ngoài phạm vi đã chốt.

## Red Team Review

### Session — 2026-11-14
**Findings:** 15 (15 accepted, 0 rejected), gộp từ 40 phát hiện thô của bốn người phản biện
**Severity breakdown:** 5 Critical, 8 High, 2 Medium

| # | Finding | Severity | Disposition | Applied To |
|---|---------|----------|-------------|------------|
| 1 | Bản release không phân tích được hóa đơn sau khi bỏ key (cổng chặn `hasApiKey` ở 4 nơi), và việc dời `ClaudeBillAnalyzer` làm hỏng build release trong khi bản debug che lỗi | Critical | Accept | Phase 3 |
| 2 | Hạn mức AI lách được bằng tài khoản ẩn danh mới và token của trang web; lượt bị trừ trước khi gọi, hoàn lượt khi lỗi là kẽ hở; không có trần toàn cục thật | Critical | Accept | Phase 3, 6, 8 |
| 3 | Tách `:core:domain` không biên dịch được như đã viết (`Contact`, `BillAnalyzerException`, parser `internal`); các giai đoạn mâu thuẫn về nơi đặt parser | Critical | Accept | Phase 1, 3, 7 |
| 4 | Không có định danh người thống nhất giữa hóa đơn, danh sách người, nhóm và người nhận món, nên không tính được số dư nhóm | Critical | Accept | Phase 4, 5, 6 |
| 5 | Hóa đơn có nhiều nơi ghi mà không có quy tắc xung đột; phần mỗi người đã lưu và phần tính lại không có nguồn chuẩn; sửa offline trên hai máy làm phần chia lệch tổng | Critical | Accept | Phase 4, 5 |
| 6 | Hai quyết định không đảo được (vị trí Firestore, ranh giới Pro) trỏ tới một Validation Log chưa tồn tại | High | Accept | plan.md, Phase 3, 8 |
| 7 | Danh sách tệp bỏ sót nơi dùng khi đổi tên, thay DI, xóa danh bạ giả và đổi chữ ký `analyze`; con số "12 chỗ tham chiếu" sai | High | Accept | Phase 1, 3, 4 |
| 8 | Vòng đời tài khoản: gộp khi trùng tài khoản chưa có chủ và thiết kế không an toàn; xóa tài khoản không mở rộng theo dữ liệu thêm ở các giai đoạn sau | High | Accept | Phase 3, 4, 5, 6, 8, 9 |
| 9 | Bản chiếu công khai không thực thi được cam kết của chính nó: client tạo được tài liệu, hết hạn không được kiểm, lộ `ownerUid`, lượt nhận theo thứ tự món, tranh chấp lúc chốt, lượt nhận mồ côi | High | Accept | Phase 6 |
| 10 | Thông tin nhận tiền bị lộ cho mọi người có link; link dán là kênh chèn mã; đường dẫn có token lọt sang trang thanh toán | High | Accept | Phase 5, 6 |
| 11 | Client tự thêm người vào `memberUids` để đẩy thông báo lừa đảo; người nợ tự đánh dấu đã trả; người lạ chiếm chỗ giữ tên của người được nợ | High | Accept | Phase 4, 5 |
| 12 | Tỷ giá chụp trong hóa đơn không khớp tiền của nhóm; làm tròn từng phần phá bất biến tổng bằng 0; nguồn tỷ giá trả phí dự phòng không ai yêu cầu | High | Accept | Phase 4, 5 |
| 13 | CI trên repo công khai để khóa ký và khóa triển khai trong tầm với của pull request; debug provider của App Check có thể lọt sang prod | High | Accept | Phase 1, 3, 9 |
| 14 | Hilt và Navigation 3 không ai yêu cầu | Medium | Accept (modified) | Phase 1 |
| 15 | Cổng phát hành vượt yêu cầu: tiếng Việt lúc ra mắt, Lighthouse và kích thước gói là điều kiện cứng, test đầu-cuối trên 4 mức API | Medium | Accept | Phase 2, 6, 9 |

Ghi chú về phát hiện 14: chủ sản phẩm chọn **Koin và Navigation 3**. Koin thay cho Hilt nên không còn câu hỏi tương thích plugin; Navigation 3 được giữ theo lựa chọn của chủ sản phẩm.

Một nhóm phát hiện mức trung bình nằm ngoài giới hạn 15 cũng đã được áp dụng vì sửa rẻ và rõ ràng: webhook thuê bao coi sự kiện là tín hiệu và bỏ qua sự kiện cũ, bỏ máy trạng thái quyền lợi trùng lặp trong app (Phase 8), và bản nháp hóa đơn định kỳ có mã cố định để hai máy không tạo trùng (Phase 7).

### Verification Results
- **Tier:** Full
- **Claims checked:** 78 (47 dẫn chiếu mã và tệp, 10 luồng hành vi, 12 giao kèo, 9 trạng thái mới)
- **Verified:** 54 | **Failed:** 19 | **Unverified:** 5

#### Failures
1. [Fact Checker] "Validation Log" trong `plan.md` chưa tồn tại lúc phản biện. Đã thêm ở dưới.
2. [Fact Checker] `SplitBillTheme` có 5 chỗ trong 3 tệp, không phải 12. Đã sửa trong Phase 1.
3. [Flow Tracer] "Chỉ cần thêm một bản cài đặt `BillAnalyzer`" sai: view model và giao diện còn chặn theo `hasApiKey`. Đã sửa trong Phase 3.
4. [Contract Verifier] 9 trên 12 giao kèo thiếu nơi dùng (`SplitBillApp`, `SplitBillTheme`, `AppContainer`, `ContactRepository`, `Contact`, `ClaudeBillAnalyzer`, `BillAnalyzer`, `BillAnalysisParser`, `OcrLayoutEncoder.encode`). Đã liệt kê đủ trong Phase 1, 3, 4.
5. [Scope Auditor] 7 trên 9 trạng thái mới không có nguồn chuẩn hoặc sai vòng đời (hạn mức, gói, bản chiếu, tỷ giá chụp, phần mỗi người, định danh người, cài đặt cũ). Đã thiết kế lại trong Phase 3 đến Phase 8.

Năm điểm chưa kiểm chứng lúc phản biện đã được xử lý: câu mô tả `SettingsStore` được viết lại; danh bạ giả đúng 12 người và hóa đơn mẫu đúng 902.664 ₫ với 7 món (đếm lại trong mã và test); lần chạy Roborazzi ngày 14/11 có bằng chứng trong `assets/`; repo công khai do chủ sản phẩm xác nhận.

### Whole-Plan Consistency Sweep
- Files reread: plan.md, phase-01-start, phase-02-design-system-expressive, phase-03-firebase-backend-ai-gateway, phase-04-bill-capture-editing-split-modes, phase-05-people-groups-settle-up, phase-06-web-companion-realtime-claim, phase-07-ai-assistant-insights, phase-08-pro-subscription, phase-09-quality-compliance-release
- Decision deltas checked: 18
- Reconciled stale references: 2
- Unresolved contradictions: 0

## Validation Log

### Session 1 — 2026-11-14
**Trigger:** Vòng phản biện chỉ ra các quyết định còn bỏ ngỏ; chủ sản phẩm chốt trước khi triển khai.
**Questions asked:** 6

#### Questions & Answers

1. **[Architecture]** Dữ liệu người dùng (Firestore) đặt ở vùng nào? Quyết định này không đổi được sau khi tạo database, nên phải chốt trước giai đoạn 3.
   - Options: nam5: Mỹ, đa vùng (Recommended) | eur3: châu Âu, đa vùng | asia-southeast1: Singapore
   - **Answer:** nam5: Mỹ, đa vùng (Recommended)
   - **Rationale:** Không đảo được; quyết định độ trễ, chi phí và nội dung hồ sơ pháp lý.

2. **[Scope]** Ranh giới miễn phí và Pro? Phản biện chỉ ra rằng hạn mức gắn với tài khoản ẩn danh lách được bằng cách xóa dữ liệu app, nên mình đề xuất: chưa đăng nhập chỉ được 3 lượt AI dùng thử, đăng nhập Google được 10 lượt mỗi tháng, Pro được 300 lượt mỗi tháng. Các con số đổi được trên server. Ngoài lượt AI, Pro còn khóa gì?
   - Options: Chỉ đo lượt AI (Recommended) | Lượt AI + thống kê, xuất file | Lượt AI + giới hạn số nhóm
   - **Answer:** Chỉ đo lượt AI (Recommended)
   - **Rationale:** Quyết định mô hình doanh thu và trần chi phí AI.

3. **[Scope]** App ra toàn cầu, nhưng bộ đọc chữ trên máy hiện chỉ đọc chữ Latin (Anh, Việt, Tây Ban Nha, Indonesia...). Hóa đơn chữ khác (Trung, Nhật, Hàn, Hindi, Thái, Ả Rập) xử lý thế nào ở v1.0?
   - Options: Chỉ chữ Latin ở v1.0 (Recommended) | Thêm Trung, Nhật, Hàn, Hindi | Gửi ảnh lên AI khi cần
   - **Answer:** Chỉ chữ Latin ở v1.0 (Recommended)
   - **Rationale:** Giữ lời hứa "ảnh không rời máy" và giữ phạm vi v1.0.

4. **[Architecture]** Phản biện cho rằng Hilt và Navigation 3 trong bản nháp là thứ không ai yêu cầu: navigation-compose bản 2.10.2 đã hỗ trợ vuốt ngược xem trước và chuyển cảnh dùng chung phần tử, còn AppContainer hiện có vẫn nối được đối tượng bằng tay. Bạn muốn nền kỹ thuật nào cho giai đoạn 1?
   - Options: Giữ gọn như hiện tại (Recommended) | Hilt + navigation-compose | Hilt + Navigation 3 | DI thủ công + Navigation 3
   - **Answer:** Other
   - **Custom input:** Koin + Navigation 3 -> Theo hướng này!
   - **Rationale:** Quyết định nền của mọi màn hình và mọi lớp phụ thuộc sau này.

5. **[Scope]** Bạn đã chọn ra toàn cầu với tiếng Anh là chính. Bản nháp đã tự thêm tiếng Việt vào điều kiện phát hành, và phản biện chỉ ra đó là phạm vi không ai yêu cầu. Giao diện v1.0 có những ngôn ngữ nào?
   - Options: Chỉ tiếng Anh (Recommended) | Tiếng Anh + tiếng Việt | Anh, Việt và 3 ngôn ngữ lớn
   - **Answer:** Chỉ tiếng Anh (Recommended)
   - **Rationale:** Quyết định khối lượng dịch và bộ ảnh chuẩn.

6. **[Risks]** Repo dantech0xff/split-bill-poc đang công khai. Sản phẩm production sẽ chứa prompt, rules bảo mật và logic hạn mức trong repo, và phản biện chỉ ra CI trên repo công khai dễ lộ khóa ký qua pull request từ bên ngoài. Bạn muốn repo thế nào?
   - Options: Chuyển sang private (Recommended) | Giữ công khai (mã nguồn mở) | Repo private mới
   - **Answer:** Giữ công khai (mã nguồn mở)
   - **Rationale:** Quyết định cách thiết kế CI và việc an toàn không được dựa vào giấu mã.

Câu hỏi của vòng phản biện, hỏi cùng lượt: "Bốn người phản biện độc lập tìm ra 15 phát hiện… Bạn muốn xử lý thế nào?" với các lựa chọn Áp dụng cả 15 (Recommended) | Để tôi duyệt từng mục | Bỏ qua, giữ bản nháp. Trả lời: **Áp dụng cả 15 (Recommended)**.

#### Confirmed Decisions
- Firestore ở `nam5`, hàm ở `us-central1`: lựa chọn trung tính cho app toàn cầu.
- Chỉ đo lượt AI, 3 / 10 / 300: thu tiền ở đúng thứ tốn tiền, không giới hạn nhập liệu.
- Chỉ chữ Latin ở v1.0: giữ phạm vi; chữ khác vào danh sách sau v1.0.
- Koin và Navigation 3: lựa chọn của chủ sản phẩm.
- Chỉ tiếng Anh ở v1.0: đúng quyết định "tiếng Anh là chính".
- Repo công khai: CI coi pull request là mã không tin cậy; giấy phép được chốt ở Session 2.

#### Action Items
- [x] Ghi vị trí `nam5` vào Phase 3 và chặn bước 1 của Phase 3 theo quyết định này
- [x] Ghi bảng hạn mức vào Phase 3 và Phase 8
- [x] Bỏ tiếng Việt khỏi cổng phát hành của Phase 9 và khỏi ma trận ảnh của Phase 2
- [x] Viết lại Phase 1 với Koin và Navigation 3
- [x] Thiết kế CI cho repo công khai trong Phase 1 và Phase 9
- [x] Chủ sản phẩm chọn giấy phép mã nguồn (xem Session 2)

#### Impact on Phases
- Phase 1: Koin thay Hilt; Navigation 3 giữ theo quyết định; CI không secret cho pull request.
- Phase 2: ma trận ảnh chuẩn không còn biến thể tiếng Việt.
- Phase 3: Firestore `nam5`; ba mức hạn mức; app không còn key ở mọi biến thể build.
- Phase 4: ghi rõ chỉ đọc chữ Latin và thông báo cho hóa đơn chữ khác.
- Phase 8: Pro chỉ khác ở số lượt AI.
- Phase 9: chỉ tiếng Anh; luồng phát hành có duyệt tay và OIDC. Việc thêm `LICENSE` được làm ngay ở Session 2.

### Whole-Plan Consistency Sweep (Session 1)
- Files reread: plan.md, phase-01-start, phase-02-design-system-expressive, phase-03-firebase-backend-ai-gateway, phase-04-bill-capture-editing-split-modes, phase-05-people-groups-settle-up, phase-06-web-companion-realtime-claim, phase-07-ai-assistant-insights, phase-08-pro-subscription, phase-09-quality-compliance-release
- Decision deltas checked: 6
- Reconciled stale references: 0
- Unresolved contradictions: 0

Kiểm lại sau khi sửa: 62 dẫn chiếu tới dòng mã trong các tệp giai đoạn đều khớp với mã hiện tại. Còn 6 điểm mang nhãn `[UNVERIFIED]`, mỗi điểm có bước kiểm trong giai đoạn của nó: plugin Gradle của Roborazzi trên AGP 9 (Phase 1), độ khớp của bóng trong ảnh dựng với máy thật và tên `SplitButtonLayout` ở bản stable (Phase 2), chi phí mỗi lần đọc và tính năng device recall (Phase 3), định dạng link của Venmo và Cash App (Phase 5).

### Session 2 — 2026-11-14
**Trigger:** Ba câu hỏi còn mở ở cuối bước bàn giao plan.
**Questions asked:** 3

#### Questions & Answers

1. **[Scope]** Gộp nhánh: bạn có muốn mình gộp `feat/production-kickoff` vào `main` không?
   - Options: câu hỏi mở
   - **Answer:** có
   - **Rationale:** `main` mang tên mới và plan trước khi bắt đầu triển khai.

2. **[Risks]** Giấy phép: repo giữ công khai thì cần một tệp `LICENSE`. Bạn chọn giấy phép nào?
   - Options: câu hỏi mở
   - **Answer:** Other
   - **Custom input:** Giấy phép không được thương mại, chỉ sử dụng để tham khảo
   - **Rationale:** Người lập plan chọn văn bản chuẩn khớp ý này là PolyForm Strict 1.0.0 (cho phép dùng phi thương mại; không cho phân phối lại hay sửa đổi). Chủ sản phẩm đổi được nếu muốn văn bản khác.

3. **[Risks]** Nhãn hiệu: tên "Split Vibe" cần được tra cứu trước khi tạo app trên Play Console… Bạn muốn mình tra thử không?
   - Options: câu hỏi mở
   - **Answer:** Other
   - **Custom input:** Chưa cần, tạm thời đặt tên đó, focus vào phát triển sản phẩm đi đã
   - **Rationale:** Việc tra cứu được hoãn, không bị bỏ: nó vẫn chặn bước tạo app trên Play.

#### Confirmed Decisions
- Gộp `feat/production-kickoff` vào `main`.
- Giấy phép PolyForm Strict 1.0.0; repo là mã nguồn công khai để tham khảo, không phải mã nguồn mở theo nghĩa cho phép dùng lại.
- Giữ tên Split Vibe trong lúc phát triển; tra cứu nhãn hiệu để sau.

#### Action Items
- [x] Thêm tệp `LICENSE` và mục License trong README
- [x] Bỏ việc thêm `LICENSE` khỏi Phase 9
- [ ] Tra cứu nhãn hiệu trước khi tạo app trên Play Console (chủ sản phẩm)

#### Impact on Phases
- Phase 9: không còn việc thêm `LICENSE`; bước tra cứu nhãn hiệu giữ nguyên.

### Whole-Plan Consistency Sweep (Session 2)
- Files reread: plan.md, phase-01-start, phase-09-quality-compliance-release
- Decision deltas checked: 3
- Reconciled stale references: 10
- Unresolved contradictions: 0

### Session 3 — 2026-11-14
**Trigger:** Phase 1 xong trên nhánh riêng; bước 1 của Phase 2 cần chủ sản phẩm chọn bảng màu.
**Questions asked:** 3

#### Questions & Answers

1. **[Scope]** Bạn chọn hướng bảng màu nào cho Split Vibe?
   - Options: B. Violet (Recommended) | A. Emerald | C. Coral
   - **Answer:** B. Violet
   - **Rationale:** Lý do do người lập plan nêu trong lựa chọn và chủ sản phẩm chọn theo: khác hẳn màu xanh lá của đối thủ lớn nhất, hợp với chữ "Vibe", và để dành xanh lá, đỏ cho nghĩa "đã trả", "còn nợ".

2. **[Scope]** Phase 1 đã xong trên nhánh `feat/phase-01-foundation`, CI xanh. Bạn muốn đưa nó vào `main` bằng cách nào?
   - Options: Mở pull request (Recommended) | Merge thẳng vào main | Chưa, giữ nguyên nhánh
   - **Answer:** Merge thẳng vào main
   - **Rationale:** Chủ sản phẩm không nêu lý do. Hệ quả: lần này CI chạy sau khi mã đã vào `main`, không phải trước.

3. **[Risks]** Plan ghi "kiểm chữ ký phụ thuộc", bản đã làm kiểm checksum SHA-256. Có thêm kiểm chữ ký PGP không?
   - Options: Checksum là đủ (Recommended) | Thêm kiểm chữ ký PGP
   - **Answer:** Checksum là đủ
   - **Rationale:** Lý do do người lập plan nêu trong lựa chọn và chủ sản phẩm chọn theo: checksum đã chặn được thư viện bị thay hoặc bị giả; kiểm chữ ký thêm việc quản lý khóa mà nhiều thư viện vẫn không ký.

#### Confirmed Decisions
- Bảng màu của Split Vibe theo hướng B. Violet.
- Phase 1 vào `main` bằng fast-forward, không qua pull request.
- Kiểm phụ thuộc bằng checksum SHA-256; không làm kiểm chữ ký PGP.

#### Action Items
- [x] Fast-forward `main` tới nhánh Phase 1 và đẩy lên (commit `c9e7bb0`)
- [x] Ghi lựa chọn bảng màu vào Phase 2
- [x] Sửa chữ "kiểm chữ ký phụ thuộc" trong Phase 1 thành kiểm checksum

#### Impact on Phases
- Phase 1: việc kiểm phụ thuộc được coi là xong với checksum.
- Phase 2: bước 1 xong; bước 2 viết token theo hướng Violet.

### Whole-Plan Consistency Sweep (Session 3)
- Files reread: plan.md, phase-01-start, phase-02-design-system-expressive
- Decision deltas checked: 3
- Reconciled stale references: 4
- Unresolved contradictions: 0

### Session 4 — 2026-11-14
**Trigger:** Phase 2 xong trên nhánh `feat/phase-02-design-system`: mọi kiểm tra ở máy, 8 test thiết bị và danh sách checksum trên máy sạch đều xanh, review độc lập đã được sửa hết. Chủ sản phẩm nhắn "Merge đi".
**Questions asked:** 0 (chủ sản phẩm tự quyết)

#### Confirmed Decisions
- Phase 2 vào `main` bằng fast-forward, như Phase 1. CI của `main` chạy sau khi mã đã vào.

#### Action Items
- [x] Fast-forward `main` tới đầu nhánh Phase 2 và đẩy lên
- [ ] Chủ sản phẩm duyệt giao diện trên máy thật bằng bản dev
- [ ] Đo ngân sách hiệu năng trên máy tham chiếu

#### Impact on Phases
- Phase 2: mã đã vào `main`. Hai tiêu chí cần chủ sản phẩm còn mở (duyệt giao diện, số đo trên máy tham chiếu), nên trạng thái vẫn là In Progress.
- Phase 3 có thể bắt đầu từ `main`.

### Session 5 — 2026-11-14
**Trigger:** Trước khi bắt đầu Phase 3, chủ sản phẩm nhắn "AI gọi qua proxy trên cloudflare nhé. Giống như skim browser". Proxy của `dantech0xff/skim-browser` đếm lượt theo mã cài đặt và IP, không có tài khoản, nên hai điểm của plan phải hỏi lại.
**Questions asked:** 2

#### Questions & Answers

1. **[Scope]** Proxy của skim đếm lượt theo mã cài đặt và IP, không cần đăng nhập. Hạn mức đã chốt là 3 lượt thử, 10 lượt mỗi tháng khi đăng nhập Google, 300 lượt mỗi tháng cho Pro. Proxy nên tính lượt thế nào?
   - Options: Giữ 3 / 10 / 300 (Recommended) | Giống hệt skim
   - **Answer:** Giữ 3 / 10 / 300
   - **Rationale:** Lý do do người lập plan nêu trong lựa chọn và chủ sản phẩm chọn theo: chưa đăng nhập thì đếm theo máy và IP như skim; đăng nhập Google thì proxy tự kiểm token Google; Pro thêm ở Phase 8; trần chi phí theo ngày giữ như skim.

2. **[Architecture]** Plan Phase 3 còn dùng Firebase cho đăng nhập, dữ liệu, Crashlytics và Remote Config. Khi AI đã chuyển sang Cloudflare, Firebase có còn giữ không?
   - Options: Bỏ khỏi Phase 3 (Recommended) | Giữ Firebase cho đăng nhập và dữ liệu
   - **Answer:** Bỏ khỏi Phase 3
   - **Rationale:** Lý do do người lập plan nêu trong lựa chọn và chủ sản phẩm chọn theo: AI và việc kiểm đăng nhập nằm hết trên Cloudflare, không cần project Firebase hay gói Blaze; có đồng bộ dữ liệu không thì quyết ở Phase 5 và Phase 6; báo lỗi crash dùng cách khác hoặc để sau.

#### Confirmed Decisions
- AI gọi qua một Worker trên Cloudflare, theo cách proxy của skim-browser: key là secret của Worker, bộ đếm trong Durable Objects, giới hạn theo IP đã băm, trần chi phí theo người gọi, theo IP và toàn cục.
- Hạn mức giữ ba mức: 3 lượt thử cho mỗi bản cài chưa đăng nhập, 10 lượt mỗi tháng khi đăng nhập Google (proxy tự kiểm ID token của Google), 300 lượt mỗi tháng cho Pro ở Phase 8.
- Phase 3 không dùng Firebase. Không có App Check hay Play Integrity, như skim-browser.
- Prompt và schema vẫn nằm trên proxy (quyết định của vòng phản biện), nên proxy chỉ đọc hóa đơn chứ không là một cổng Claude dùng chung.

#### Action Items
- [x] Viết lại Phase 3 thành `phase-03-cloudflare-ai-proxy.md`
- [x] Sửa các phần của Phase 1, 4, 7, 8, 9 còn dựa vào Firebase của Phase 3; ghi chú ở Phase 5 và 6 rằng backend chốt khi bắt đầu
- [ ] Chủ sản phẩm chuẩn bị tài khoản Cloudflare, key Anthropic và OAuth client của Google trước bước triển khai

#### Impact on Phases
- Phase 3: viết lại toàn bộ quanh proxy trên Cloudflare.
- Phase 4: hóa đơn và bản nháp lưu trong cơ sở dữ liệu trên máy; đường tỷ giá chuyển lên proxy.
- Phase 5 và 6: backend cho dữ liệu dùng chung chưa chốt; các mục ghi Firebase là bản cũ, sửa khi bắt đầu phase.
- Phase 7: "chỉnh bằng lời" là một đường mới trên proxy.
- Phase 8: người mua là tài khoản Google mà proxy đã kiểm; gói hiệu lực nằm trên proxy.
- Phase 9: công tắc tắt AI và phiên bản tối thiểu nằm trên proxy; cách báo lỗi crash chọn ở Phase 9, mặc định là Android vitals của Play Console.
