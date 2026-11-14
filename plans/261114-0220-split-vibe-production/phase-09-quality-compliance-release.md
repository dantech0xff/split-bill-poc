---
title: "Phase 9: Chất lượng, tuân thủ và phát hành"
status: todo
phase: 9
priority: P1
effort: "10d"
dependencies: [2, 3, 4, 5, 6, 7, 8]
---

# Phase 9: Chất lượng, tuân thủ và phát hành

## Overview

Split Vibe 1.0 lên Google Play cho toàn cầu: qua đủ cổng chất lượng, có hồ sơ quyền riêng tư và pháp lý, qua vòng thử kín bắt buộc, phát hành theo từng nấc có theo dõi, và có quy trình vận hành sau ra mắt.

## Context

- Các phần còn treo từ POC được đóng ở các giai đoạn trước: lần gọi API thật (Phase 3), test trên thiết bị chạy bằng Gradle (Phase 1). Giai đoạn này kiểm lại toàn bộ trên bản release.
- Dữ kiện: [báo cáo giao diện và Play](../reports/researcher-261114-0209-android-expressive-ui-and-play-requirements.md), mục 5; [báo cáo backend](../reports/researcher-261114-0209-product-backend-and-payment-links.md), mục 5.
- Một số việc trong giai đoạn này **bắt đầu sớm**, song song với Phase 5 đến Phase 8: vòng thử kín (cần ít nhất 14 ngày), chính sách quyền riêng tư, hồ sơ Play.

## Key Insights

- Tài khoản nhà phát triển cá nhân tạo sau 13/11/2023 phải có ít nhất **12 người thử tham gia liên tục 14 ngày** trước khi xin quyền phát hành. Đây là đường găng của lịch phát hành.
- App nhắm API 36 là đạt yêu cầu hiện hành. Thư viện gốc đã căn 16 KB (kiểm ngày 14/11/2026: 8 trên 8 thư viện đạt); kiểm lại trên AAB cuối.
- Gửi văn bản hóa đơn tới API của model được tính là **thu thập dữ liệu** trong biểu mẫu An toàn dữ liệu, dù chỉ xử lý tạm thời.
- App có tài khoản nên phải có xóa tài khoản trong app **và** một trang web để yêu cầu xóa.
- Phục vụ người dùng ở EU thì nhiều khả năng cần đại diện tại EU (GDPR Điều 27). Lưu dữ liệu người dùng Việt Nam trên đám mây nước ngoài là chuyển dữ liệu xuyên biên giới theo Luật 91/2025 và cần hồ sơ đánh giá tác động. Hai việc này cần người có chuyên môn pháp lý xác nhận; plan chỉ nêu việc phải làm.
- material3 1.5.0 đang ở bản RC. Không phát hành production khi nó chưa ra bản stable.

## Requirements

Cổng chất lượng trên bản release:
- Unit test, test rules, test giao kèo của cổng AI, so ảnh chụp: tất cả xanh trong CI.
- Test đầu-cuối trên thiết bị do Gradle quản lý ở API 26, 30, 34, 36: luồng chia hóa đơn mẫu, nhập tay, tạo yêu cầu, mua thử.
- Ngân sách hiệu năng của Phase 2 đạt trên máy tham chiếu với bản release có baseline profile.
- Bộ đánh giá AI đạt ngưỡng đã định ở Phase 3 và Phase 7 trên model đang dùng ở prod.
- Kiểm tra tiếp cận: TalkBack đi hết luồng chính, cỡ chữ 200%, độ tương phản.
- Ngôn ngữ lúc ra mắt: tiếng Anh và tiếng Việt; chuỗi nằm hết trong tài nguyên, chạy thử ngôn ngữ giả để bắt chữ bị cắt.

Rà soát bảo mật:
- Mô hình mối đe dọa cho cổng AI (đốt chi phí, chèn lệnh qua hóa đơn), rules, trang web công khai, webhook, link mời.
- Quét phụ thuộc và quét secret trong CI; kiểm tra bản release không chứa key, không bật gỡ lỗi, không cho phép HTTP thường.

Hồ sơ quyền riêng tư và pháp lý (chủ sản phẩm chịu trách nhiệm, người làm chuẩn bị bản nháp):
- Chính sách quyền riêng tư và điều khoản sử dụng trên web, nêu rõ văn bản hóa đơn được gửi tới API của Anthropic để xử lý, ảnh không rời máy, thời hạn lưu.
- Biểu mẫu An toàn dữ liệu, phân loại nội dung, khai báo thuê bao.
- Xóa tài khoản trong app và trang web yêu cầu xóa.
- Chế độ đồng ý cho Analytics: mặc định từ chối ở EEA, Anh, Thụy Sĩ cho tới khi người dùng đồng ý.
- Thỏa thuận xử lý dữ liệu với Google và Anthropic.
- Đánh giá việc cần đại diện tại EU và hồ sơ chuyển dữ liệu xuyên biên giới của Việt Nam.
- Tra cứu trùng nhãn hiệu cho tên "Split Vibe" trước khi lên Play.

Phát hành:
- Kênh nội bộ, rồi thử kín (12 người, 14 ngày), rồi thử mở, rồi production theo nấc 5%, 20%, 50%, 100%.
- Điều kiện lên nấc kế tiếp: tỷ lệ người dùng không gặp sự cố từ 99,5% trở lên, tỷ lệ ANR dưới ngưỡng xấu của Play, chi phí AI mỗi người dùng trong ngân sách, không có lỗi mất dữ liệu.
- Hồ sơ cửa hàng: ảnh chụp sinh từ đường dựng ảnh của dự án, mô tả tiếng Anh và tiếng Việt.

Vận hành:
- Bảng theo dõi: sự cố, phễu sử dụng (quét, phân tích, chia sẻ, đánh dấu đã trả), chi phí AI theo ngày và theo người dùng.
- Cảnh báo: tỷ lệ lỗi cổng AI, chi phí bất thường, hàm theo lịch thất bại.
- Công tắc qua Remote Config: tắt AI (app vẫn nhập tay được), phiên bản tối thiểu được hỗ trợ.
- Sao lưu Firestore theo lịch và một lần diễn tập khôi phục.
- Sổ tay sự cố và kênh hỗ trợ người dùng.

## Architecture

Không có kiến trúc mới. Giai đoạn này thêm luồng CI phát hành:

```
tag phiên bản ─▶ CI: build AAB prod ─▶ ký bằng khóa upload ─▶ tải lên kênh nội bộ của Play
                 └─ triển khai functions, rules, hosting lên project prod (có bước duyệt tay)
```

## Files

Tạo mới:
- `.github/workflows/release.yml`
- `app/src/androidTest/java/com/dantech/splitvibe/e2e/` (test đầu-cuối trên thiết bị)
- `docs/privacy-policy.md`, `docs/terms.md` (nguồn của trang web pháp lý), `docs/security-threat-model.md`, `docs/runbook.md`, `docs/release-checklist.md`
- `web/account-deletion.html`
- `app/src/main/res/values-vi/strings.xml`

Sửa:
- `app/src/main/res/values/strings.xml` (gom mọi chuỗi đang viết thẳng trong mã Compose)
- `README.md` (viết lại cho sản phẩm thay cho POC)
- `app/build.gradle.kts` (cấu hình thiết bị do Gradle quản lý cho 4 mức API)

## Implementation Steps

1. Ngay khi Phase 5 xong: đưa bản dựng lên kênh thử kín và bắt đầu đếm 14 ngày với 12 người thử.
2. Gom chuỗi vào tài nguyên, dịch tiếng Việt, chạy ngôn ngữ giả.
3. Viết test đầu-cuối trên thiết bị và chạy trên 4 mức API.
4. Rà soát bảo mật theo mô hình mối đe dọa; sửa mọi phát hiện mức cao trước khi đi tiếp.
5. Soạn chính sách quyền riêng tư, điều khoản, trang xóa tài khoản; điền An toàn dữ liệu và phân loại nội dung.
6. Viết luồng CI phát hành; diễn tập một lần trên project dev.
7. Dựng bảng theo dõi, cảnh báo, sao lưu; diễn tập khôi phục và diễn tập tắt AI.
8. Kiểm lại 16 KB và API mục tiêu trên AAB cuối; xác nhận material3 đã ở bản stable.
9. Xin quyền production, phát hành theo nấc, theo dõi 7 ngày ở 100%.

## Todo

- [ ] Vòng thử kín 12 người trong 14 ngày
- [ ] Chuỗi trong tài nguyên, tiếng Việt, ngôn ngữ giả
- [ ] Test đầu-cuối trên 4 mức API
- [ ] Rà soát bảo mật và sửa phát hiện mức cao
- [ ] Chính sách quyền riêng tư, điều khoản, trang xóa tài khoản
- [ ] An toàn dữ liệu, phân loại nội dung, chế độ đồng ý
- [ ] Đánh giá đại diện EU, hồ sơ xuyên biên giới Việt Nam, tra nhãn hiệu
- [ ] Luồng CI phát hành và diễn tập
- [ ] Bảng theo dõi, cảnh báo, sao lưu, sổ tay sự cố
- [ ] Kiểm 16 KB, API mục tiêu, material3 stable trên AAB cuối
- [ ] Phát hành theo nấc tới 100% và theo dõi 7 ngày

## Success Criteria

- Mọi cổng chất lượng xanh trên bản release dùng để phát hành; kết quả lưu trong `plans/reports/`.
- Play Console cấp quyền production sau vòng thử kín.
- Bản phát hành ở 100% trong 7 ngày với tỷ lệ người dùng không gặp sự cố từ 99,5% trở lên và tỷ lệ ANR dưới ngưỡng xấu của Play.
- Biểu mẫu An toàn dữ liệu được duyệt; chính sách quyền riêng tư và trang xóa tài khoản truy cập được công khai.
- Xóa tài khoản trong app xóa hết dữ liệu của người đó trên Firestore (kiểm bằng test trên emulator và một lần trên prod với tài khoản thử).
- Diễn tập: bật công tắc tắt AI thì app chuyển sang nhập tay trong vòng 1 phút; khôi phục Firestore từ bản sao lưu vào project dev thành công.
- `zipalign -c -P 16` trên các APK tách từ AAB cuối báo đạt; `targetSdk` là 36; material3 là bản stable.
- Chi phí AI trung bình mỗi người dùng hoạt động trong tuần đầu nằm trong ngân sách đã đặt ở Phase 3.

## Risks

| Rủi ro | Dấu hiệu nhận biết | Phản ứng đã định |
|---|---|---|
| Không đủ 12 người thử liên tục 14 ngày | Số người tham gia tụt dưới 12 | Tuyển dư 20 người ngay từ đầu; đồng hồ 14 ngày chạy lại nếu thiếu |
| Play từ chối vì khai báo dữ liệu hoặc thanh toán | Thư từ chối khi duyệt | Sửa theo thư và nộp lại; không lách chính sách |
| material3 1.5.0 chưa stable lúc sẵn sàng phát hành | Trang phát hành của AndroidX chưa có 1.5.0 | Lùi ngày phát hành production; tiếp tục thử mở |
| Yêu cầu pháp lý EU hoặc Việt Nam chưa xong | Chưa có xác nhận của người có chuyên môn | Chủ sản phẩm quyết định: lùi ngày hoặc tạm chưa mở ở vùng đó |
| Chi phí AI vọt lên sau ra mắt | Cảnh báo chi phí | Hạ hạn mức miễn phí qua Remote Config; bật công tắc tắt AI nếu cần |
| Tên "Split Vibe" trùng nhãn hiệu | Kết quả tra cứu | Đổi tên hiển thị và package **trước** lần tải lên Play đầu tiên, vì sau đó applicationId không đổi được |

## Security

- Luồng phát hành cần một bước duyệt tay trước khi chạm vào project prod.
- Khóa upload, tài khoản dịch vụ của Play và của Firebase chỉ nằm trong secret của CI với quyền tối thiểu.
- Bản release: không gỡ lỗi được, không cho HTTP thường, không log dữ liệu người dùng.

## Next

Sau v1.0: xem danh sách gợi ý trong `plan.md` (iOS, hóa đơn không phải chữ Latin, nhập từ ngân hàng).
