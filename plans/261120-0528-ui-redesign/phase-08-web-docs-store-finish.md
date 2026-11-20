---
title: "Phase 8: Web, tài liệu, ảnh cửa hàng và hoàn tất"
status: pending
phase: 8
priority: P2
effort: "2d"
dependencies: [7]
---

# Phase 8: Web, tài liệu, ảnh cửa hàng và hoàn tất

## Overview

Các trang web dùng chung token hiển thị đúng với bảng màu mới, tài liệu mô tả giao diện mới, bảng ảnh cho Google Play và ảnh trong README được thay, và nhánh sẵn sàng để chủ sản phẩm cho merge.

## Context

- Đường đi của token sang web, các bài kiểm thử web và tài liệu có nói về giao diện: [rà kiểm thử, CI, web và tài liệu](./reports/scout-tests-ci-web-docs.md), mục 5 đến 7.
- Phần web làm song song được với các giai đoạn 4 đến 6, vì `web/src/tokens.css` đã được ghi lại ở giai đoạn 1.

## Key Insights

- Token đi một chiều: Kotlin, `WebTokensTest`, `web/src/tokens.css`, `web/src/app.css`, gói của Worker. `app.css` chỉ dùng biến `--sv-…`, nên đổi nghĩa một vai màu (ví dụ `secondaryContainer` thành mực) đổi luôn trang web mà không bài kiểm thử nào báo.
- Trang web chỉ tới người dùng khi Worker được triển khai lại, việc chỉ xảy ra trong quy trình phát hành. Cho tới lúc đó trang đang chạy giữ diện mạo cũ.
- Trang web cấm kiểu dáng viết trên phần tử và có ngân sách 150 KB nén cho lần tải đầu; ngân sách này không được CI kiểm.
- Kiểm thử Playwright của web không chạy trong CI; phải chạy tay.
- Bảy ảnh của bảng ảnh cho Google Play lấy từ ảnh tham chiếu; hai trong số đó được cắt tay từ ảnh dài.
- `docs/screenshots/new-split.png` trong README không khớp ảnh tham chiếu nào hiện có.

## Requirements

- Các trang `/s/`, `/r/`, `/g/`, `/delete-account`, `/privacy`, `/terms` đọc được và đúng tương phản với token mới, ở cả giao diện sáng và tối, trên bề rộng điện thoại. Bố cục và chữ của trang không đổi; phông vẫn là phông hệ thống.
- `docs/design-system.md` mô tả bảng màu, chữ, hình khối, mức nổi, `SvPalette`, mọi thành phần và quy tắc màu nút mới; bảng thành phần khớp với mã.
- `docs/architecture.md`, `docs/web.md`, `docs/testing.md` và `README.md` khớp với điều hướng nhiều tab, đường đi của token, cách ghi ảnh và các lệnh hiện hành.
- `docs/store-listing.md` liệt kê bảy ảnh mới với tên tệp, kích thước và nội dung; `docs/play-console.md` không còn nhắc tới giá trên ảnh cũ nếu ảnh đã đổi.
- README dùng một ảnh lấy từ ảnh tham chiếu hiện hành.
- Bản ghi thiết kế lại và plan này phản ánh kết quả thật.

## Files to Create / Modify

- Modify: `web/src/app.css` nếu một vai màu đổi nghĩa làm trang sai.
- Modify: `docs/design-system.md`, `docs/architecture.md`, `docs/web.md`, `docs/testing.md`, `docs/store-listing.md`, `docs/play-console.md`, `README.md`.
- Modify: `docs/screenshots/new-split.png`.
- Modify: `plans/reports/brainstorm-261120-0148-ui-redesign.md`, `plans/261120-0528-ui-redesign/plan.md` và các tệp giai đoạn (trạng thái và kết quả).
- Create: một mục nhật ký trong `plans/journals/` qua `ak journal create`.

## Implementation Steps

1. Đọc `web/src/app.css` và lập bảng vai màu nào được dùng ở đâu. Với mỗi vai đã đổi nghĩa ở giai đoạn 1, quyết định trang giữ vai đó hay chuyển sang vai khác; sửa `app.css` bằng lớp, không viết kiểu dáng trên phần tử.
2. Chạy `npm test --prefix web`, `npm run typecheck` và `npm test` trong `proxy/`, rồi `npm run size --prefix web`.
3. Chạy tay `npm run e2e --prefix web` và chụp sáu trang ở bề rộng điện thoại, sáng và tối, gửi chủ sản phẩm.
4. Viết lại `docs/design-system.md` theo mã sau giai đoạn 7; đối chiếu bảng thành phần với thư mục `components/`.
5. Cập nhật các tài liệu còn lại; với mỗi câu nói về giao diện hoặc lệnh, đối chiếu với mã, bài kiểm thử hoặc quy trình CI.
6. Chọn lại bảy ảnh cho Google Play từ ảnh tham chiếu mới, cắt hai ảnh dài về cỡ điện thoại như tài liệu mô tả, và ghi lại bảng trong `docs/store-listing.md`. Thay ảnh trong README.
7. Cho một lượt review độc lập trên toàn bộ thay đổi của nhánh, sửa các phát hiện, chạy lại các kiểm tra bị ảnh hưởng.
8. Chạy tại chỗ mọi lệnh mà `ci.yml` chạy và ghi kết quả vào plan. Chỉ mở pull request khi chủ sản phẩm yêu cầu. Viết nhật ký.
9. Dừng lại chờ chủ sản phẩm nói "ok merge".

## Todo

- [ ] `app.css` đã rà theo vai màu mới
- [ ] Kiểm thử web và proxy, ngân sách dung lượng
- [ ] Ảnh sáu trang web gửi chủ sản phẩm
- [ ] `docs/design-system.md` viết lại
- [ ] Các tài liệu còn lại và README
- [ ] Bảy ảnh cho Google Play và bảng của chúng
- [ ] Review độc lập và sửa phát hiện
- [ ] Các lệnh của CI qua tại chỗ, nhật ký, chờ merge

## Success Criteria

- `npm test --prefix web` và `npm test` trong `proxy/` qua; lần tải đầu của web không quá 150 KB nén.
- Chủ sản phẩm duyệt ảnh sáu trang web.
- Mọi lệnh ghi trong tài liệu chạy được, và mọi tệp ảnh tài liệu nhắc tới đều tồn tại.
- Mọi lệnh của `ci.yml` qua khi chạy tại chỗ; sau khi merge, CI trên `main` xanh ở cả hai việc.

## Risk Assessment

- Một vai màu đổi nghĩa làm trang web khó đọc mà không ai thấy trước khi phát hành: bước 1 và 3 là để thấy sớm; nếu phải chọn, trang web giữ tương phản và hy sinh sự giống hệt app.
- Kiểm thử Playwright bám vào chữ và lớp: giai đoạn này không đổi chữ hay tên lớp của trang.
- Trang web đang chạy khác app mới cho tới lần triển khai Worker kế tiếp: ghi rõ trong báo cáo cuối; triển khai sớm hơn là quyết định của chủ sản phẩm.

## Rollback

Hoàn lại các commit của giai đoạn. Trang web đang chạy không đổi cho tới khi Worker được triển khai lại.
