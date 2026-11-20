---
title: "Split Vibe: đưa giao diện mới vào app"
description: "Tám giai đoạn đưa bản thiết kế lại đã duyệt trên canvas vào app Android và các trang web dùng chung token: bảng màu, phông chữ, thành phần, thanh điều hướng dưới, từng nhóm màn hình, kiểm thử và tài liệu."
status: pending
priority: P1
effort: "25d"
branch: feat/ui-redesign
tags: [feature, frontend, refactor]
blockedBy: []
blocks: []
created: 2026-11-20
---

# Split Vibe: đưa giao diện mới vào app

## Overview

Tám giai đoạn đưa bản thiết kế lại đã duyệt trên canvas vào app Android và các trang web dùng chung token. App giữ nguyên mọi tính năng và dữ liệu; chỉ diện mạo, bố cục và điều hướng thay đổi. Bối cảnh, câu trả lời của chủ sản phẩm và danh sách bảng nằm trong [bản ghi thiết kế lại](../reports/brainstorm-261120-0148-ui-redesign.md).

Ước lượng tổng công là 25 ngày làm việc cho một người làm cùng trợ lý AI, sai số khoảng 40%. Con số này chỉ để so sánh các giai đoạn, không phải cam kết ngày phát hành.

## Nguồn thiết kế

- Canvas "Split Vibe: thiết kế lại giao diện" trên claude.ai (riêng tư của chủ sản phẩm) có 37 bảng thiết kế: 32 bảng giao diện sáng và 5 bảng giao diện tối. Mỗi bảng là một tệp HTML rộng 390 px với kiểu dáng viết ngay trên phần tử, nên kích thước, màu và khoảng cách đọc được trực tiếp; 1 px trên bảng ứng với 1 dp trong app.
- Tệp nguồn của canvas không nằm trong repo. Đầu mỗi giai đoạn, phiên điều phối tải các bảng của giai đoạn đó từ canvas về thư mục tạm và giao đường dẫn cho người thực hiện.
- Khi bảng và app hiện tại khác nhau về chữ, chuỗi trong `app/src/main/res/values/strings.xml` thắng, trừ các nhãn mới liệt kê trong từng giai đoạn.

## Quyết định đã chốt với chủ sản phẩm (20/11/2026)

- Hướng thẩm mỹ: trẻ, vui, đậm màu; khối tím điện, màu nhấn xanh chanh, chữ màu mực trên nền trung tính.
- Được làm lại cả điều hướng: thanh dưới với Home, Groups, Requests, Insights và nút "Split a bill" ở giữa.
- Bỏ nhãn "Photo stays on this phone" và bỏ phần "Show what was sent to Claude" trên màn kết quả.
- App không được gợi ý rằng việc đọc hóa đơn do một model OCR ở máy chủ làm. Màn xin đồng ý và chính sách quyền riêng tư giữ nguyên nội dung.

Mặc định do người lập plan đặt, chủ sản phẩm đổi được:
- Mỗi tab giữ lịch sử riêng theo công thức nhiều back stack chính thức của Navigation 3; chỉ bốn màn gốc của tab hiện thanh dưới; bấm lại tab đang mở thì về màn gốc của tab đó.
- Đóng gói bảy tệp phông tĩnh (Bricolage Grotesque Bold, ExtraBold và ExtraBold bản 96pt; Be Vietnam Pro 400 đến 700). Mọi số tiền đặt bằng Bricolage Grotesque vì Be Vietnam Pro không có chữ số đều chiều rộng.
- Ảnh tham chiếu chỉ được ghi lại và commit một lần ở giai đoạn 7; trong các giai đoạn 1 đến 6, ảnh mới được dựng ra thư mục tạm để chủ sản phẩm xem.
- Bản tóm tắt PDF, hai khung hình chuyển cảnh và bố cục các trang web không được thiết kế lại.

## Goals

| # | Goal | Priority |
|---|------|----------|
| 1 | Mọi màn hình dùng token, thành phần và bố cục của bản thiết kế, ở cả giao diện sáng và tối | P1 |
| 2 | Điều hướng mới hoạt động đúng với nút lùi, vuốt lùi có xem trước, liên kết mở app và khôi phục trạng thái | P1 |
| 3 | Không mất tính năng, dữ liệu hay tiêu chuẩn trợ năng nào đang có | P1 |
| 4 | Kiểm thử, ảnh tham chiếu, hồ sơ khởi động, tài liệu và ảnh cửa hàng khớp với giao diện mới | P1 |

## Phases

| # | Phase | Status |
|---|-------|--------|
| 1 | [Nền tảng: màu, chữ, hình khối](./phase-01-start.md) | Pending |
| 2 | [Thành phần dùng chung](./phase-02-shared-components.md) | Pending |
| 3 | [Điều hướng với thanh dưới](./phase-03-navigation-bottom-bar.md) | Pending |
| 4 | [Trang chủ và luồng chia hóa đơn](./phase-04-home-and-split-flow.md) | Pending |
| 5 | [Nhóm, yêu cầu trả tiền và liên kết hóa đơn](./phase-05-groups-requests-bill-links.md) | Pending |
| 6 | [Hóa đơn, thống kê, cài đặt và Pro](./phase-06-bills-insights-settings-pro.md) | Pending |
| 7 | [Kiểm thử, ảnh tham chiếu và hồ sơ khởi động](./phase-07-tests-screenshots-profile.md) | Pending |
| 8 | [Web, tài liệu, ảnh cửa hàng và hoàn tất](./phase-08-web-docs-store-finish.md) | Pending |

Thứ tự và phụ thuộc: 1, rồi 2, rồi 3. Các giai đoạn 4, 5 và 6 cần 3 và chạy song song được vì mỗi giai đoạn sở hữu các gói `ui/` riêng; `strings.xml`, `AppNavHost.kt` và `ui/common/` do phiên điều phối ghép. Giai đoạn 7 cần 4, 5 và 6. Giai đoạn 8 cần 7; phần web của nó làm song song được với 4 đến 6.

Mỗi giai đoạn kết thúc bằng: kiểm thử của giai đoạn qua, một lượt review độc lập, sửa các phát hiện, và ảnh màn hình thật gửi chủ sản phẩm so với canvas.

## Ngoài phạm vi

- Tính năng mới, thay đổi máy chủ, giá Pro, hạn mức AI, và bản dịch tiếng Việt.
- Bản tóm tắt PDF và bố cục các trang web cho người nhận liên kết.
- Phát hành lên Google Play; việc này vẫn theo `docs/release-checklist.md`.

## Success Criteria

- [ ] 37 bảng thiết kế đều có màn hình tương ứng trong app, và chủ sản phẩm duyệt ảnh chụp màn hình thật của từng nhóm.
- [ ] Kiểm thử đơn vị, ktlint, lint và kiểm tra gói qua khi chạy tại chỗ, và CI trên `main` xanh sau khi merge.
- [ ] Kiểm thử trên máy ảo qua ở API 34, và các hành trình qua ở API 26, 30 và 36.
- [ ] Các kiểm tra trợ năng của kiểm thử trên máy ảo qua; chữ cỡ 200% không bị cắt trên bốn màn chính.
- [ ] `ColorTokensTest` và `WebTokensTest` qua với bảng màu mới; `web/src/tokens.css` khớp với token của app.
- [ ] Ảnh tham chiếu được ghi lại một lần, không còn ảnh nào mà không bài kiểm thử nào ghi.
- [ ] Tài liệu hệ thiết kế, kiến trúc, bảng ảnh cho Google Play và ảnh trong README mô tả giao diện mới.

## Tài liệu của plan

- Rà code: [hệ thiết kế và điều hướng](./reports/scout-design-system-navigation.md), [màn hình và dữ liệu](./reports/scout-screens-and-state.md), [kiểm thử, CI, web và tài liệu](./reports/scout-tests-ci-web-docs.md).
- Tra cứu: [thanh dưới với Navigation 3](./research/researcher-01-navigation3-bottom-bar.md), [hai phông chữ](./research/researcher-02-fonts.md).

<!-- slug: ui-redesign -->
