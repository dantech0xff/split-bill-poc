---
title: "Phase 9: Trang web"
status: completed
phase: 9
priority: P1
effort: "5d"
dependencies: [8]
---

# Phase 9: Trang web

## Overview

Các trang web mà người nhận liên kết nhìn thấy mang diện mạo của bản thiết kế web: trang hóa đơn `/s/`, yêu cầu trả tiền `/r/`, lời mời vào nhóm `/g/`, `/delete-account`, `/privacy` và `/terms`. Chữ, đường dẫn và hành vi của từng trang không đổi. Số tiền và ngày trên web theo vùng của trình duyệt, cùng quy tắc với app.

Giai đoạn này chỉ bắt đầu khi chủ sản phẩm đã duyệt các bảng web. Nó không đụng mã của app, nên có thể chạy song song với các giai đoạn 5 đến 8 nếu chủ sản phẩm muốn; khi đó nó dùng bảng màu của giai đoạn 1 và rà lại màu sau khi giai đoạn 8 chốt token.

## Context

- Mọi trang, trạng thái, câu chữ, kiểu dáng, chính sách bảo mật nội dung và bài kiểm thử hiện có: [rà các trang web](./reports/scout-web-pages.md).
- Bảng web trên trang "Trang web" của canvas, chủ sản phẩm duyệt ngày 21/11/2026 và lưu trong [`boards/web/`](./boards/web/README.md) cùng mã băm: `WebBill`, `WebBillClosing`, `WebBillSettled`, `WebStates`, `WebDarkBill`, `WebRequest`, `WebInvite`, `WebDeleteAccount`, `WebDeleteSteps`, `WebLegal`, `WebDarkRequest`, `WebWide`.
- Quy tắc viết tiền và ngày: [giai đoạn 4](./phase-04-money-and-date-formats.md).
- Nguồn và mã băm của tệp phông: [giai đoạn 1](./phase-01-colors-type-shapes.md).

## Key Insights

- Chữ và cấu trúc của trang bị ghim bởi kiểm thử: các bài của `web/tests/` và 8 bài Playwright chọn phần tử theo câu chữ chính xác và theo tên lớp như `.hero`, `.hero__amount`, `.hero__status`, `.payment`, `.details-card`, `.details-card__text`, `.steps`, `.notice`, `.banner`, `.error-text`, `.item`, `.item__name`, `.person`, `.person__row`, `.person__label`, `.person--you`, `.chip--you`, `.claimer`, `.google-button` và `main.page`. Vì vậy việc chính là viết lại `web/src/app.css`; tên lớp và thứ tự phần tử giữ nguyên.
- Ba lớp đang được dùng mà chưa có luật nào: `hero__amount`, `person__note` và `person`. Bản thiết kế cần luật cho cả ba.
- Trang web cấm kiểu dáng viết trên phần tử: `style-src 'self'` trong chính sách bảo mật nội dung, và `web/scripts/page-checks.mjs` từ chối thuộc tính `style`. Bảng vẽ dùng kiểu dáng trên phần tử chỉ vì đó là cách vẽ; khi làm thật, mọi thứ thành lớp trong `app.css`.
- Hôm nay web không có tệp phông nào và bốn chỗ cùng chặn phông: cả ba chính sách trong `proxy/src/web.ts:57-91` không có `font-src`; `proxy/scripts/embed-web.mjs` và `web/scripts/verify-build.mjs:24` chỉ nhận tệp `.js` và `.css` trong `assets/`; `page-checks.mjs:66` chỉ cho thẻ `<link>` tới tệp kiểu dáng; và `webAssetRoute` không có kiểu nội dung cho phông. Muốn dùng hai phông của bản thiết kế thì phải sửa cả bốn chỗ cùng lúc.
- `npm run size` chỉ đếm `index.html` và các tệp mà trang đó trỏ tới bằng `src=` hoặc `href=`. Phông nạp qua `@font-face` trong tệp kiểu dáng không được đếm, nên ngân sách 150 KB hiện tại không nhìn thấy phông.
- Token đi một chiều từ Kotlin sang `web/src/tokens.css` qua `WebTokensTest`. Giai đoạn 1 giữ chữ và hình khối của web như cũ và không xuất `SvPalette`; giai đoạn này mở phần xuất đó cho đúng những gì web dùng.
- `web/src/money.ts`, `web/src/dates.ts` và `web/src/share-state.ts` viết mọi thứ bằng `Intl` với vùng cố định `"en"`, để khớp với app. App đổi quy tắc ở giai đoạn 4, nên web đổi theo.
- Trang web chỉ tới người dùng khi `release.yml` triển khai Worker. Kiểm thử Playwright và phép đo dung lượng không chạy trong CI; phải chạy tay.

## Requirements

- Mỗi trang và mỗi trạng thái trong bảng web trông như bảng của nó ở bề rộng 390 px và 1280 px, ở giao diện sáng và tối. Trang vẫn là một cột rộng tối đa 600 px nằm giữa.
- Không đổi một chữ nào của trang, không đổi đường dẫn, tiêu đề trang, vai và thuộc tính trợ năng, thứ tự tiêu đề, hay tên lớp mà kiểm thử chọn.
- Mã QR vẫn đen trên trắng ở cả hai giao diện, không bo góc, giữ vùng trắng bốn ô quanh mã.
- Vùng chạm tối thiểu 48 px; viền tiêu điểm thấy rõ; chữ đạt tương phản 4,5:1; thanh đang tải đứng yên khi người dùng tắt chuyển động.
- Chính sách bảo mật nội dung chỉ thêm `font-src 'self'` vào ba chính sách. Không thêm máy chủ ngoài nào, không nới `style-src`, không có kiểu dáng viết trên phần tử.
- Phông được phục vụ từ chính Worker, dạng WOFF2, chỉ gồm chữ Latin và tiếng Việt, với `font-display: swap` để chữ hiện ngay bằng phông hệ thống trong lúc phông tải. Dùng nhiều nhất ba tệp: Bricolage Grotesque 800, Be Vietnam Pro 400 và 600. Tệp 800 cắt từ bản 14pt (`BricolageGrotesque-ExtraBold.ttf`); bản 96pt không dùng trên web. Khi cắt bớt chữ, các mục tên mang dòng bản quyền và giấy phép trong tệp phông được giữ nguyên, và hai tệp `OFL.txt` nằm cạnh ba tệp phông trong `web/src/fonts/`.
- Lần tải đầu của HTML, mã và kiểu dáng vẫn không quá 150 KB nén. Ba tệp phông có trần riêng là 120 KB nén tổng cộng, và `npm run size` in ra, kiểm cả hai con số.
- Số tiền và ngày theo vùng của trình duyệt (`navigator.languages`), cùng quy tắc với app: dấu phân cách, vị trí ký hiệu và thứ tự ngày tháng theo vùng; chữ số luôn là 0 đến 9; ký hiệu tiền tệ và tên tháng theo tiếng Anh của trang. Danh sách tên người vẫn nối bằng chữ tiếng Anh.
- Các đường dẫn không phải là trang (`/`, `/s` không có gạch chéo cuối) vẫn trả lời như hôm nay.

## Files to Create / Modify

- Create: `web/src/fonts/` với ba tệp `.woff2` và một danh sách mã băm, kèm lệnh đã dùng để tạo chúng từ các tệp TTF ghim ở giai đoạn 1.
- Modify: `web/src/app.css` (viết lại theo bảng, giữ các bộ chọn mà kiểm thử dùng, thêm `@font-face`).
- Modify: `core/designsystem/src/test/java/com/dantech/splitvibe/designsystem/tokens/WebTokensTest.kt` và `web/src/tokens.css` (xuất thêm các token `SvPalette` mà web dùng, hình khối của theme, thang chữ và hai biến họ phông).
- Modify: `web/src/money.ts`, `web/src/dates.ts` (vùng của trình duyệt thay cho `"en"`).
- Modify, chỉ ở chỗ bảng cần thêm một phần tử bọc hoặc một lớp: `web/src/app.tsx`, `web/src/pages/share-sections.tsx`, `request-page.tsx`, `invite-page.tsx`, `delete-account-page.tsx`, `web/src/ui/payment-block.tsx`, `problem-view.tsx`, `footer.tsx`, `site-footer.tsx`, và khung trang pháp lý trong `web/scripts/static-pages.mjs`.
- Modify: `proxy/src/web.ts` (`font-src 'self'` ở ba chính sách, kiểu nội dung `font/woff2` trong `webAssetRoute`), `proxy/scripts/embed-web.mjs`, `web/scripts/verify-build.mjs`, `web/scripts/page-checks.mjs` (nhận tệp `.woff2` trong `assets/`, vẫn cấm mọi thứ khác), `web/scripts/size.mjs` (đếm và kiểm phông).
- Modify tests: `web/tests/money.test.ts` và bài kiểm thử ngày, `web/tests/page-checks.test.ts`, các bài `web/tests/*.test.tsx` có phần tử bọc đổi, bài kiểm thử tiêu đề phản hồi của `proxy/`, ba tệp `web/e2e/*.spec.ts`, và `web/playwright.config.ts` (đặt vùng cố định `en-US` để các câu chữ đã ghim còn đúng).

## Implementation Steps

1. Điều kiện vào: chủ sản phẩm duyệt các bảng web. Lưu chúng vào `boards/web/` cùng mã băm và cập nhật tệp này nếu việc duyệt làm đổi bảng.
2. Thử phông trước tiên: tạo ba tệp WOFF2 chỉ gồm chữ Latin và tiếng Việt từ các tệp TTF đã ghim, đo cỡ nén. Nếu tổng vượt 120 KB, dừng lại và đưa chủ sản phẩm chọn: bỏ một độ đậm, chỉ dùng phông mới cho tiêu đề và số tiền, hoặc giữ phông hệ thống cho web.
3. Mở đường cho phông: sửa ba chính sách, kiểu nội dung, kịch bản nhúng, kịch bản kiểm bản dựng và kiểm trang trong cùng một commit; thêm kiểm thử cho tiêu đề phản hồi và cho việc một tệp lạ trong `assets/` vẫn bị từ chối.
4. Mở phần xuất token trong `WebTokensTest` cho những gì web dùng, rồi ghi lại `tokens.css` bằng `-PwebTokens=write`.
5. Viết lại `app.css` theo từng bảng: khung trang và thanh thương hiệu, khối đầu trang, nhóm hàng, ô chọn món và nhãn tên, biểu ngữ trạng thái, lời nhắc và lỗi, nút, ô nhập, khối cách trả tiền với thẻ mã QR và thẻ chi tiết, các bước, chân trang, và phần `.prose` của trang pháp lý. Thêm luật cho `hero__amount`, `person__note` và `person`.
6. Sửa mã trang ở những chỗ bảng cần phần tử bọc; không đổi chữ, vai hay thứ tự.
7. Đổi `money.ts` và `dates.ts` sang vùng của trình duyệt theo quy tắc của giai đoạn 4; các hàm nhận vùng làm tham số để kiểm thử truyền vào. Thêm kiểm thử cho `en-US`, `vi-VN` và `de-DE`.
8. Chạy `npm test --prefix web`, `npm run build --prefix web`, `npm run size --prefix web`, rồi `npm run typecheck` và `npm test` trong `proxy/`. Chạy tay `npm run e2e --prefix web -- --trace off`.
9. Chụp mọi trang và mọi trạng thái trong bảng ở 390 px và 1280 px, sáng và tối, gửi chủ sản phẩm so với bảng. Sửa các chỗ lệch.
10. Cho một lượt review độc lập, sửa các phát hiện, chạy lại bước 8.

## Todo

- [x] Chủ sản phẩm duyệt bảng web, bảng được lưu vào `boards/web/`
- [x] Ba tệp phông WOFF2 được tạo, đo và ghi mã băm; quyết định về trần 120 KB được ghi lại
- [x] Chính sách, kiểu nội dung và ba kịch bản nhận phông, có kiểm thử
- [x] `WebTokensTest` xuất token cho web, `tokens.css` ghi lại
- [x] `app.css` viết lại theo bảng
- [x] Mã trang sửa ở chỗ cần phần tử bọc
- [x] Tiền và ngày theo vùng của trình duyệt, có kiểm thử
- [x] Kiểm thử web và proxy, bản dựng, dung lượng, Playwright
- [x] Ảnh các trang gửi chủ sản phẩm
- [x] Review độc lập và sửa phát hiện

## Success Criteria

- `npm test --prefix web` và `npm test` trong `proxy/` qua; ba tệp Playwright qua khi chạy tay.
- `npm run build --prefix web` qua, tức kịch bản kiểm bản dựng chấp nhận mọi tệp sinh ra.
- `npm run size --prefix web` báo HTML, mã và kiểu dáng không quá 150 KB nén, và phông không quá trần đã chốt.
- Phản hồi của ba loại trang mang chính sách chỉ khác hôm nay ở `font-src 'self'`.
- Chủ sản phẩm duyệt ảnh của các trang so với bảng.
- Với trình duyệt đặt `vi-VN`, trang hóa đơn hiện "231.000 ₫" và "14 Nov 2026"; với `en-US`, "₫231,000" và "Nov 14, 2026".

## Risk Assessment

- Phông vượt trần dung lượng: bước 2 đo trước khi viết dòng kiểu dáng nào, và nêu ba lối ra cho chủ sản phẩm.
- Sửa chính sách bảo mật nội dung làm lọt một nguồn khác: kiểm thử ở bước 3 so nguyên văn ba chính sách.
- Viết lại `app.css` làm gãy bài kiểm thử bám vào lớp: danh sách lớp ở mục Key Insights là thứ phải còn; chạy `npm test --prefix web` sau từng trang.
- Kiểu dáng mới làm mã QR khó quét: không áp bo góc, bóng hay màu lên phần tử `canvas`; thử quét bằng app ngân hàng trên ảnh thật.
- Chữ nhảy khi phông tải xong: chọn phông thay thế có bề rộng gần, và đặt cỡ dòng cố định cho tiêu đề và số tiền.
- Các câu chữ đã ghim trong Playwright phụ thuộc vùng của trình duyệt thử: `web/playwright.config.ts` đặt vùng cố định.
- Kiểm thử Playwright cần tệp cấu hình cục bộ của `proxy/`; tệp đó không được mở ra đọc hay in ra, chỉ để công cụ tự dùng.

## Rollback

Hoàn lại các commit của giai đoạn chỉ dùng được khi giai đoạn 10 chưa bắt đầu; sau đó lỗi được sửa bằng commit mới. Trang web đang chạy không đổi cho tới khi Worker được triển khai lại trong một lần phát hành.

## Kết quả thực hiện

Làm ngày 21/11/2026, trong các commit `934c051` (phông do Worker phục vụ), `8984d57` (tiền và ngày theo vùng của trình duyệt), `54c69fa` (giao diện các trang theo bảng) và `2f10733` (sửa theo review).

- Chủ sản phẩm duyệt 12 bảng web ngày 21/11/2026; các bảng được lưu trong [`boards/web/`](./boards/web/README.md) cùng mã băm.
- Phông: ba tệp WOFF2 cắt từ phông của app, chỉ gồm chữ Latin và tiếng Việt, giữ hinting cùng các bản ghi bản quyền và giấy phép: Bricolage Grotesque 800 (36.328 byte), Be Vietnam Pro 400 (32.368 byte) và 600 (34.376 byte), tổng 103.072 byte, dưới trần 120 KB. Kịch bản `web/src/fonts/make-fonts.sh` tạo lại đúng từng byte; mã băm nằm trong `web/src/fonts/font-checksums.sha256`, hai tệp OFL nằm cạnh.
- Lần tải đầu của HTML, mã và kiểu dáng là 33,5 KB nén, dưới ngân sách 150 KB; `npm run size` in và kiểm cả hai con số.
- Chính sách bảo mật nội dung của ba loại trang chỉ thêm `font-src 'self'`, và kiểm thử của proxy cùng Playwright so nguyên văn. `/assets/` chỉ phục vụ các tệp `.js`, `.css` và `.woff2` có trong bản dựng; một tệp lạ trong `assets/` bị cả kịch bản kiểm bản dựng lẫn kịch bản nhúng từ chối, có kiểm thử chạy trên thư mục tạm.
- Tiền và ngày theo vùng của trình duyệt, cùng quy tắc với app: với `vi-VN` trang hóa đơn hiện "231.000 ₫" và "14 Nov 2026", với `en-US` hiện "₫231,000" và "Nov 14, 2026". Playwright chạy với vùng `en-US` cố định.
- Kiểm thử: `npm test --prefix web` (294 bài), `npm run build`, `npm run size`, `npm run typecheck` và `npm test` của proxy (509 bài của Worker, 30 bài của Node), Playwright 8/8, và WebTokensTest trong cổng Gradle.
  - Cổng kiểm thử đầy đủ của plan, gồm 14 bước với các lệnh của web và proxy, qua trên `f0d0786` và trên `60a0fba`.
- Đã sửa sau lượt [review độc lập](./reports/review-phase-09.md):
  - Danh sách các bước, danh sách gạch đầu dòng và danh sách của trang pháp lý mang `role="list"`, nên Safari lại đọc chúng là danh sách; số thứ tự của các bước lại được đọc lên.
  - Không còn khoảng trắng không ngắt thừa cạnh ký hiệu tiền ở các vùng như Mexico hay Malaysia.
  - Nhãn trạng thái của yêu cầu trả tiền có nền đặc, nên chữ giữ tương phản 4,5:1 cả khi nằm trên vòng tròn trang trí.
  - Phép kiểm kiểu dáng từ chối `image-set` và ký tự thoát, và đọc trọn địa chỉ trong ngoặc kép; tên tệp trong `assets/` phải bắt đầu bằng chữ hoặc số.
  - Token chữ chỉ dùng những độ đậm mà ba tệp phông có (400, 600, 800), có kiểm thử giữ quy tắc này.
  - `docs/proxy.md` trích ba chính sách có `font-src 'self'` và nói rằng `/assets/` phục vụ cả phông.
- Các giá trị mà giai đoạn để ngỏ, do người thực hiện chọn và đã báo chủ sản phẩm kèm ảnh:
  - Cỡ chữ theo thang chữ của app, lệch bảng tối đa 2 px. Độ đậm 500 của bảng vẽ thành 400 và 700 thành 600, vì chỉ có ba tệp phông.
  - Màu đỏ báo lỗi lấy từ token đã chốt (#B42A14 sáng, #FF8A70 tối) thay cho #C4321A của bảng.
  - Thẻ tên người tô theo quy tắc màu của app, theo tên, nên "Lan" màu hồng thay cho xanh trời của bảng.
  - Nhãn "Bill total" có độ mờ 0,9 thay cho 0,8, để đạt 4,5:1 trên vòng tròn.
  - Băng rôn của hóa đơn đã khóa chọn dùng màu `bar` và `onBar` như app; bản tối của các trạng thái không có bảng tối dựng từ cùng các vai màu.
  - Mã tiền tệ lạ viết sau số như app ("1,234.56 ZZZ").
  - Phông giữ hinting (103 KB); bỏ hinting thì còn 62,8 KB.
- Lỗi cũ sửa luôn: nút đăng nhập Google của trang xóa tài khoản không còn ở lại trên màn ở các bước sau.
- Còn mở:
  - VoiceOver trên iPhone, Safari và Firefox thật chưa được thử; ở đây chỉ có Chromium không giao diện.
  - Mã QR chưa được quét bằng app ngân hàng thật.
  - Trang web mới chỉ tới người dùng khi Worker được triển khai lại, tức ở lần phát hành đầu tiên sau khi merge.
  - `source-safety.test.ts` không đọc được CSS dưới Vitest; lỗi này có từ trước giai đoạn 9.
