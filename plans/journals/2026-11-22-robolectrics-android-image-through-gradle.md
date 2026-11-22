---
title: "Robolectric's Android image through Gradle"
date: 2026-11-22
summary: "CI's unit tests no longer depend on Robolectric downloading its 151 MB Android image from Maven Central: Gradle takes it from Google's copy of Maven Central, checks and caches it, and Robolectric runs offline."
---

# Robolectric's Android image through Gradle

## Chuyện gì đã xảy ra

- CI trên `main` ở `65c4cb2` hỏng hai lần liền ở bước "Unit tests and screenshots": 139 bài chạy, 85 hỏng, đều ở `classMethod` với `AssertionError at MavenArtifactFetcher.java:130`. Báo cáo của lần chạy hỏng (artifact `reports`) chỉ ra nguyên nhân: 510 lần `Server returned HTTP response code: 429 for URL: https://repo1.maven.org/maven2/org/robolectric/android-all-instrumented/14-robolectric-10818077-i7/…jar`.
- Robolectric tự tải ảnh Android 14 (151 MB) vào `~/.m2` lúc bài chạy, bằng `MavenDependencyResolver` của nó, chỉ một lần thử. CI chỉ giữ bộ đệm của Gradle, không giữ `~/.m2`, nên lần chạy nào cũng tải lại từ Maven Central, và một câu 429 làm hỏng mọi lớp bài Robolectric. Mã của Robolectric 4.17 (`LegacyDependencyResolver.pickOne`) chọn trình tải đó vì bản build không đặt `robolectric.dependency.dir` hay `robolectric.offline`.
- Gradle 9.9.1 coi 408 và 429 là lỗi tạm (`NetworkingIssueVerifier`) và thử tới 3 lần, bắt đầu cách 1 giây (`NetworkOperationBackOffAndRetry`). `setup-gradle` giữ những gì Gradle tải trong bộ đệm của CI.
- Ảnh Android giờ là thư viện của cấu hình `robolectricAndroid` (`robolectric-android` trong danh mục phiên bản). Task `robolectricImages` chép nó vào thư mục build của app. Các bài unit test đặt `robolectric.offline=true` và `robolectric.dependency.dir` là thư mục đó, và nhận ảnh làm đầu vào. Robolectric không còn tự tải gì.
- Cú pháp `val x by configurations.creating` bị Gradle 9.6 đánh dấu bỏ (hết hẳn ở Gradle 10), nên cấu hình mới dùng `configurations.create(...)`.
- Checksum: jar và POM mà Gradle tải trên máy này (qua bản sao Maven Central của Google) trùng SHA-512 Maven Central công bố. Lượt `checksums.yml` trên nhánh cho danh sách trùng từng byte với tệp đã commit, trừ dòng của jar mà nó không tải được vì 429. Lần chạy CI thứ hai trên `main` tải jar từ `repo.maven.apache.org` và qua bước kiểm checksum nghiêm ngặt, tức checksum của jar khớp bản trên Maven Central.
- Lần chạy cổng kiểm thử đầu tiên trên máy này hỏng 473 trên 530 bài vì đĩa đầy (`No space left on device`), không phải vì bản sửa. Bộ đệm của Gradle 9.4.1, bản dự án dùng trước khi lên 9.9.1, chiếm 9,5 GB. Xóa nó, chạy lại từ bản build sạch, thì cả chín bước qua.
- Để biết Robolectric còn tự tải hay không, bản mà Robolectric từng tải vào `~/.m2` được dời đi trước khi chạy cổng. Sau cổng, `~/.m2` không có bản mới, và log không có dòng `MavenArtifactFetcher` hay `repo1` nào: Robolectric đọc ảnh mà Gradle chép ra.
- Chủ sản phẩm duyệt việc gộp, và `main` được tua nhanh lên `4cf00af`. Trên `main`:
  - CI lần 1 hỏng sau 33 giây, vì Gradle gặp 429 cả ba lần khi tải jar (bộ đệm chưa có ảnh). Lần 2 qua, bài unit test chạy 3 phút 14 giây. Bộ đệm chưa lưu ảnh: lần 1 đã lưu Gradle home không có ảnh dưới cùng khóa của commit này, nên lần 2 báo "Entry not saved: cache key not changed".
  - Bài trên máy ảo qua. Job ghi hồ sơ khởi động hỏng lần đầu vì không tìm thấy plugin Kotlin Compose (Maven Central giới hạn), và qua khi chạy lại.
  - `checksums.yml` gặp 429 với jar, nhưng danh sách nó ghi trùng từng byte với tệp đã commit: Gradle giữ những dòng đã có cho tệp nó không tải được.
- Lượt `checksums.yml` đầu tiên trên nhánh chạy 54 phút rồi mất máy chạy ("The hosted runner lost communication with the server"), như đã xảy ra trên `main` trước bản sửa.
- CI của commit journal (`24fbcec`) hỏng vì Gradle báo không tìm thấy jar trên `repo.maven.apache.org` (`Could not find …jar`), và lần chạy lại hỏng y hệt sau 30 giây. Lần chạy lại lấy lại bộ đệm mà lần hỏng vừa lưu dưới cùng khóa. Không rõ trong bộ đệm đó Gradle đã ghi tệp là không tìm thấy, hay Maven Central lại trả không tìm thấy.
- Từ 04:28 đến 08:45 UTC, sáu trong chín lần máy GitHub tải tệp này mà kết quả được biết rõ đã hỏng (năm lần 429, một lần không tìm thấy), dù người tải là Robolectric hay Gradle. Một tệp lớn khác của Robolectric, `nativeruntime-dist-compat` 1.0.19, cũng gặp 429 trên máy sạch.
- Chủ sản phẩm chọn lấy riêng tệp này từ bản sao Maven Central của Google: khối `exclusiveContent` trong `settings.gradle.kts`, chỉ cho `org.robolectric:android-all-instrumented` (commit `013edd1`). Bản sao trả jar 151.142.227 byte, cùng cỡ với tệp trên Maven Central.
  - Trên nhánh: cổng chín bước qua, bài trên máy ảo qua, và lượt `checksums.yml` đầu tiên qua trọn vẹn trong ngày. Máy sạch tải jar từ bản sao của Google, và danh sách nó ghi trùng từng byte với tệp đã commit, nên checksum của jar cũng được máy sạch xác nhận.
  - Trên `main` ở `013edd1`, mọi lượt chạy qua ngay lần đầu: CI, bài trên máy ảo, `checksums.yml` (danh sách lại trùng từng byte), dependency graph và security. CI lấy lại bộ đệm của `24fbcec` mà vẫn qua, vì Gradle không còn hỏi Maven Central về tệp này. `setup-gradle` lưu Gradle home mới và một gói thư viện mới (`gradle-dependencies-v2-48be9aa…`) có ảnh, nên các lần chạy sau lấy ảnh từ bộ đệm.

## Quyết định

- Ảnh Android của bài Robolectric đi qua Gradle như mọi thư viện: có checksum, có thử lại, có bộ đệm. Robolectric chạy offline.
- Ảnh đến từ bản sao Maven Central của Google và chỉ từ đó; mọi thư viện khác vẫn từ Maven Central. Checksum không đổi, nên tệp khác đi một byte là bản build dừng. Lý do: Maven Central trả 429 hay không tìm thấy cho phần lớn các lần máy GitHub tải tệp 151 MB này, và một lần tải hỏng lưu Gradle home không có ảnh dưới khóa của commit, nên chạy lại không làm đầy bộ đệm.
- Phiên bản của ảnh theo bản Robolectric và SDK của bài (`sdk=34`), không theo bản mới nhất, nên Dependabot bỏ qua `org.robolectric:android-all-instrumented`. Một bản Robolectric mới đổi ảnh thì bài hỏng với `Path is not a file:`, kèm tên tệp nó cần; khi đó đổi `robolectricAndroid` và lấy checksum mới.

## Bước tiếp

- CI chỉ tải lại ảnh khi phiên bản đổi hoặc bộ đệm hết hạn (bảy ngày không có lần chạy nào), và khi đó tải từ bản sao của Google.
- Sau một bản Robolectric dùng ảnh mới, bản sao của Google cần có tệp mới thì Gradle mới tải được; nếu nó chưa có, Gradle báo không tìm thấy.
- Gradle 10 sẽ bỏ hẳn `val ktlint by configurations.creating` ở `build.gradle.kts` gốc; cần đổi trước khi lên Gradle 10. Hai cảnh báo còn lại (`Configuration.setVisible`, `Project` làm ký hiệu thư viện) đến từ Android Gradle Plugin.

> Historical work record — not durable authority. Prefer docs/specs/ADRs for current decisions.
