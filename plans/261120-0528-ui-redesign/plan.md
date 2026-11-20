---
title: "Split Vibe: đưa giao diện mới vào app và trang web"
description: "Mười giai đoạn đưa bản thiết kế lại đã duyệt vào app Android và các trang web: bảng màu, phông chữ, thành phần, thanh điều hướng dưới, tiền và ngày theo vùng của máy, từng nhóm màn hình, kiểm thử, trang web và tài liệu."
status: in-progress
priority: P1
effort: "36d"
branch: feat/ui-redesign
tags: [feature, frontend, refactor]
blockedBy: []
blocks: []
created: 2026-11-20
---

# Split Vibe: đưa giao diện mới vào app và trang web

## Overview

Mười giai đoạn đưa bản thiết kế lại đã duyệt vào app Android và các trang web cho người nhận liên kết. App giữ nguyên mọi tính năng và dữ liệu. Thứ thay đổi là diện mạo, bố cục, điều hướng, cách viết số tiền và ngày, và vài chỗ chữ mà chủ sản phẩm đã chốt. Bối cảnh và câu trả lời ban đầu của chủ sản phẩm nằm trong [bản ghi thiết kế lại](../reports/brainstorm-261120-0148-ui-redesign.md).

Ước lượng tổng công là 36 ngày làm việc cho một người làm cùng trợ lý AI, sai số khoảng 40%. Con số này chỉ để so sánh các giai đoạn, không phải cam kết ngày phát hành.

## Nguồn thiết kế

- App: 37 bảng đã duyệt (32 sáng, 5 tối) nằm trong [`boards/`](./boards/README.md), kèm danh sách mã băm. Người thực hiện và người review so với các tệp này, không so với canvas đang sống. Mỗi bảng là một tệp HTML rộng 390 px với kiểu dáng viết ngay trên phần tử; 1 px trên bảng ứng với 1 dp trong app.
- Trang web: các bảng web nằm trên trang "Trang web" của cùng canvas và đang chờ chủ sản phẩm duyệt. Khi được duyệt, chúng được lưu vào `boards/web/` trong cùng commit cập nhật giai đoạn 9. Giai đoạn 9 không bắt đầu trước lúc đó.
- Sửa trên canvas sau ngày lưu là một yêu cầu thay đổi: chủ sản phẩm duyệt, rồi bảng, mã băm và phần plan bị ảnh hưởng được cập nhật cùng nhau.
- Khi bảng và app khác nhau về chữ, chuỗi trong `app/src/main/res/values/strings.xml` thắng, trừ các nhãn mới và các chuỗi đổi chữ mà từng giai đoạn liệt kê.
- Bảng vẽ số tiền và ngày theo kiểu của máy đặt vùng Mỹ ("₫902,664", "Nov 13"). Ảnh tham chiếu cũng được ghi trên máy đặt vùng Mỹ, nên so được với bảng.
- Bốn bảng `Pro`, `ProMember`, `Settings` và `SettingsStates` còn vẽ ngày dài theo kiểu ngày đứng trước ("1 December", "17 December 2026"), vì app hôm nay viết cứng như vậy. Quy tắc của giai đoạn 4 thắng: máy đặt vùng Mỹ sẽ hiện "December 1" và "December 17, 2026".
- Các trạng thái chưa có bảng riêng dựng từ thành phần của bảng gần nhất và được chủ sản phẩm duyệt qua ảnh thật: trang chủ khi chưa đăng nhập, trang chủ khi chưa tải xong yêu cầu, và mọi màn ở cỡ chữ 200%.

## Quyết định của chủ sản phẩm (20/11/2026)

1. Hướng thẩm mỹ: trẻ, vui, đậm màu; khối tím điện, màu nhấn xanh chanh, chữ màu mực trên nền trung tính.
2. Làm lại cả điều hướng: thanh dưới với Home, Groups, Requests, Insights và nút "Split a bill" ở giữa.
3. Bỏ phần "Show what was sent to Claude" trên màn kết quả. Cái giá đã được nêu và chấp nhận: người dùng không còn xem được trong app đoạn chữ đã gửi đi; chính sách quyền riêng tư vẫn mô tả đầy đủ những gì được gửi.
4. Nhãn "Photo stays on this phone" chỉ từng có trên bảng canvas và đã được bỏ ở đó; app không có nhãn này. Câu "The photo stays on this phone." trong chuỗi xin đồng ý `ai_consent_bill_read` giữ nguyên.
5. App không được gợi ý rằng việc đọc hóa đơn do một model OCR ở máy chủ làm. Mọi chuỗi `ai_consent_*`, chuỗi `settings_ai_consent_given` và chính sách quyền riêng tư giữ nguyên từng chữ và tiếp tục nêu rõ Claude và Anthropic.
6. Giọng sản phẩm thay cho tên Claude ở mọi chỗ khác trong app:

   | Chuỗi hôm nay | Chữ mới |
   |---|---|
   | `split_asking_claude` "Claude is working out the bill…" | "Reading your bill…" |
   | (nhãn mới) tiêu đề thẻ tóm tắt trên màn kết quả | "What Split Vibe understood" |
   | `home_intro_text` "… worked out by Claude, …" | "… worked out by AI, …" |
   | `assistant_ask_claude` "Ask Claude" | "Ask the AI" |
   | `assistant_working` "Claude is working out the changes…" | "Working out the changes…" |
   | `assistant_nothing_to_change` "Claude found nothing to change." | "The AI found nothing to change." |
   | `assistant_answer_hint` "… and Claude works the change out again." | "… and the AI works the change out again." |
   | `assistant_unreadable` "%d more change(s) Claude proposed …" | "%d more change(s) the AI proposed …" |

   Tên khóa có chữ `claude` được đổi theo. Hai chuỗi `split_show_sent` và `split_hide_sent` bị xóa cùng tính năng.
7. Số tiền và ngày viết theo vùng của điện thoại. Trên máy đặt tiếng Việt, ngày ngắn là "13 Nov": thứ tự ngày và tháng theo vùng, tên tháng bằng tiếng Anh cho khớp chữ của app. Quy tắc đầy đủ nằm ở giai đoạn 4.
8. Trang web được thiết kế lại trong plan này: vẽ bảng web trước, chủ sản phẩm duyệt, rồi giai đoạn 9 thực hiện.
9. 37 bảng đã duyệt được lưu trong repo công khai này.
10. Cả 15 điểm phản biện được áp dụng; xem mục Red Team Review.

Mặc định do người lập plan đặt, chủ sản phẩm đổi được:

- Điều hướng dùng một ngăn xếp duy nhất như hôm nay. Thanh dưới chỉ hiện ở bốn màn gốc; bấm một tab thay màn gốc đang đứng, và lùi từ một màn gốc khác Home đưa về Home. Vì thanh không hiện ở màn con, không có lịch sử riêng của tab nào để giữ, và vị trí cuộn của một màn gốc không được nhớ khi sang tab khác.
- Trang chủ và số đếm trên tab Requests cần danh sách yêu cầu trả tiền mới. App tải lại danh sách đó khi mở và ngay sau khi đăng nhập, tức thêm một lời gọi tới máy chủ cho mỗi lần mở app khi đã đăng nhập.
- Trang chủ hiện "See all" khi máy có hóa đơn hoặc có hóa đơn định kỳ, để màn Bills và thẻ Recurring luôn mở được sau khi nút "Recurring bills" rời trang chủ.
- Bảng dạng ngày ở giai đoạn 4 do người lập plan điền từ nguyên tắc mà chủ sản phẩm đã chốt (thứ tự theo vùng, tên tháng tiếng Anh, dạng ngắn "13 Nov"). Phần người lập plan thêm gồm các dạng có năm, các dạng dài, các dạng của vùng viết năm trước, và việc ngày ngắn mang thêm năm khi ngày không thuộc năm hiện tại.
- Dòng "Amount: …" trong chi tiết cách trả tiền do máy của người gửi soạn, nên mang kiểu số của vùng người gửi, kể cả khi hiện trên trang web của người nhận.
- Các trạng thái của trang chủ và màn nhóm chưa có bảng (chỉ mình đang nợ, số dư bằng 0, số dư chưa rõ) theo mặc định ghi ở giai đoạn 5 và 6.
- Đóng gói bảy tệp phông tĩnh và hai tệp giấy phép. Không thêm màn hay hàng "Font licences": giấy phép OFL chỉ đòi văn bản giấy phép đi kèm phông.
- Viền của thanh nổi ở giao diện tối giữ màu `#3A3A42` của bảng đã duyệt. Nó là nét trang trí, không phải thứ duy nhất giúp nhận ra thanh, nên không áp ngưỡng 3:1.
- Trang web viết tiền và ngày theo cùng quy tắc với app, lấy vùng từ trình duyệt, vì `web/src/money.ts` và `web/src/dates.ts` vốn được viết để khớp với app.
- Trang web dùng nhiều nhất ba tệp phông do chính Worker phục vụ, với trần riêng 120 KB nén. Ngân sách 150 KB của HTML, mã và kiểu dáng giữ nguyên. Nếu phông vượt trần, giai đoạn 9 dừng ở bước đo để chủ sản phẩm chọn.
- Ảnh cho Google Play giữ cỡ 824 px như `docs/store-listing.md` đang ghi. Ảnh 1080 px vẫn là việc chủ sản phẩm quyết sau.
- Bản tóm tắt PDF không được thiết kế lại; số tiền và ngày trong đó đổi theo quy tắc của giai đoạn 4 vì dùng chung hàm.

## Quy tắc chung cho mọi giai đoạn

### Cổng kiểm thử

`ci.yml` chỉ chạy cho pull request và cho `main`, và plan này không mở pull request khi chủ sản phẩm chưa yêu cầu. Vì vậy cuối mỗi giai đoạn, người thực hiện chạy tại chỗ đúng các lệnh của `ci.yml`:

```bash
./gradlew ktlintCheck
./gradlew :core:domain:test :core:designsystem:testDebugUnitTest :app:testProdDebugUnitTest -Pscreenshots=compare
./gradlew :app:lintProdDebug :core:designsystem:lintDebug
./gradlew :app:assembleProdDebug :app:assembleProdRelease :app:bundleProdRelease
./gradlew :app:assembleDevDebug :app:assembleProdDebugAndroidTest :baseline-profile:assembleProdNonMinifiedRelease
python3 scripts/release-checks.test.py
python3 scripts/worker-settings.test.py
python3 scripts/release-checks.py app/build/outputs/apk/prod/release/app-prod-release-unsigned.apk
```

Kèm theo là bước "No Anthropic code in the packages" của `ci.yml`, và các lệnh của việc `proxy` trong `ci.yml` khi giai đoạn đụng tới `web/` hoặc `proxy/`.

- Từ giai đoạn 1 đến 7, kiểm thử chạy với `-Pscreenshots=compare`. Đã kiểm ngày 20/11/2026 trên Roborazzi 1.76.0 với `ComponentCatalogTest`, một ảnh tham chiếu bị đổi và một ảnh bị xóa: bài kiểm thử vẫn chạy trọn và qua, thư mục `app/src/test/screenshots/` không bị ghi gì, và ảnh mới cùng ảnh so nằm ở `app/build/outputs/roborazzi/<tên>_actual.png` và `<tên>_compare.png`. Ảnh gửi chủ sản phẩm xem lấy từ thư mục đó.
- Không commit gì dưới `app/src/test/screenshots/` trước giai đoạn 8. Cuối mỗi giai đoạn, `git status --porcelain app/src/test/screenshots` phải rỗng.
- Từ giai đoạn 8 trở đi, kiểm thử chạy ở chế độ so mặc định như CI.
- `device-tests.yml` tự chạy khi nhánh được đẩy lên với thay đổi của app. Cuối mỗi giai đoạn có đổi màn hình, kết quả của lượt chạy trên commit cuối được đọc và ghi lại.
- Mỗi giai đoạn kết thúc bằng: cổng trên qua, một lượt review độc lập, sửa các phát hiện, và ảnh màn hình thật gửi chủ sản phẩm so với bảng. Quy tắc này áp cho cả mười giai đoạn, kể cả khi tệp giai đoạn không nhắc lại từng việc. Mọi phép thử tay mà một giai đoạn nêu được làm và ghi kết quả vào báo cáo cuối giai đoạn đó.
- Giai đoạn nào cần ảnh của một trạng thái để chủ sản phẩm duyệt thì thêm bài chụp cho trạng thái đó ngay trong giai đoạn. Giai đoạn 8 ghi lại những gì đã có và chỉ thêm cái còn thiếu.
- Giai đoạn 9 chạy các lệnh của web và proxy, và cả cổng Gradle vì nó sửa `WebTokensTest.kt`.

### Thứ tự và tệp dùng chung

Các giai đoạn chạy lần lượt từ 1 đến 10. Giai đoạn 9 (web) không đụng mã của app, nên có thể chạy song song với 5 đến 8 nếu chủ sản phẩm muốn rút ngắn; mặc định vẫn là lần lượt.

Vì chạy lần lượt, các tệp nhiều giai đoạn cùng sửa không có tranh chấp. Chúng vẫn được nêu tên để không ai bỏ sót: `app/src/main/res/values/strings.xml`, `ui/AppNavHost.kt`, và các tệp kiểm thử `ui/ScreenGalleryTest.kt`, `ui/PseudoLocaleGalleryTest.kt`, `text/NumbersInTextsTest.kt`, `e2e/JourneySteps.kt`, `e2e/PaymentRequestJourneyTest.kt`. Giai đoạn nào đổi chữ ký của một màn thì sửa luôn mọi nơi gọi màn đó, kể cả trong các tệp này. Hàng `BillListingRow` trong `ui/history/HistoryScreen.kt` vẽ cả danh sách hóa đơn gần đây của trang chủ lẫn màn Bills: giai đoạn 5 đổi hình dáng hàng theo bảng `Main`, giai đoạn 7 dùng lại hàng đó. Các giai đoạn nhắc tới chỗ trong `AppNavHost.kt` bằng tên mục (`entry<HomeKey>`), không bằng số dòng, vì giai đoạn 3 viết lại tệp này.

### Hoàn lại

Hoàn lại commit chỉ dùng được khi giai đoạn kế tiếp chưa bắt đầu. Sau đó, lỗi của một giai đoạn được sửa bằng commit mới, vì các giai đoạn sau đã dựng trên nó.

## Goals

| # | Goal | Priority |
|---|------|----------|
| 1 | Mọi màn hình của app dùng token, thành phần và bố cục của bản thiết kế, ở cả giao diện sáng và tối | P1 |
| 2 | Điều hướng mới hoạt động đúng với nút lùi, vuốt lùi có xem trước, liên kết mở app và khôi phục trạng thái | P1 |
| 3 | Số tiền và ngày theo vùng của máy ở mọi nơi app viết chúng | P1 |
| 4 | Các trang web theo bản thiết kế web đã duyệt, trong giới hạn bảo mật và dung lượng hiện có | P1 |
| 5 | Không mất tính năng, dữ liệu hay tiêu chuẩn trợ năng nào đang có | P1 |
| 6 | Kiểm thử, ảnh tham chiếu, hồ sơ khởi động, tài liệu và ảnh cửa hàng khớp với giao diện mới | P1 |

## Phases

| # | Phase | Effort | Status |
|---|-------|--------|--------|
| 1 | [Nền tảng: màu, chữ, hình khối](./phase-01-colors-type-shapes.md) | 3d | Completed |
| 2 | [Thành phần dùng chung](./phase-02-shared-components.md) | 4d | Completed |
| 3 | [Điều hướng với thanh dưới](./phase-03-navigation-bottom-bar.md) | 3d | In progress |
| 4 | [Tiền và ngày theo vùng của máy](./phase-04-money-and-date-formats.md) | 3d | Pending |
| 5 | [Trang chủ và luồng chia hóa đơn](./phase-05-home-and-split-flow.md) | 6d | Pending |
| 6 | [Nhóm, yêu cầu trả tiền và liên kết hóa đơn](./phase-06-groups-requests-bill-links.md) | 4d | Pending |
| 7 | [Hóa đơn, thống kê, cài đặt và Pro](./phase-07-bills-insights-settings-pro.md) | 3d | Pending |
| 8 | [Kiểm thử, ảnh tham chiếu và hồ sơ khởi động](./phase-08-tests-screenshots-profile.md) | 3d | Pending |
| 9 | [Trang web](./phase-09-web-pages.md) | 5d | Pending, chờ duyệt bảng web |
| 10 | [Tài liệu, ảnh cửa hàng và hoàn tất](./phase-10-docs-store-finish.md) | 2d | Pending |

## Ngoài phạm vi

- Tính năng mới, giá Pro, hạn mức AI và bản dịch tiếng Việt.
- API, cơ sở dữ liệu và văn bản thông báo đẩy của máy chủ. Riêng phần Worker phục vụ trang web (`proxy/src/web.ts` và kịch bản nhúng) được sửa trong giai đoạn 9 khi trang web cần.
- Bố cục của bản tóm tắt PDF.
- Ẩn số tiền của trang chủ trong màn đa nhiệm của Android. Trang chủ hôm nay đã hiện hóa đơn, số tiền và tên người, nên bản thiết kế không thêm loại thông tin mới.
- Phát hành lên Google Play; việc này vẫn theo `docs/release-checklist.md`.

## Success Criteria

- [ ] 37 bảng của app đều có màn hình tương ứng, và chủ sản phẩm duyệt ảnh chụp màn hình thật của từng nhóm.
- [ ] Mọi lệnh của `ci.yml` qua khi chạy tại chỗ ở cuối từng giai đoạn, và CI trên `main` xanh sau khi merge.
- [ ] Kiểm thử trên máy ảo qua ở API 34 cùng kiểm tra trợ năng, và kết quả của các hành trình ở API 26, 30 và 36 được đọc từng việc một.
- [ ] Ở cỡ chữ 200%, không chữ nào bị cắt trên trang chủ, màn thêm hóa đơn, màn kết quả và màn đòi tiền.
- [ ] Trên máy đặt vùng Việt Nam, số tiền hiện kiểu "902.664 ₫" và ngày kiểu "13 Nov"; trên máy đặt vùng Mỹ, "₫902,664" và "Nov 13".
- [ ] Ngoài màn xin đồng ý và mục đồng ý trong Cài đặt, app không còn chuỗi nào gọi tên Claude; các chuỗi `ai_consent_*` không đổi một chữ.
- [ ] Ảnh tham chiếu được ghi lại một lần, không còn ảnh nào mà không bài kiểm thử nào ghi.
- [ ] Các trang web khớp với bảng web đã duyệt, qua kiểm thử của web, và lần tải đầu không quá 150 KB nén.
- [ ] Tài liệu hệ thiết kế, kiến trúc, bảng ảnh cho Google Play và ảnh trong README mô tả giao diện mới.

## Tài liệu của plan

- Rà code: [hệ thiết kế và điều hướng](./reports/scout-design-system-navigation.md), [màn hình và dữ liệu](./reports/scout-screens-and-state.md), [kiểm thử, CI, web và tài liệu](./reports/scout-tests-ci-web-docs.md), [tiền, số và ngày](./reports/scout-money-date-formats.md), [các trang web](./reports/scout-web-pages.md).
- Tra cứu: [thanh dưới với Navigation 3](./research/researcher-01-navigation3-bottom-bar.md), [hai phông chữ](./research/researcher-02-fonts.md).
- Phản biện: [phạm vi](./reports/red-team-scope.md), [bảo mật](./reports/red-team-security.md), [kiểu hỏng](./reports/red-team-failure-modes.md), [giả định](./reports/red-team-assumptions.md).

## Red Team Review

### Session — 2026-11-20
**Findings:** 15 sau khi gộp 39 phát hiện của bốn người phản biện (15 accepted, 0 rejected)
**Severity breakdown:** 0 Critical, 8 High, 7 Medium

Chủ sản phẩm chọn "Áp dụng cả 15 điểm". Bốn điểm được áp dụng có sửa đổi, ghi ở cột Disposition.

| # | Finding | Severity | Disposition | Applied To |
|---|---------|----------|-------------|------------|
| 1 | Lịch sử riêng từng tab không bao giờ có gì để giữ vì thanh chỉ hiện ở màn gốc, trong khi công thức nhiều ngăn xếp cần lifecycle 2.11 | High | Accept: dùng một ngăn xếp, bỏ công thức và việc nâng navigation3 | Phase 3 |
| 2 | Trang chủ và số đếm Requests đọc một bộ nhớ đệm mà chỉ màn Requests làm mới; "chưa tải" và "không có" lẫn vào nhau; ô nhóm không có nguồn dữ liệu | High | Accept (modified): tải lại khi mở app, ba trạng thái, ô nhóm lấy từ `ProxyCache`. Không ẩn số tiền trong màn đa nhiệm: trang chủ hôm nay đã hiện hóa đơn, số tiền và tên người | Phase 3, 5 |
| 3 | Ba giai đoạn màn hình không độc lập; cổng "bỏ các bài so ảnh" không có cách làm; CI không chạy trên nhánh | High | Accept (modified): chạy lần lượt, cổng `-Pscreenshots=compare` đã kiểm, chạy lệnh của CI tại chỗ. Không mở pull request nháp: chủ sản phẩm chưa yêu cầu pull request | plan.md, mọi giai đoạn |
| 4 | Nhãn "Split a bill" của nút tròn chưa định nghĩa trong khi 12 chỗ kiểm thử bám vào; trình sửa mỗi lúc mở một hàng làm hỏng hai bài kiểm thử | High | Accept | Phase 2, 3, 5 |
| 5 | Biến `secondaryContainer` thành màu mực làm 11 nút `FilledTonalButton` và `SvEmptyState` thành khối nặng | High | Accept: `secondaryContainer` là sắc trung tính nhạt; trạng thái đang chọn dùng `inverseSurface` | Phase 1, 2 |
| 6 | Ở cỡ chữ 200%, nhãn của thanh nổi và số tiền chính không thể vừa | High | Accept: định rõ ứng xử chữ lớn và soát sớm | Phase 2, 3, 5, 8 |
| 7 | Tệp phông không có commit ghim hay mã băm; bài thử phông nằm ở module không có Robolectric; hai tệp ExtraBold không chung được một họ; các kiểu emphasized bị bỏ sót | High | Accept | Phase 1 |
| 8 | "Bỏ nhãn Photo stays on this phone" đọc như mâu thuẫn với "màn xin đồng ý không đổi" | High | Accept | plan.md, Phase 5 |
| 9 | Màn "Font licences" không ai yêu cầu | Medium | Accept: chỉ đóng gói văn bản giấy phép | Phase 1, 7 |
| 10 | Xuất token sang web làm đổi cỡ và độ đậm chữ của web, và thêm token không ai dùng | Medium | Accept (modified): web giữ chữ cũ cho tới giai đoạn 9, nơi trang web được thiết kế lại theo quyết định mới của chủ sản phẩm | Phase 1, 9 |
| 11 | `HistoryKey` mang thẻ làm bấm "Repeat" đẩy thêm một màn Bills, và không có thông báo nào mở thẻ Recurring | Medium | Accept: `HistoryKey` giữ nguyên, thẻ là trạng thái của màn, và việc gộp làm ở giai đoạn điều hướng | Phase 3, 7 |
| 12 | Thiếu cách đổi màu nút trong thanh nổi cho 9 nơi dùng; Insights sẽ có hai thanh chồng nhau; việc chuyển 32 chỗ gọi Material nằm sai giai đoạn; thiếu thành phần hộp thoại; `SvTextField` phải giữ tùy chọn bàn phím và giới hạn độ dài; kiểm thử cuộn không bắt được thanh che nội dung | Medium | Accept | Phase 2, 5, 6, 7 |
| 13 | Một lượt `device-tests.yml` với `api-levels=true` không chạy API 34 và không ghi hồ sơ khởi động | Medium | Accept: hai lượt chạy, hồ sơ ghi sau cùng | Phase 8, 10 |
| 14 | Cái giá về minh bạch khi gỡ phần chữ đã gửi chưa được ghi; ba tài liệu pháp lý chưa được đối chiếu; quy tắc soát ảnh cửa hàng bị rơi | Medium | Accept | plan.md, Phase 5, 10 |
| 15 | `moneyOut` đo được 4,499:1; thanh nổi tối không có viền; `NumbersInTextsTest` bị sót; khoảng cách "THB 187.00" chưa quyết; nguồn thiết kế chỉ nằm trên canvas; mục hoàn lại nói quá | Medium | Accept (modified): viền thanh giữ màu `#3A3A42` đã duyệt và không áp 3:1; khoảng cách của mã tiền tệ theo vùng của máy | Phase 1, 4, 5, 7, plan.md |

### Whole-Plan Consistency Sweep
- Files reread: `plan.md` và mười tệp giai đoạn, đọc trọn sau lượt sửa song song: `phase-01-colors-type-shapes.md`, `phase-02-shared-components.md`, `phase-03-navigation-bottom-bar.md`, `phase-04-money-and-date-formats.md`, `phase-05-home-and-split-flow.md`, `phase-06-groups-requests-bill-links.md`, `phase-07-bills-insights-settings-pro.md`, `phase-08-tests-screenshots-profile.md`, `phase-09-web-pages.md`, `phase-10-docs-store-finish.md`.
- Decision deltas checked: 15
- Reconciled stale references: 5 trong lượt rà, sửa ở 8 chỗ của `phase-02-shared-components.md`, `phase-05-home-and-split-flow.md` và `phase-09-web-pages.md`; sau lượt rà sửa thêm 12 chỗ ở `plan.md` và các giai đoạn 1, 3, 4, 7, 9 để đóng các mâu thuẫn và khoảng trống mà lượt rà nêu.
- Unresolved contradictions: 0. Lượt rà nêu bốn mâu thuẫn và cả bốn đã được đóng: mã băm của phông chỉ nằm trong tệp danh sách và tài liệu trỏ tới đó; bảng dạng ngày do người lập plan điền từ nguyên tắc chủ sản phẩm đã chốt; phông của web có trần riêng 120 KB; chữ ngày trên hóa đơn được viết lại khi đọc được và giữ nguyên văn khi không.

## Validation Log

### Session 1 — 2026-11-20
**Trigger:** plan chế độ kỹ, sau lượt phản biện
**Questions asked:** 7

#### Questions & Answers

1. **[Scope]** Giao diện mới gọi AI bằng tên gì? Đề xuất của tôi là giọng của sản phẩm ở ba chỗ: dòng trạng thái "Reading your bill…", tiêu đề thẻ "What Split Vibe understood", và câu giới thiệu "worked out by AI". Tên Claude và Anthropic vẫn nằm nguyên trong màn xin đồng ý và chính sách quyền riêng tư.
   - Options: Giọng sản phẩm ở ba chỗ (Recommended) | Giữ chữ Claude như hiện tại
   - **Answer:** Giọng sản phẩm ở ba chỗ
   - **Rationale:** quyết định chữ nào của `strings.xml` đổi, và giữ app không gợi ý sai về nơi hóa đơn được xử lý.
2. **[Scope]** Số tiền và ngày hiển thị theo kiểu nào? App hiện dùng kiểu tiếng Anh ở mọi nơi, ví dụ "₫902,664". Đợt này ghi lại toàn bộ ảnh tham chiếu, nên đổi bây giờ thì chỉ phải ghi ảnh một lần.
   - Options: Theo vùng của điện thoại (Recommended) | Giữ kiểu tiếng Anh
   - **Answer:** Theo vùng của điện thoại
   - **Rationale:** thêm giai đoạn 4 và đổi những gì ảnh tham chiếu ghi lại.
3. **[Scope]** Các trang web cho người nhận liên kết (trang hóa đơn, yêu cầu trả tiền, mời vào nhóm, chính sách) dùng chung bảng màu với app. Đợt này chúng đổi tới đâu?
   - Options: Chỉ màu, hình khối và bóng (Recommended) | Giữ nguyên trang web | Thiết kế lại cả trang web
   - **Answer:** Thiết kế lại cả trang web
   - **Rationale:** quyết định trang web có thành một phần việc lớn hay không.
4. **[Scope]** Bạn chọn thiết kế lại cả trang web; việc đó cần bảng vẽ riêng và sẽ thành một plan riêng sau đợt app. Trong lúc chờ, các trang web hiện có (trang hóa đơn, yêu cầu trả tiền, mời vào nhóm, chính sách) sẽ thế nào khi bản app mới phát hành?
   - Options: Nhận màu mới, giữ bố cục (Recommended) | Giữ nguyên diện mạo cũ | Vẽ web ngay, gộp vào plan này
   - **Answer:** Vẽ web ngay, gộp vào plan này
   - **Rationale:** thêm giai đoạn 9 và việc vẽ, duyệt bảng web trước khi giai đoạn đó bắt đầu.
5. **[Scope]** Ngoài ba chỗ bạn vừa chọn, phần sửa cách chia bằng một câu còn 5 chuỗi gọi tên Claude: nút "Ask Claude", "Claude is working out the changes…", "Claude found nothing to change.", "Answer the question, and Claude works the change out again." và câu báo có thay đổi không đọc được. Các bảng đã duyệt viết phần này là "the AI". Đổi nốt cho nhất quán?
   - Options: Đổi nốt sang "the AI" (Recommended) | Giữ chữ Claude ở 5 chuỗi này
   - **Answer:** Đổi nốt sang "the AI"
   - **Rationale:** không để một màn dùng hai cách gọi.
6. **[Tradeoffs]** Bạn chọn số tiền và ngày theo vùng của điện thoại, nhưng app hiện chỉ có chữ tiếng Anh. Trên máy đặt tiếng Việt, ngày ngắn nên hiện thế nào?
   - Options: "13 Nov" (Recommended) | "13 thg 11"
   - **Answer:** "13 Nov"
   - **Rationale:** tên tháng theo ngôn ngữ của chữ trong app, thứ tự theo vùng; quy tắc này cũng áp cho ký hiệu tiền tệ và chữ số.
7. **[Risks]** Bảng thiết kế đã duyệt hiện chỉ nằm trên canvas riêng tư của bạn. Người thực hiện và người review cần một bản cố định để so. Có lưu 37 bảng đã duyệt vào repo không? Repo này công khai.
   - Options: Lưu vào thư mục plan (Recommended) | Chỉ giữ trên canvas
   - **Answer:** Lưu vào thư mục plan
   - **Rationale:** nguồn thiết kế có phiên bản và mã băm, không phụ thuộc vào canvas đang sống.

Câu hỏi về cách xử lý 15 điểm phản biện được ghi ở mục Red Team Review.

#### Confirmed Decisions
- Chữ gọi AI: giọng sản phẩm ở mọi chuỗi ngoài phần xin đồng ý. App vẫn nói thật, và phần xin đồng ý vẫn nêu Claude và Anthropic.
- Tiền và ngày: theo vùng của máy, tên tháng và ký hiệu tiền tệ theo ngôn ngữ của chữ trong app.
- Trang web: thiết kế lại trong plan này, sau khi bảng web được duyệt.
- Nguồn thiết kế: lưu trong repo.

#### Action Items
- [x] Cập nhật ba chỗ chữ trên canvas và lưu 37 bảng vào `boards/`.
- [x] Thêm giai đoạn 4 và giai đoạn 9, đánh số lại các giai đoạn.
- [ ] Chủ sản phẩm duyệt các bảng web; sau đó lưu chúng vào `boards/web/` và chốt giai đoạn 9.

#### Impact on Phases
- Phase 1: bảng màu sửa theo điểm 5 và 15; phông có nguồn ghim; web giữ chữ cũ.
- Phase 2: thêm hộp thoại, cơ chế màu trong thanh nổi, ứng xử chữ lớn; việc chuyển chỗ gọi rời sang giai đoạn sở hữu.
- Phase 3: một ngăn xếp; nhãn "Split a bill"; tổng yêu cầu và số đếm.
- Phase 4: giai đoạn mới.
- Phase 5, 6, 7: chữ mới, ba trạng thái của trang chủ, chỗ gọi Material và hộp thoại của gói mình, `HistoryKey` giữ nguyên, bỏ màn giấy phép phông.
- Phase 8: hai lượt chạy trên máy ảo, ảnh cho máy đặt vùng Việt Nam.
- Phase 9: giai đoạn mới, chờ duyệt bảng web.
- Phase 10: đối chiếu tài liệu pháp lý, quy tắc soát ảnh cửa hàng, hồ sơ khởi động ghi sau cùng.

### Verification Results
- **Tier:** Full
- **Claims checked:** 115 của bốn người phản biện, cộng 4 việc kiểm trong phiên này
- **Verified:** xem dưới | **Failed:** 0 sau khi sửa | **Unverified:** 4

Bốn người phản biện báo 37 điều sai và 9 điều đúng một phần trong bản nháp. Mỗi điều ứng với một trong 15 điểm ở trên và đã được sửa hoặc gỡ khỏi plan. Bốn việc kiểm trong phiên này:

1. [Flow Tracer] Chế độ `-Pscreenshots=compare`: đã chạy thật, kết quả ghi ở mục Cổng kiểm thử.
2. [Fact Checker] Nguồn của bảy tệp phông: đã tải theo commit ghim và ghi mã băm trong giai đoạn 1.
3. [Fact Checker] Mọi chỗ app viết tiền, số và ngày: ghi kèm tệp và dòng trong [bản rà](./reports/scout-money-date-formats.md).
4. [Contract Verifier] Các trang web, kiểu dáng, chính sách bảo mật nội dung và bài kiểm thử bám vào chúng: ghi trong [bản rà](./reports/scout-web-pages.md).

Bốn điều chưa kiểm được, mỗi điều là bước đầu tiên của giai đoạn chứa nó:

1. [UNVERIFIED] Robolectric nạp được phông từ `res/font` (giai đoạn 1).
2. [UNVERIFIED] Mã băm của bảy tệp phông khớp khi tải trên một máy sạch của GitHub (giai đoạn 1).
3. [UNVERIFIED] Thứ tự ngày, tháng, năm mà Android trả về cho từng vùng ở API 26 khớp với bản Java dùng trong kiểm thử (giai đoạn 4).
4. [UNVERIFIED] Ba tệp phông cho web vừa trong trần 120 KB nén dành riêng cho phông (giai đoạn 9).

### Whole-Plan Consistency Sweep
- Files reread: `plan.md` và mười tệp giai đoạn, đọc trọn sau lượt sửa song song: `phase-01-colors-type-shapes.md`, `phase-02-shared-components.md`, `phase-03-navigation-bottom-bar.md`, `phase-04-money-and-date-formats.md`, `phase-05-home-and-split-flow.md`, `phase-06-groups-requests-bill-links.md`, `phase-07-bills-insights-settings-pro.md`, `phase-08-tests-screenshots-profile.md`, `phase-09-web-pages.md`, `phase-10-docs-store-finish.md`.
- Decision deltas checked: 15
- Reconciled stale references: 5 trong lượt rà, sửa ở 8 chỗ của `phase-02-shared-components.md`, `phase-05-home-and-split-flow.md` và `phase-09-web-pages.md`; sau lượt rà sửa thêm 12 chỗ ở `plan.md` và các giai đoạn 1, 3, 4, 7, 9 để đóng các mâu thuẫn và khoảng trống mà lượt rà nêu.
- Unresolved contradictions: 0. Lượt rà nêu bốn mâu thuẫn và cả bốn đã được đóng: mã băm của phông chỉ nằm trong tệp danh sách và tài liệu trỏ tới đó; bảng dạng ngày do người lập plan điền từ nguyên tắc chủ sản phẩm đã chốt; phông của web có trần riêng 120 KB; chữ ngày trên hóa đơn được viết lại khi đọc được và giữ nguyên văn khi không.

<!-- slug: ui-redesign -->
