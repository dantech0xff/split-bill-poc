---
title: "Cập nhật Dependabot: Kotlin 2.4, OkHttp 5, Gradle 9.9.1 và các gói npm"
date: 2026-11-22
summary: "Mười lăm PR Dependabot được áp theo nhóm sau cổng kiểm thử, @types/node 26 bị bỏ qua có lý do; hai cuộc đua trong bài kiểm thử được sửa; dependency graph đã bật, và 64 cảnh báo của nó chỉ nằm ở công cụ build."
---

# Cập nhật Dependabot: Kotlin 2.4, OkHttp 5, Gradle 9.9.1 và các gói npm

## Chuyện gì đã xảy ra

- Theo lời chủ sản phẩm "xử lý mấy PR Dependabot luôn đi", các PR được xử lý từng nhóm, mỗi nhóm qua cổng kiểm thử trên máy này và CI trước khi tua nhanh `main`.
- Nhóm npm bản vá nhỏ (#11, #15, #16, #17, #18, #19) và TypeScript 7 (#7, #13) được áp. Tệp khóa của wrangler và TypeScript ở proxy được tạo lại bằng `npm install --save-exact`, vì bản Dependabot va với nhau.
- @types/node 26 (#10) không áp: CI và `engines` dùng Node 22, nên kiểu của một Node mới hơn sẽ cho dùng những API mà Node 22 không có. `.github/dependabot.yml` giờ bỏ qua bản major của gói này ở `web`.
- OkHttp 5.5.1 (#9) được áp cùng checksum mới. Lượt tính checksum chạy lại để lộ một cuộc đua thật trong `GroupViewModelTest`: luồng của MockWebServer thêm vào danh sách trong lúc bài đọc nó. Danh sách giờ là `CopyOnWriteArrayList`.
- Kotlin 2.4.21 (#6), kotlinx-serialization-json 1.13.0 (#8) và roborazzi 1.79.0 (#14) được áp chung trên một nhánh, vì serialization 1.13 dựng trên Kotlin 2.4.10 và cả ba cùng cần checksum mới. Danh sách checksum là phần hợp của ba lượt tính: thêm 59 thành phần, không bỏ dòng nào, cộng `also-trust` cho marker của hai plugin Kotlin.
- Lượt tính checksum trên `main` sau OkHttp hỏng ở bài màn nhận phần mới thêm hôm 21/11. Bài đó cho rằng câu trả lời của cơ sở dữ liệu chỉ đến khi looper chính chạy. Thăm dò cho thấy câu trả lời đến ngay trong 18 trên 3.000 lần, và 200 trên 200 lần khi luồng chính bị chậm 20 ms sau khi gửi việc cho cơ sở dữ liệu. `TestStorage` giờ nhận luồng riêng cho cơ sở dữ liệu (`DatabaseThreads`), và bài giữ chúng lại trong lúc màn hình khởi động. Khi được giữ, không lần nào câu trả lời đến sớm (0 trên 200), và bài vẫn bắt được lỗi cũ của màn hình.
- Hai PR proxy mới (#20 vitest 5.1.1, #21 @anthropic-ai/sdk 1.10.0) qua đủ năm bước proxy và web: 509 bài proxy, 294 bài web, kiểm tra kiểu, audit không lỗ hổng. Proxy chỉ dùng `beta.messages.stream` với `output_config` dạng `json_schema` và các lớp lỗi của SDK, mà hai bản 1.9 và 1.10 không đổi. Dependabot tự đóng hai PR sau khi `main` có chúng.
- Gradle 9.9.1 (#4) làm riêng: với Kotlin 2.4.21, Kotlin Gradle plugin lấy các jar biến thể `-gradle96` nên cần checksum mới. Lời ghi trong một commit nháp rằng wrapper 9.9.1 "không cần checksum mới" là sai, nên wrapper được rút khỏi nhóm Kotlin. Lượt `checksums.yml` đầu tiên của nhánh hỏng vì Robolectric không tải được tệp Android, nhưng `--continue` vẫn ghi đủ danh sách: năm jar `-gradle96` cộng `kotlin-reflect` 2.4.21, khớp đúng bảy tệp mà bài trên máy ảo báo thiếu, và lược đồ của tệp lên bản 1.4. Sau cổng 9 bước và bài trên máy ảo trên máy sạch, Gradle 9.9.1 vào `main` ở `49fe098`. Lượt tính checksum trên `main` mất kết nối máy chạy giữa chừng; lần chạy lại cho ra danh sách trùng từng byte với bản đã commit.
- README ghi lại phiên bản của bộ công cụ sau các bản cập nhật (Gradle, Kotlin, OkHttp, TypeScript, wrangler, SDK, jose, vitest); dòng đó đã cũ từ nhóm Kotlin.
- Chủ sản phẩm bật dependency graph, cùng cập nhật bảo mật của Dependabot. Lượt "Dependency graph" của repo trước đó luôn hỏng vì cài đặt này tắt. Ngay sau khi bật, GitHub mở 64 cảnh báo trên 17 gói Maven, đều là thư viện gián tiếp. Không gói nào có trong `prodReleaseRuntimeClasspath`: chúng đến từ classpath của plugin build (Bouncy Castle, guava, jdom2, jose4j, httpclient, commons-lang3), nền tảng chạy bài trên máy ảo của Android (netty, protobuf) và bài Robolectric. Dependabot không mở được PR cho chúng, nên hai lượt cập nhật bảo mật báo `dependency_not_found`.
- Phiên làm việc không đăng được bình luận lên PR (bộ phân loại chặn) và không đổi được cài đặt bảo mật qua kết nối GitHub của nó, nên bốn PR Gradle đã áp chờ lượt quét hằng tuần của Dependabot để tự đóng.

## Quyết định

- Bản cập nhật nào cần checksum thì lấy danh sách từ lượt `checksums.yml` trên máy sạch, chỉ nhận phần khác mà bản cập nhật giải thích được, và gộp theo từng khối bằng script chứ không ghép văn bản.
- Lỗi mạng của máy GitHub (không tìm thấy plugin, Robolectric tải hỏng) thì chạy lại. Một bài hỏng thì tìm nguyên nhân, như hai cuộc đua trong bài kiểm thử ở trên.
- Một bài kiểm thử nhìn trạng thái "trước khi dữ liệu đến" phải tự giữ dữ liệu lại, không dựa vào thời điểm của các luồng.

## Bước tiếp

- Chủ sản phẩm quyết định với 64 cảnh báo của công cụ build: để mở chờ bản cập nhật của plugin Android và Robolectric, hay đóng với lý do mã đó không có trong app.
- Lượt "Dependency graph" của repo và lượt gửi tự động của GitHub giờ cùng gửi một bản build Gradle; chủ sản phẩm chọn giữ một.
- Các PR Gradle đã áp (#4, #6, #8, #9, #14) chờ lượt quét hằng tuần của Dependabot để tự đóng, hoặc chủ sản phẩm đóng tay.
- Các nhánh làm việc cũ vẫn còn trên GitHub, vì phiên làm việc không có quyền xóa nhánh.

> Historical work record — not durable authority. Prefer docs/specs/ADRs for current decisions.
