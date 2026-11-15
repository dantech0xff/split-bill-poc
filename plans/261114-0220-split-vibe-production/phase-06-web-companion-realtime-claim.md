---
title: "Phase 6: Trang web cho người nhận và tự nhận món trực tiếp"
status: in-progress
phase: 6
priority: P1
effort: "10d"
dependencies: [5]
---

# Phase 6: Trang web cho người nhận và tự nhận món trực tiếp

## Overview

<!-- Updated: 15/11/2026 - chủ sản phẩm chọn backend khi bắt đầu phase -->
> **Đã chốt ngày 15/11/2026:**
> - Dữ liệu của trang web nằm trong D1 của proxy Cloudflare đang chạy. Mỗi link chia sẻ hóa đơn có một Durable Object giữ các kết nối WebSocket để đẩy cập nhật trực tiếp.
> - Trang web do chính Worker phục vụ, ở địa chỉ workers.dev của proxy như link mời và link yêu cầu của Phase 5.
> - Người nhận món trên web không cần tài khoản. Bot bị chặn bằng Cloudflare Turnstile ở lần nhận đầu tiên, cùng giới hạn 30 người mỗi link và giới hạn tần suất theo IP.
> - Link chia sẻ hóa đơn hết hạn sau 30 ngày; người tạo thu hồi được sớm hơn.

Người nhận yêu cầu mở một link và thấy phần của mình cùng cách trả, không cần cài app. Cả bàn có thể mở cùng một link và mỗi người tự chạm vào món mình ăn; mọi người thấy cập nhật trực tiếp, rồi người trả tiền chốt cách chia trong app.

Phase này cũng làm trang đầy đủ cho link yêu cầu (`/r/`) và link mời (`/g/`) mà Phase 5 đã hẹn. Người chưa cài app thấy yêu cầu cùng cách trả, hay tên nhóm mời họ vào.

Tính năng v1.0 thuộc giai đoạn này: trang web trong "thẻ chia sẻ và trang web"; mỗi người tự nhận món.

## Context

<!-- Updated: 15/11/2026 - viết lại theo mã sau Phase 5 -->
- Repo chưa có mã web nào. Proxy đã có `GET /g/` và `GET /r/` trả một trang tĩnh "mở trong app" (`proxy/src/links.ts`); token của link nằm sau dấu `#`.
- Đây là bề mặt đầu tiên mà người không có tài khoản ghi được dữ liệu, nên bảo mật là trọng tâm của giai đoạn.
- Phase 4 đã cho mỗi món một `itemId` ổn định; hóa đơn nằm trên máy (`SavedBill` trong Room).
- Phase 5 đã có yêu cầu trả tiền mang bản chụp của một cách nhận tiền. Nội dung link và mã QR do `PaymentRequestBuilder` dựng, mà hàm này chỉ có trong Kotlin.
- Dữ kiện cũ về Firebase nằm ở [báo cáo backend](../reports/researcher-261114-0209-product-backend-and-payment-links.md), mục 4.2 và 4.5; Turnstile thay App Check, và kiểm quyền trong Worker thay rules.

## Key Insights

- D1 là nguồn sự thật, như Phase 5. Durable Object chỉ giữ kết nối và phát lại bản chiếu công khai mà Worker đưa cho nó sau mỗi lần ghi. Không dữ liệu nào chỉ nằm trong Durable Object, nên mất kết nối không mất gì: trình duyệt nối lại rồi đọc lại.
- Token đi trong phần sau dấu `#` và trong thân request. Với WebSocket, token đi trong header `Sec-WebSocket-Protocol`, vì URL nằm trong trace của Cloudflare còn giá trị header thì không.
- Chốt hai bước dựa vào việc D1 ghi tuần tự:
  - một lượt nhận chỉ được ghi khi link còn `open`, trong cùng câu lệnh;
  - khóa đổi `open` thành `locking` rồi đọc mọi lượt nhận trong cùng một lô.
  
  Vì vậy lượt nhận ghi trước khóa có trong kết quả, còn lượt ghi sau thì bị từ chối.
- Token của link chia sẻ là HMAC của chủ và mã link, như link yêu cầu ở Phase 5. Chủ lấy lại được link bất cứ lúc nào, còn link của một người không bao giờ trùng link của người khác.
- Trang web không chạy được mã tính tiền của Kotlin. App của người chốt tính phần mỗi người và dựng sẵn nội dung cách trả; trang chỉ hiển thị.
- Không dùng Workers Static Assets: deploy đi qua Cloudflare API vì không có wrangler đăng nhập, mà API tải tài sản tĩnh cần header riêng. Trang web nhỏ (mục tiêu dưới 150 KB nén) nên được nhúng vào bundle của Worker lúc dựng.

## Requirements

Chức năng:
- **Xem hóa đơn:** trang `/s/#token` hiện:
  - hóa đơn: quán, ngày, các món, phụ phí và tổng;
  - ai đã nhận món gì;
  - sau khi chốt, phần của từng người cùng cách trả (link, mã QR hay thông tin để chép).
  
  Giao diện tiếng Anh như app.
- **Tự nhận món:** người mở link nhập tên rồi chạm vào các món của mình; một món có thể có nhiều người nhận. Mọi trình duyệt đang mở, và màn hình theo dõi trong app của người tạo, thấy cập nhật trực tiếp.
- **Chốt:** trong app, người tạo:
  - xem ai nhận gì;
  - ghép mỗi người nhận với một người tham gia, hoặc thêm họ thành người mới;
  - xóa người lạ;
  - sửa cách chia nếu cần, rồi chốt.
  
  Từ lúc khóa, trang không nhận thêm lượt nhận nào; sau khi chốt, trang hiện phần phải trả của từng người.
- **Thu hồi và hết hạn:** người tạo thu hồi link bất cứ lúc nào; link hết hiệu lực đúng 30 ngày sau khi tạo.
- **Link yêu cầu trên web:** `/r/#token` hiện ai đòi, đòi bao nhiêu và cho việc gì, trạng thái, cùng cách trả do app của người đòi dựng sẵn; có nút mở trong app.
- **Link mời trên web:** `/g/#token` hiện tên nhóm và cách cài app để vào nhóm.

Phi chức năng:
- Trang dùng token màu, chữ và hình khối xuất từ hệ thiết kế của app, sáng và tối theo cài đặt của máy.
- Công cụ tìm kiếm không lập chỉ mục các trang này.
- <!-- Red Team: cổng phát hành vượt yêu cầu — 2026-11-14 --> Mục tiêu (đo và báo cáo, không chặn phát hành): điểm hiệu năng và tiếp cận của Lighthouse ở chế độ di động từ 90 trở lên; gói tải ban đầu dưới 150 KB đã nén.

## Architecture

<!-- Updated: 15/11/2026 - thay Firebase bằng D1, Durable Objects và Turnstile trên proxy -->
```
web/    Vite + TypeScript + Preact; một ứng dụng cho /s/, /r/ và /g/; dựng ra web/dist
proxy/  nhúng web/dist vào bundle lúc dựng (module sinh ra, không commit) và phục vụ:
  GET /s/  /r/  /g/              trang, kèm CSP, noindex và no-referrer
  GET /assets/*                  JS và CSS, cache lâu
  Đường của chủ link (cần phiên đăng nhập):
    PUT    /v1/shares/{id}               tạo, hoặc chiếu lại hóa đơn
    GET    /v1/shares/{id}               trạng thái, lượt nhận và link
    POST   /v1/shares/{id}/lock          open thành locking, trả mọi lượt nhận
    POST   /v1/shares/{id}/reopen        locking hay locked thành open
    POST   /v1/shares/{id}/finalize      ghi phần và cách trả của từng người, thành locked
    DELETE /v1/shares/{id}/claims/{cid}  xóa một người nhận
    DELETE /v1/shares/{id}               thu hồi
  Đường công khai (token trong thân, không cần phiên):
    POST /v1/shares/open                 bản chiếu công khai
    POST /v1/shares/claim                tạo hay sửa lượt nhận của trình duyệt này; Turnstile ở lần đầu
    GET  /v1/shares/live                 WebSocket; token trong Sec-WebSocket-Protocol
    POST /v1/request-links/view          yêu cầu trả tiền, cho trang /r/
    POST /v1/invites/preview             tên nhóm, cho trang /g/
D1, migration 0002: bảng shares và share_claims; bảng requests thêm cột payment
Durable Object ShareRoom, một cho mỗi link: giữ WebSocket, phát bản chiếu sau mỗi thay đổi
```

Hợp đồng đầy đủ nằm ở [docs/proxy.md](../../docs/proxy.md), mục "Bill links and the web pages".

Bản chiếu công khai gồm:
- hóa đơn: quán, ngày, tiền tệ, các món (`itemId`, tên, số lượng, tiền), phụ phí, tổng;
- người tham gia, mỗi người có mã và tên như người tạo đã nhập;
- lượt nhận: mã người nhận, tên tự nhập và các `itemId`;
- sau khi chốt, phần và cách trả của từng người tham gia;
- `status` (`open`, `locking`, `locked`), `revision` và `expiresAt`.

Bản chiếu không có tài khoản, mã băm hay IP nào.

Người nhận món trên web:
- Ở lần nhận đầu, trình duyệt gửi token Turnstile. Proxy kiểm token với Cloudflare, tạo người nhận, và trả một khóa bí mật 128 bit mà trình duyệt giữ trong localStorage. Các lần sau chỉ cần khóa.
- Proxy kiểm:
  - link đang `open`;
  - tên dài 1 đến 40 ký tự;
  - mọi `itemId` có trong bản chiếu;
  - mỗi link tối đa 30 người nhận;
  - giới hạn theo giờ cho mỗi IP đã băm.
- Token Turnstile và khóa người nhận không mở được đường nào khác, kể cả đọc hóa đơn bằng AI.

Giữ bản chiếu khớp với hóa đơn: app gửi lại bản chiếu mỗi khi hóa đơn đổi lúc link còn mở. Proxy bỏ khỏi các lượt nhận những món không còn trong hóa đơn, và trang báo hóa đơn vừa được cập nhật.

Chốt hai bước:
1. `lock` đổi `open` thành `locking` và trả mọi lượt nhận trong cùng một lô.
2. App lần lượt:
   - ghép người nhận với người tham gia;
   - dựng cách chia theo món bằng `:core:domain` và cho người dùng sửa;
   - tính phần của từng người, và dựng nội dung cách trả bằng `PaymentRequestBuilder`;
   - ghi hóa đơn trên máy;
   - gọi `finalize`.
   
   Proxy kiểm rằng các phần cộng lại bằng tổng.

Dọn dẹp: cron hằng ngày xóa link đã hết hạn cùng mọi lượt nhận của nó. Thu hồi xóa lượt nhận ngay lập tức. Xóa tài khoản xóa mọi link của tài khoản đó.

## Files

Tạo mới:
- `web/`: `package.json`, `index.html`, `src/`, `tests/`, `e2e/`
- `web/src/tokens.css`, do một test của `core/designsystem` sinh ra và giữ cho khớp
- `proxy/migrations/0002_shares.sql`; `proxy/src/shares.ts`, `share-room.ts`, `turnstile.ts`, `web.ts`, cùng test
- `core/domain/src/main/kotlin/com/dantech/splitvibe/domain/ClaimSplit.kt`, cùng test
- `app/src/main/java/com/dantech/splitvibe/data/share/`, `app/src/main/java/com/dantech/splitvibe/ui/claim/`

Sửa:
- Proxy:
  - `proxy/src/index.ts` và `links.ts`;
  - `requests.ts` (cột `payment`) và `invites.ts` (xem trước lời mời);
  - `reminders.ts` (dọn link hết hạn) và `account-data.ts`;
  - `wrangler.jsonc` (Durable Object, migration `v3`, biến của Turnstile và địa chỉ báo lạm dụng);
  - `docs/proxy.md`.
- App:
  - `SavedBill.kt` (link chia sẻ của hóa đơn);
  - màn hình kết quả (mời cả bàn tự nhận món);
  - `RequestMoneyViewModel` (gửi nội dung cách trả cho trang `/r/`).
- `.github/workflows/ci.yml`: dựng web trước khi kiểm proxy.

## Implementation Steps

1. Hợp đồng API trong `docs/proxy.md`, rồi migration `0002_shares.sql`. Viết test của proxy cho quyền, giới hạn, hết hạn, thu hồi và khóa hai bước, rồi cài đặt.
2. `ShareRoom` và WebSocket, với test phát cập nhật.
3. Turnstile: kiểm token ở lần nhận đầu. Widget được tạo qua Cloudflare API, và secret được đặt thẳng vào Worker.
4. `web/`, gồm:
   - trang xem, tự nhận món và cập nhật trực tiếp;
   - trang đã chốt;
   - `/r/` và `/g/`;
   - các trạng thái hết hạn và thu hồi;
   - token thiết kế và tiêu đề bảo mật.
5. App, gồm:
   - `ClaimSplit`;
   - kho dữ liệu của link, với WebSocket;
   - màn hình theo dõi và chốt;
   - chiếu lại khi hóa đơn đổi;
   - nội dung cách trả cho `/r/`.
6. Dọn dẹp theo lịch và xóa tài khoản.
7. Test đầu-cuối bằng Playwright trên `wrangler dev` ở khung nhìn điện thoại; đo Lighthouse và kích thước gói.
8. Review độc lập, deploy (sao lưu D1 trước migration) và tài liệu.

## Todo

- [x] Hợp đồng API, migration và test quyền của proxy
- [x] Link chia sẻ: tạo, chiếu lại, khóa, mở lại, chốt, xóa người nhận, thu hồi
- [x] Nhận món công khai với Turnstile và giới hạn
- [x] Cập nhật trực tiếp qua Durable Object
- [x] Trang web `/s/`, `/r/`, `/g/` với token thiết kế và tiêu đề bảo mật
- [x] App: tạo link, theo dõi trực tiếp, ghép người, chốt hai bước, ghi cách chia vào hóa đơn
- [x] Chiếu lại khi hóa đơn bị sửa
- [x] Dọn dẹp theo lịch; trang link hết hiệu lực
- [x] Xóa tài khoản xóa link và lượt nhận
- [x] Test đầu-cuối; số đo Lighthouse và kích thước gói

## Kết quả thực hiện

<!-- Updated: thực hiện ngày 15/11/2026 trên nhánh feat/phase-06-web-companion-realtime-claim, chưa merge vào main -->
Chín trên mười tiêu chí đạt và có bằng chứng. Tiêu chí ba thiết bị cùng mở một link mới đạt một phần: hai trình duyệt đã được đo, còn app Android chỉ mới chạy với proxy giả, vì bản thật chưa đăng nhập được khi `GOOGLE_CLIENT_ID` còn trống.

| Tiêu chí | Kết quả | Bằng chứng |
|---|---|---|
| Không đường nào liệt kê link; đúng token thì đọc được khi link còn hạn; link hết hạn hay đã thu hồi không đọc được, kể cả khi dòng chưa bị xóa; không đường công khai nào tạo, sửa hay xóa bản chiếu | Đạt | `shares.test.ts`: chỉ chủ link đọc, sửa và thu hồi được, và không đường nào liệt kê link; link sống 30 ngày, hết hạn thì không đọc hay sửa được dù lượt dọn hằng ngày chưa xóa; link thu hồi trả 410 cho mọi người. `share-claims.test.ts`: không đường nào thiếu phiên tạo, sửa hay xóa được hóa đơn |
| `claim` từ chối tên quá dài, `itemId` lạ, người nhận thứ 31 và mọi lượt sau khi khóa; người nhận mới thiếu token Turnstile hợp lệ bị từ chối | Đạt | `share-claims.test.ts`: tên quá 40 ký tự, món không có trên hóa đơn, người thứ 31 và link đã khóa đều bị từ chối. Token Turnstile phải được Cloudflare cho qua với action `claim` trên đúng host, và chỉ dùng được một lần |
| Lượt nhận ngay trước `lock` có trong kết quả của `lock`, lượt ngay sau bị từ chối (hai việc song song) | Đạt | `share-claims.test.ts`: ba test về thứ tự, trong đó một test gửi lượt nhận và lệnh khóa cùng lúc: lượt nhận hoặc có trong câu trả lời của lệnh khóa, hoặc bị từ chối và không được lưu ở đâu |
| Hai trình duyệt và một máy Android cùng mở một link; một bên nhận món thì hai bên kia thấy trong vòng 2 giây | Một phần | Hai trình duyệt (Playwright với proxy chạy cục bộ): lượt nhận tới trình duyệt kia sau 18 đến 69 ms. App theo dõi link qua WebSocket và nhận thay đổi trong `ShareRepositoryTest` và `ClaimViewModelTest`, nhưng chỉ với proxy giả. Chưa thử máy Android thật với proxy đã triển khai |
| Sau khi chốt, phần mỗi người trên trang, trong hóa đơn trên máy và trong `SplitCalculator` bằng nhau | Đạt | `BillLinkSyncTest` dùng màn chia thật: số tiền gửi lên trang bằng phần đã lưu trên máy và bằng kết quả của `SplitCalculator`, và sau khi chốt app không gửi bản chiếu nào làm mất số tiền đó. Trang hiện đúng số tiền nhận được (test của `web/` và test đầu-cuối) |
| Chủ xóa một món khi link đang mở: lượt nhận mất món đó, lượt nhận món khác vẫn đúng | Đạt | `shares.test.ts`: món không còn trên hóa đơn bị bỏ khỏi mọi lượt nhận, các lượt nhận món khác giữ nguyên. `BillLinkSyncTest`: món bị xóa trên máy biến khỏi trang |
| Bản chiếu không có tài khoản, mã băm hay IP; khóa người nhận và token Turnstile không gọi được `/v1/bill-analysis` hay đường nào cần phiên | Đạt | `share-claims.test.ts`: bản chiếu không nêu tài khoản, không có mã băm, IP hay mã riêng của link; khóa người nhận và token Turnstile không mở được gì khác, kể cả việc đọc hóa đơn |
| Trang trả tiêu đề cấm lập chỉ mục, `Referrer-Policy: no-referrer` và CSP; link thanh toán mở thẻ mới không kèm nguồn giới thiệu, chỉ `https:` hay `upi:` | Đạt | `web.test.ts` cho cả ba trang, và bản đang chạy trả đúng các tiêu đề này. Test đầu-cuối thấy link PayPal có `noopener noreferrer`. Proxy, trang và app (`PaymentPayload`) đều chỉ nhận `https:` và `upi:` |
| Sau khi cron chạy, link hết hạn không còn lượt nhận nào | Đạt | `shares.test.ts`: lượt chạy hằng ngày xóa link hết hạn hay đã thu hồi cùng lượt nhận của chúng, và giữ các link khác |
| Báo cáo có số đo Lighthouse và kích thước gói so với mục tiêu | Đạt | Lighthouse trên di động với mạng chậm giả lập: `/s/` đang mở đạt 100 hiệu năng và 100 trợ năng, `/s/` đã chốt đạt 99 và 100, so với mục tiêu 90. Tải ban đầu 26,6 KB gzip, so với ngân sách 150 KB |

Kiểm tra chung: 364 unit test Kotlin (app 227, domain 134, hệ thiết kế 3), 260 test proxy, 182 test trang web và 5 test đầu-cuối đều qua; ktlint, lint và `tsc` sạch.

Những gì đã làm:
- **Proxy:** link chia sẻ trong D1 (`shares`, `share_claims`); các đường của chủ link: tạo, chiếu lại, khóa, mở lại, chốt kèm `paidBy`, gỡ lượt nhận, thu hồi; các đường công khai `open`, `claim` (Turnstile) và `live`; Durable Object `ShareRoom` chỉ giữ WebSocket; dọn link theo lịch; xóa tài khoản xóa cả link. Trang web được nhúng vào Worker. Hợp đồng ở [docs/proxy.md](../../docs/proxy.md).
- **Trang web:** `/s/`, `/r/` và `/g/` bằng Preact, có nhận món trực tiếp, Turnstile và mã QR; hướng dẫn ở [docs/web.md](../../docs/web.md).
- **App:** màn link của hóa đơn (tạo link, theo dõi trực tiếp, ghép người, khóa rồi chốt, chọn cách nhận tiền). Màn chia là nơi duy nhất ghi hóa đơn, và mỗi lần lưu làm trang thay đổi thì gửi bản chiếu mới. `ClaimSplit` nằm trong domain, `tokens.css` sinh từ hệ thiết kế. Kiến trúc ở [docs/architecture.md](../../docs/architecture.md).
- **Review độc lập của app:** 1 lỗi nghiêm trọng, 5 nặng, 10 trung bình và 9 nhẹ ([báo cáo](../reports/code-reviewer-261115-1200-phase-6-app-review.md)), đã sửa hết.
- **Review độc lập của proxy và trang web:** 1 lỗi nặng, 4 trung bình và 8 nhẹ ([báo cáo](../reports/code-reviewer-261115-1200-phase-6-proxy-web-review.md)), đã sửa hết ([bảng sửa](../reports/fullstack-developer-261115-1330-proxy-web-review-fixes.md)).

Triển khai lên Cloudflare ngày 15/11/2026:
- **Turnstile:** widget "Split Vibe bill links", chế độ managed, chỉ dùng được trên host của Worker. Secret `TURNSTILE_SECRET` đi thẳng từ câu trả lời tạo widget vào Worker, không in ra ở đâu; site key công khai nằm trong `wrangler.jsonc`.
- **D1 `split-vibe`:** trước khi chạy đã ghi điểm khôi phục Time Travel `00000003-00000000-00005124-4afb9bbd669e9355729c7c7c7f029dc5`. `0002_shares.sql` chạy trong một lô cùng dòng ghi vào `d1_migrations`, thêm 2 bảng, 5 chỉ mục, 1 trigger và cột `requests.payment`.
- **Worker `split-vibe-proxy`:** gói dựng bằng `npm run bundle` từ commit `ac0fe7d`. Thêm Durable Object `ShareRoom` (migration v3), `TURNSTILE_SITE_KEY` và `ABUSE_EMAIL` để trống. Các biến, secret và lịch cron 16:30 UTC đang có giữ nguyên.
- **Thử bản đang chạy:**
  - `/s/`, `/r/` và `/g/` trả trang với CSP, `no-referrer`, `noindex` và `no-store`, có site key; tệp trong `/assets/` được lưu lâu dài.
  - `open`, `claim`, `request-links/view` và WebSocket trả 404 với token lạ, và không ghi dòng nào vào D1. Đường của chủ link trả 401 khi chưa có phiên.
  - Tỷ giá, `assetlinks.json` và `sign_in_off` vẫn như trước.
  - Widget Turnstile thật hiện được trên trang thật, dưới CSP của trang: trình duyệt tự động thấy ô "Verify you are human".
- Một Worker và một KV namespace tạm dùng để chuyển gói lên đã bị xóa sau khi xong.

Các mặc định đã chọn, chủ sản phẩm có thể đổi:
- Mỗi IP được 30 người nhận mới mỗi giờ, thay vì 10, để cả bàn dùng chung Wi-Fi vẫn nhận được. Mỗi IP còn được 300 lần sửa lượt nhận và 600 lần đọc mỗi giờ.
- Trang chỉ đòi tiền khi người tạo link trả cả hóa đơn và hóa đơn không thuộc nhóm. Ngoài trường hợp đó, trang hiện phần mỗi người, và `paidBy` cho biết ai đã trả để mọi người tự thanh toán với người đó.
- Lượt nhận không có món nào không thêm người vào hóa đơn.
- Khi một lần sửa làm mất số tiền đã chốt trên trang, app báo cho người dùng chứ không tự gửi lại.
- Tên người nhận mới không được nhớ để gợi ý cho hóa đơn sau.
- Ký tự định dạng vô hình (nhóm Cf của Unicode) bị bỏ khỏi mọi tên và nhãn, ở proxy và trong bản chiếu của app.

Chưa làm, chờ chủ sản phẩm quyết:
- Giữ ZWJ và ZWNJ, vốn cần cho emoji ghép và cho tên tiếng Ba Tư hay một số chữ Ấn Độ, hay bỏ như mọi ký tự Cf khác. Giữ thì tên giả dạng dễ làm hơn.
- Trang có tự bỏ ký tự Cf khỏi tên người gõ không, để không gửi lại một tên chưa đổi mỗi lần rời ô.
- CI có chạy `npm run bundle` không, để chứng minh gói triển khai dựng được.
- Trang có cho biết lượt nhận nào đã được ghép với người nào trên hóa đơn không.

Còn lại trước khi đóng phase:
- Đặt `GOOGLE_CLIENT_ID` để đăng nhập được, rồi thử hai trình duyệt và một máy Android với proxy thật.
- Đặt `ABUSE_EMAIL` để chân trang có link báo lạm dụng.
- Kiểm tay các link thanh toán trên trang với app ngân hàng thật, cùng ma trận của Phase 5.

## Success Criteria

<!-- Updated: 15/11/2026 - đổi sang các khái niệm của Cloudflare -->
- Test của proxy qua:
  - không có đường nào liệt kê link;
  - đọc bằng đúng token thì được khi link còn hạn;
  - link đã hết hạn và link đã thu hồi đều không đọc được, kể cả khi dòng của nó chưa bị xóa;
  - không đường công khai nào tạo, sửa hay xóa được bản chiếu.
- `claim` từ chối tên quá dài, `itemId` lạ, người nhận thứ 31, và mọi lượt sau khi khóa. Người nhận mới không có token Turnstile hợp lệ cũng bị từ chối.
- Một lượt nhận gửi ngay trước `lock` có trong kết quả của `lock`; một lượt gửi ngay sau bị từ chối (test chạy hai việc song song).
- Hai trình duyệt và một máy Android mở cùng link: một bên chạm nhận món thì hai bên kia thấy trong vòng 2 giây.
- Sau khi chốt, phần mỗi người trên trang web, trong hóa đơn trên máy và trong kết quả của `SplitCalculator` trên cùng fixture bằng nhau (test đối chiếu tự động).
- Chủ xóa một món khi link đang mở: các lượt nhận món đó mất món đó, còn các lượt nhận món khác vẫn trỏ đúng món.
- Bản chiếu không chứa tài khoản, mã băm hay IP. Khóa người nhận và token Turnstile không gọi được `/v1/bill-analysis` hay đường nào cần phiên.
- Trang trả tiêu đề cấm lập chỉ mục, `Referrer-Policy: no-referrer` và Content-Security-Policy. Link thanh toán mở trong thẻ mới không kèm nguồn giới thiệu, và chỉ là `https:` hay `upi:`.
- Sau khi cron chạy, link đã hết hạn không còn lượt nhận nào.
- Báo cáo có số đo Lighthouse và kích thước gói so với mục tiêu.

## Risks

| Rủi ro | Dấu hiệu nhận biết | Phản ứng đã định |
|---|---|---|
| Link bị chuyển tiếp cho người ngoài | Xuất hiện người nhận món lạ | Người trả thấy danh sách, xóa được từng người và thu hồi link; tên người nhận được ghi rõ là tự nhập |
| Phá hoại bằng cách gửi nhiều lượt nhận | Số lượt nhận bị từ chối tăng vọt | Giới hạn 30 người mỗi link, giới hạn tần suất theo IP, Turnstile, thu hồi |
| Trình duyệt trong app chat chặn lưu trữ | Trình duyệt quên khóa người nhận sau khi tải lại | Chế độ xem không cần gì; trang mời mở bằng trình duyệt ngoài để giữ lượt nhận |
| Gói miễn phí của Cloudflare không đủ | Số request mỗi ngày hay thời gian chạy của Durable Object chạm hạn mức | Đóng kết nối khi tab ẩn; khóa link sau khi chốt; giới hạn 50 kết nối mỗi link |
| Mạo danh bằng cách nhập tên người khác | Phản hồi của người dùng | Lượt nhận không tự thành cách chia; người trả ghép và duyệt khi chốt |

## Security

<!-- Updated: 15/11/2026 - đổi sang Cloudflare -->
- Token do server sinh (HMAC, 128 bit), không đoán được; không có đường nào liệt kê token.
- Bản chiếu không chứa tài khoản, email hay số điện thoại; chỉ có tên như người dùng đã nhập, và cách trả mà người tạo đã chọn.
- Trang không dựng HTML từ dữ liệu của người dùng; mọi chuỗi đi qua cơ chế chèn văn bản an toàn của Preact. Link thanh toán chỉ là `https:` hay `upi:`, và được kiểm ở cả proxy lẫn trang.
- `Referrer-Policy: no-referrer` giữ đường dẫn có token khỏi trang thanh toán. CSP chỉ cho chạy script của chính trang và của Turnstile.
- Chân trang có link báo lạm dụng, tới địa chỉ do chủ sản phẩm đặt (`ABUSE_EMAIL`).

## Next

Phase 9 đưa trang web vào bộ kiểm thử phát hành và vào chính sách quyền riêng tư.
