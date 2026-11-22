---
title: Trang landing tại / và lần triển khai Worker ngày 22/11
date: 2026-11-22
summary: "Worker có trang chủ tĩnh giới thiệu Split Vibe, đã qua review độc lập và được triển khai qua Cloudflare API rồi thử trên bản chạy thật; đính chính rằng giao diện thiết kế lại đã lên từ 21/11."
---

# Trang landing tại / và lần triển khai Worker ngày 22/11

## Chuyện gì đã xảy ra

- Chủ sản phẩm hỏi dự án đã có web chưa. Worker chỉ phục vụ các trang mà link mở (`/s/`, `/r/`, `/g/`), các trang pháp lý và trang xóa tài khoản; `/` trả 404. Tôi nêu trang chủ thành câu hỏi rồi chờ mà không nói rõ là đang chờ, nên chủ sản phẩm phải hỏi lại.
- Trang chủ `/` là trang tĩnh, không chạy script, do `web/scripts/static-pages.mjs` viết thành `home.html`, chữ tiếng Anh lấy từ phần mô tả Google Play trong `docs/store-listing.md`. Bảy ảnh chụp app của Play, bản sáng và tối, thành 14 tệp WebP rộng 600 px (khoảng 280 KB mỗi giao diện) do `web/src/landing/make-images.py` tạo; bản build chép chúng vào `assets/` dưới tên có mã băm. Worker phục vụ `/` với chính sách của trang pháp lý, và `noindex` như mọi trang.
- Công cụ chụp kiểm của kỹ năng thiết kế giao diện không truyền `--no-sandbox`, nên Chromium dưới quyền root thoát ngay; một script bọc qua `CHROME_PATH` chạy được nó mà không sửa script của kỹ năng. Ba khổ màn hình, 0 lỗi.
- Review độc lập tìm 3 lỗi trung bình và 7 lỗi nhẹ, không có lỗi chặn. Bước kiểm trang chỉ đọc thuộc tính sau khoảng trắng, nên `<img/src=…>` hay `alt="x"src=…` lọt qua; chính sách bảo mật nội dung vẫn chặn script và nguồn từ nơi khác, nhưng ảnh cùng nguồn, ảnh `data:` và meta refresh thì không. Trang chủ giờ chỉ được có các phần tử và thuộc tính nó dùng; hai loại trang tĩnh từ chối mọi script, meta refresh và các thuộc tính tự tải khác. Câu "Private by design" nói quá: máy chủ có giữ những gì được chia sẻ qua nhóm, yêu cầu trả tiền và link hóa đơn; trang và phần mô tả cửa hàng giờ dùng nguyên câu của chính sách bảo mật (phần mô tả còn 3.399 ký tự). Câu về lượt AI thiếu chi phí một phần năm lượt cho mỗi lần sửa bằng một câu; đã thêm, và có bài kiểm thử nối cả hai câu với nguồn.
- Đính chính: Cloudflare ghi phiên bản 14 của Worker được triển khai bằng wrangler lúc 13:08 ngày 21/11, đúng phút có một lần build để triển khai trong scratchpad, với giao diện thiết kế lại. Bản tóm tắt phiên trước bỏ sót việc này, nên tôi đã nói sai với chủ sản phẩm rằng trang đang chạy vẫn là bản ngày 19/11.
- Triển khai ngày 22/11, 04:30 UTC, qua Cloudflare API vì wrangler không còn đăng nhập:
  - mốc Time Travel của D1 trước đó: `00000015-00000000-0000512b-c0db922c087f1ec655888204a453feaf`; không có migration;
  - gói dựng từ `65c4cb2`, `index.js` 2.448.144 byte, SHA-256 `8d0d1cb8ae5993a57a17cb318d09310fa1258077338570d24fab7c1be99de8df`;
  - gói đi qua một Worker tạm chỉ nhận đúng mã băm và một KV tạm, cả hai đã xóa;
  - 16 binding, 3 secret, lịch chạy hằng ngày và migration `v3` giữ nguyên; Worker khởi động trong 14 ms;
  - thử bản đang chạy: `/` trả 200 với chính sách của trang tĩnh, không script; ảnh WebP trả `image/webp` giữ một năm; `/privacy`, `/terms`, `/delete-account`, `/s/`, `/r/` trả 200; `/index.html` 404; `/v1/usage` của bản cài mới trả 3 lượt thử.
- CI trên `main` ở `65c4cb2` qua ở lần chạy thứ ba. Hai lần trước, job Android hỏng vì Robolectric không tải được tệp Android từ Maven (`IOException` trong `MavenArtifactFetcher`), làm 85 lớp bài hỏng ở `classMethod`. Job "AI proxy" có thay đổi của trang thì qua cả ba lần.
- Worker đang chạy không có secret `ANTHROPIC_API_KEY`, nên AI đọc hóa đơn chưa chạy được trên bản thật.

## Quyết định

- Trang chủ là trang tĩnh do bản build viết, không script; ảnh là WebP được commit cùng script tạo lại chúng từ ảnh tham chiếu của app.
- Bước kiểm trang tĩnh dùng danh sách cho phép; chính sách bảo mật nội dung mà Worker gửi mới là ranh giới giữ trang khỏi chạy script hay tải từ nơi khác.
- Câu chữ của trang chủ phải trùng nguồn của nó, phần mô tả cửa hàng và chính sách bảo mật; bài kiểm thử giữ chúng khớp nhau.
- Triển khai qua API đi qua Worker tạm có ghim mã băm, và giữ biến cùng secret bằng `keep_bindings`.

## Bước tiếp

- Chủ sản phẩm đặt `ANTHROPIC_API_KEY` cho Worker trước khi mở app.
- Khi app lên Google Play: đổi `GET_THE_APP` thành link tới trang của app, và quyết định bỏ `noindex` cho `/`.
- Ảnh trên trang chủ có tên quán mẫu "Quán Ốc Ba Miền", mà chủ sản phẩm đã quyết giữ; trang giờ công khai.

> Historical work record — not durable authority. Prefer docs/specs/ADRs for current decisions.
