---
title: "Phase 3: Điều hướng với thanh dưới"
status: pending
phase: 3
priority: P1
effort: "3d"
dependencies: [2]
---

# Phase 3: Điều hướng với thanh dưới

## Overview

App có bốn màn gốc Home, Groups, Requests và Insights với thanh nổi phía dưới và nút tròn "Split a bill" ở giữa. Điều hướng vẫn là một ngăn xếp như hôm nay: bấm một tab thay màn gốc đang đứng, lùi từ một màn gốc khác Home đưa về Home, và liên kết mở app giữ nguyên ứng xử. Bills và Recurring gộp thành một màn hai thẻ, còn tổng và số đếm yêu cầu trả tiền có một nguồn chung cho cả app.

## Context

- Điều hướng hiện tại: [rà hệ thiết kế](./reports/scout-design-system-navigation.md), mục 6, và [rà màn hình](./reports/scout-screens-and-state.md), mục 0. Nhãn mà kiểm thử bám vào: [rà kiểm thử](./reports/scout-tests-ci-web-docs.md), mục 2 và 3.
- [Tra cứu Navigation 3](./research/researcher-01-navigation3-bottom-bar.md): giai đoạn này dùng phần về nút lùi, vuốt lùi có xem trước và phần tử dùng chung. Công thức nhiều ngăn xếp mà bản tra cứu khuyến nghị không được dùng; lý do nằm ở mục Key Insights.
- Bằng chứng kèm tệp và dòng cho các yêu cầu dưới đây: phản biện về [giả định](./reports/red-team-assumptions.md), [kiểu hỏng](./reports/red-team-failure-modes.md), [phạm vi](./reports/red-team-scope.md) và [bảo mật](./reports/red-team-security.md).
- Bảng tham chiếu: [`Main`](./boards/Main.dc.html), [`Groups`](./boards/Groups.dc.html), [`Requests`](./boards/Requests.dc.html), [`Insights`](./boards/Insights.dc.html) (thanh ở bốn màn gốc); [`Group`](./boards/Group.dc.html) và [`NewSplit`](./boards/NewSplit.dc.html) (không có thanh); [`Bills`](./boards/Bills.dc.html) và [`Recurring`](./boards/Recurring.dc.html) (hai thẻ của một màn).

## Key Insights

- App đang có một ngăn xếp: `rememberNavBackStack(HomeKey)` trong `AppNavHost`, hàm `open()` thêm một màn trừ khi nó đã ở đỉnh, và `remove()` gỡ một màn khi lùi. Mô hình này được giữ.
- Thanh chỉ hiện ở bốn màn gốc, nên người dùng chỉ đổi tab được khi đang đứng ở một màn gốc. Lịch sử riêng của một tab vì thế không bao giờ có gì để giữ. Thêm nữa, lifecycle 2.10.0 dùng chung một kho view model cho mỗi khóa, nên hai bản của cùng một khóa ở hai ngăn xếp sẽ chung trạng thái.
- Thanh phải được vẽ bên trong bốn màn gốc, trong `Screen {}`, không bọc quanh `NavDisplay`. Như vậy nó tự ẩn ở các màn luồng, đi theo cử chỉ vuốt lùi, và nút giữa giữ được hiệu ứng nở `SPLIT_SURFACE` (`HomeScreen.kt:88`, `SplitScreen.kt:134`). Ngoài `Screen {}`, `sharedSurface` im lặng không làm gì.
- Mỗi bước của luồng chia hóa đơn đã có thanh đáy riêng, nên thanh điều hướng không được xuất hiện ở đó.
- `RequestRepository.requests` là `null` cho tới khi được tải, và lại là `null` sau mỗi lần đăng nhập, đăng xuất và xóa tài khoản (`RequestRepository.kt:53-60`). Hôm nay chỉ `RequestsViewModel` làm mới danh sách này (`RequestsViewModel.kt:59-66`), tức chỉ khi màn Requests mở; lời gọi ở `SettingsViewModel.kt:60` làm mới số lượt gọi AI của tài khoản, không phải danh sách này. Số đếm trên thanh vì thế cần một nơi tải riêng.
- Nút giữa là nút tròn chỉ có biểu tượng, trong khi mười hai chỗ của kiểm thử và hành trình ghi hồ sơ khởi động đang tìm chữ "Split a bill". Chúng phải chuyển sang tìm theo mô tả.
- `open()` chỉ bỏ qua một khóa trùng với màn ở đỉnh. Hôm nay "Repeat" trong màn Bills mở `RecurringKey` thành một màn nữa; khi Recurring chỉ là một thẻ của Bills, việc đó phải thành đổi thẻ tại chỗ.
- Cài đặt được mở từ Home, Split, Claim, Groups, JoinGroup, Requests và RequestLink, nên lúc đăng xuất có thể còn màn của tài khoản cũ nằm dưới nó.
- Liên kết chỉ được đọc một lần, lúc Activity được tạo mới; `LaunchedEffect(link)` trong `AppNavHost` mở đích ngay trên Home.

## Requirements

Ngăn xếp và thanh:
- Một ngăn xếp duy nhất, như hôm nay: `rememberNavBackStack(HomeKey)`, `open()` và `remove()`. Không thêm lớp trạng thái điều hướng nào, không chép công thức nhiều ngăn xếp, không nâng navigation3 hay lifecycle, và không đụng tệp Gradle nào.
- Dạng của ngăn xếp: chỉ số 0 luôn là `HomeKey`; nhiều nhất một trong `GroupsKey`, `RequestsKey`, `InsightsKey` nằm ở chỉ số 1.
- Chọn một mục trên thanh: Home cắt ngăn xếp còn `[HomeKey]`; mục khác đưa ngăn xếp về `[HomeKey, tab]` trong một thay đổi duy nhất, bằng cách đặt lại chỉ số 1 hoặc thêm vào. Mục đang chọn của thanh được đọc từ ngăn xếp; không có trạng thái tab riêng.
- Lùi ở một màn gốc khác Home gỡ màn đó khỏi ngăn xếp nên về Home, đúng như `remove(key)` hôm nay vẫn làm; lùi ở Home thoát app. Vị trí cuộn của một màn gốc không được giữ khi chọn tab khác.
- Thanh chỉ hiện ở bốn màn gốc. Bốn màn gốc nhận một ô `bottomBar`, mặc định rỗng, và mất nút lùi riêng: `GroupsScreen.onBack`, `RequestActions.onBack`, `InsightsActions.onBack`. Nơi gọi trong kiểm thử phải sửa trong giai đoạn này: `ui/GroupsGalleryTest.kt:79` và `:194`, `ui/SpendingGalleryTest.kt:118`.
- `InsightsScreen` hôm nay đã đặt `SvBottomBar` của "Export these bills" trong ô đó (`InsightsScreen.kt:123`). Cho tới khi giai đoạn 7 đưa nút này vào nội dung, hai thanh xếp chồng theo chiều dọc trong cùng ô, thanh điều hướng ở dưới, để không cái nào che cái nào.
- Danh sách của bốn màn gốc nhận `contentPadding` đáy bằng chiều cao đo được của thanh, không dùng một hằng số, vì thanh cao lên theo cỡ chữ. Phép kiểm thanh che nội dung của giai đoạn 2 được áp cho một màn gốc.

Nút giữa và nhãn:
- Nút giữa gọi `startNewSplit()` rồi mở `SplitKey` phía trên màn gốc đang đứng; lùi trở về đúng màn gốc đó. Nó mang `SPLIT_SURFACE` thay cho nút nổi của Home.
- Mô tả của nút là "Split a bill" (chuỗi `home_split_a_bill`), và nút không có chữ hiện. Mười hai chỗ đang tìm theo chữ chuyển sang tìm theo mô tả trong giai đoạn này: `BackNavigationTest.kt:83` và `:104`; `AppStartupTest.kt:33` và `:65`; trong `app/src/androidTest`: `SplitFlowOnDeviceTest.kt:87`, `AppLaunchTest.kt:45` và `:51`, `e2e/SampleBillJourneyTest.kt:40`, `e2e/DeclinedAiJourneyTest.kt:38`, `e2e/PaymentRequestJourneyTest.kt:75`, `e2e/ManualEntryJourneyTest.kt:32`; và `baseline-profile/src/main/java/com/dantech/splitvibe/baselineprofile/Journeys.kt:19`, nơi `By.text` thành `By.desc`.
- Mục của thanh được tìm bằng vai `Tab` cùng nhãn, vì "Requests" giờ vừa là nhãn tab vừa là tiêu đề màn; `e2e/PaymentRequestJourneyTest.kt:113` đổi theo.
- Nhãn mới của giai đoạn: "Home" cho mục thứ nhất của thanh; "All bills" và "Recurring" cho hai thẻ của màn Bills. Ba mục còn lại dùng chuỗi sẵn có `groups_title`, `requests_title` và `home_insights`.

Trang chủ:
- `HomeScreen` mất nút nổi và bốn nút dẫn tới Groups, Requests, Insights và Recurring (`HomeScreen.kt:112-115`), cùng hàm `PlaceButton` chỉ chúng dùng, nơi có `FilledTonalButton` ở `HomeScreen.kt:147`. Các tham số `onSplitBill`, `onOpenGroups`, `onOpenRequests`, `onOpenInsights` và `onOpenRecurring` rời khỏi `HomeScreen`. Nơi gọi phải sửa: `entry<HomeKey>` trong `ui/AppNavHost.kt`, và ba chỗ trong kiểm thử đang truyền `onSplitBill`: `ui/ScreenGalleryTest.kt:386`, `ui/PseudoLocaleGalleryTest.kt:87`, `text/NumbersInTextsTest.kt:154`.
- Cài đặt mở từ nút tài khoản trên Home. Mô tả của nút vẫn là "Settings", vì `AppStartupTest.kt:40`, `:49`, `:71` và `AppLaunchTest.kt:41` tìm nút theo mô tả đó.

Bills và Recurring:
- Ngay trong giai đoạn này Bills và Recurring thành hai thẻ của một màn. Đây mới là cấu trúc, với hình dáng hôm nay; hình dáng mới thuộc giai đoạn 7.
- `HistoryKey` vẫn là `data object`. Thẻ đang chọn là trạng thái của màn, giữ bằng `rememberSaveable`. "Repeat" đổi thẻ tại chỗ, không mở màn thứ hai. `RecurringKey` và `entry<RecurringKey>` bị bỏ; `RecurringViewModel` vẫn lấy theo Activity.
- `RecurringKey` hôm nay có ở bốn chỗ trong `AppNavHost.kt` (khai báo, nút trên Home, "Repeat" của History, mục của chính nó) và ở `docs/architecture.md:157-159`; tài liệu được sửa ở giai đoạn 10.
- Không thông báo nào mở Recurring. Thông báo về bản nháp mở chính hóa đơn đó (`AppLink.kt:40-42`), và việc này giữ nguyên.
- Nơi dựng `HistoryScreen` và `RecurringScreen` phải sửa theo: `entry<HistoryKey>`, `text/NumbersInTextsTest.kt:155`, `ui/ScreenGalleryTest.kt:388`, và hai hàm `History()` và `Recurring()` của `ui/SpendingGalleryTest.kt`.

Tổng và số đếm yêu cầu trả tiền:
- `ui/requests/RequestTotals.kt` là mã thuần: tổng theo từng tiền tệ của khoản bạn bè nợ mình và khoản mình nợ, và số yêu cầu đang chờ mình. Nó có ba trạng thái: `SignedOut`, `Unknown` (đã đăng nhập nhưng danh sách chưa tải xong) và `Loaded`.
- Bạn bè nợ mình là các yêu cầu mình tạo mà mình chưa xác nhận. Mình nợ là các yêu cầu gửi tới mình mà mình chưa báo đã trả. Đang chờ mình là khoản mình nợ mà chưa ai báo gì, hoặc khoản người khác nợ mình mà người trả đã báo.
- Một bộ giữ theo Activity, tạo trong `AppNavHost` như `RecurringViewModel` (ví dụ `RequestOverviewViewModel`), cầm các tổng này. Nó gọi `RequestRepository.refresh()` khi khởi động lúc đã đăng nhập và sau mỗi lần đăng nhập. Lời gọi chạy lặng lẽ: lỗi mạng hay lỗi máy chủ không hiện thông báo và không làm app dừng.
- Mục Requests của thanh mang số yêu cầu đang chờ mình; không có số khi chưa đăng nhập, khi danh sách chưa tải xong, hoặc khi số đó là 0. Giai đoạn 5 và 6 đọc cùng bộ giữ này, nên trang chủ và màn Requests không thể lệch nhau.

Liên kết và tài khoản:
- Liên kết mở app giữ đúng ứng xử hôm nay: đích mở ngay trên Home, qua `LaunchedEffect(link)` trong `AppNavHost`, và lùi về Home. `AppLinkTest` vẫn qua mà không sửa; mỗi loại liên kết có thêm một phép kiểm lùi.
- Sau khi đăng xuất hoặc xóa tài khoản, ngăn xếp chỉ còn `HomeKey` và màn nơi việc đó xảy ra, tức Settings. Quy tắc này có bài kiểm thử.

Kiểm thử:
- Kiểm thử đi qua giao diện, bằng cách bấm lên thanh, trong `BackNavigationTest` và `AppStartupTest`: từ tab này sang tab khác; lùi về Home rồi thoát; vuốt lùi từ Groups xem trước Home, hủy thì ở lại, hoàn tất thì về Home; trạng thái còn sau `recreate()`; thanh có ở bốn màn gốc và không có ở Split, Claim, Group, Settings, Pro, PaymentMethods và History; nút giữa mở luồng từ mọi màn gốc và lùi về đúng màn đó; bấm một tab giữa lúc đang vuốt lùi không để màn trắng.
- Trong lúc chuyển cảnh, cả hai cảnh đều có một phần tử mang `SPLIT_SURFACE`. Phép kiểm này thay ảnh chụp làm chốt canh hiệu ứng nở; `BackNavigationTest.kt:90` vẫn ghi ảnh của nó, và chế độ so chấp nhận ảnh khác. Để kiểm thử tìm được phần tử đó, `sharedSurface` đánh dấu nó bằng tên bề mặt trong ngữ nghĩa (ví dụ `testTag`), và chỉ khi đang ở trong `Screen {}`.
- `TabNavigationTest` kiểm hàm thuần của việc chọn tab và của quy tắc đổi tài khoản; `RequestTotalsTest` kiểm các tổng, số đếm và ba trạng thái.

## Files to Create / Modify

- Create: `app/src/main/java/com/dantech/splitvibe/ui/TabNavigation.kt` (hàm thuần tính ngăn xếp mới khi chọn tab và khi tài khoản rời đi, cùng hàm mở rộng của `NavBackStack` áp kết quả trong một thay đổi).
- Create: `app/src/main/java/com/dantech/splitvibe/ui/AppNavBar.kt` (bọc `SvNavBar`: bốn đích, số đếm của Requests, nút giữa mang `SPLIT_SURFACE`, phần tử dùng chung "bottom-bar").
- Create: `ui/requests/RequestTotals.kt` và bộ giữ theo Activity của nó, ví dụ `ui/requests/RequestOverviewViewModel.kt`, đăng ký trong `di/AppModule.kt`.
- Create: `app/src/test/java/com/dantech/splitvibe/ui/TabNavigationTest.kt` và `ui/requests/RequestTotalsTest.kt`.
- Modify: `ui/AppNavHost.kt`. `entry<HomeKey>`, `entry<GroupsKey>`, `entry<RequestsKey>` và `entry<InsightsKey>` truyền `AppNavBar` và bỏ `onBack`; `entry<HistoryKey>` nhận `recurringViewModel` và để "Repeat" đổi thẻ; `RecurringKey` và `entry<RecurringKey>` bị bỏ; bộ giữ các tổng yêu cầu được tạo cạnh `RecurringViewModel`; thêm quy tắc đổi tài khoản. `LaunchedEffect(link)` giữ nguyên.
- Modify: `ui/home/HomeScreen.kt`, `ui/groups/GroupsScreen.kt`, `ui/requests/RequestsScreen.kt`, `ui/insights/InsightsScreen.kt` (ô `bottomBar`, bỏ nút lùi của màn gốc); `ui/history/HistoryScreen.kt` và `ui/recurring/RecurringScreen.kt` (một màn hai thẻ).
- Modify: `core/designsystem/src/main/java/com/dantech/splitvibe/designsystem/motion/SharedSurface.kt` (dấu để kiểm thử tìm được phần tử mang một bề mặt dùng chung).
- Modify: `app/src/main/res/values/strings.xml` (ba nhãn mới).
- Modify trong `app/src/test/java/com/dantech/splitvibe/`: `BackNavigationTest.kt`, `AppStartupTest.kt`, `ui/GroupsGalleryTest.kt`, `ui/SpendingGalleryTest.kt`, `ui/ScreenGalleryTest.kt`, `ui/PseudoLocaleGalleryTest.kt`, `text/NumbersInTextsTest.kt`.
- Modify trong `app/src/androidTest/java/com/dantech/splitvibe/`: `AppLaunchTest.kt`, `SplitFlowOnDeviceTest.kt`, `e2e/SampleBillJourneyTest.kt`, `e2e/DeclinedAiJourneyTest.kt`, `e2e/PaymentRequestJourneyTest.kt`, `e2e/ManualEntryJourneyTest.kt`, và `e2e/JourneySteps.kt` nếu cần một hàm tìm tab dùng chung.
- Modify: `baseline-profile/src/main/java/com/dantech/splitvibe/baselineprofile/Journeys.kt`.
- Không sửa `gradle/libs.versions.toml`, `gradle/verification-metadata.xml` hay `ui/AppLinkTest.kt`.

## Implementation Steps

1. Viết `ui/TabNavigation.kt` và `TabNavigationTest` trước: chọn Home, chọn tab khác từ `[HomeKey]` và từ `[HomeKey, tab]`, chọn lại tab đang đứng, và ngăn xếp sau khi tài khoản rời đi.
2. Viết `RequestTotals` và `RequestTotalsTest`: cộng theo tiền tệ; bỏ yêu cầu đã xác nhận; khoản mình nợ bỏ yêu cầu đã báo trả; số đang chờ mình; `null` khác danh sách rỗng; chưa đăng nhập. Thêm bộ giữ theo Activity và tạo nó trong `AppNavHost`.
3. Thêm `AppNavBar`. Cho bốn màn gốc ô `bottomBar`, bỏ nút lùi riêng của chúng, và truyền thanh từ `entry<HomeKey>`, `entry<GroupsKey>`, `entry<RequestsKey>` và `entry<InsightsKey>`. Sửa ba chỗ kiểm thử đang truyền `onBack`.
4. Trang chủ: bỏ nút nổi, bốn nút dẫn và `PlaceButton`; giữ nút tài khoản với mô tả "Settings"; chuyển `SPLIT_SURFACE` sang nút giữa của thanh. Sửa bốn nơi gọi `HomeScreen`.
5. Gộp Bills và Recurring thành một màn hai thẻ, cho "Repeat" đổi thẻ tại chỗ, bỏ `RecurringKey` và `entry<RecurringKey>`. Sửa các nơi dựng hai màn này.
6. Thêm quy tắc đổi tài khoản vào `AppNavHost`. Không đổi `LaunchedEffect(link)`.
7. Cho `sharedSurface` đánh dấu phần tử của nó, rồi đổi mười hai chỗ tìm chữ "Split a bill" sang tìm theo mô tả, và chỗ tìm mục Requests sang vai `Tab` cùng nhãn.
8. Mở rộng `BackNavigationTest` và `AppStartupTest` theo phần Kiểm thử, kể cả phép kiểm lùi cho từng loại liên kết và bài đổi tài khoản.
9. Cho các bài dựng bốn màn gốc truyền `AppNavBar` vào ô `bottomBar` để ảnh có thanh: `ui/ScreenGalleryTest.kt:386` (Home, kể cả ảnh `01-home-large-text`), `ui/GroupsGalleryTest.kt` (bài `22-groups` ở `:65`, hai bài `25-requests` ở `:111` và `:116`) và hàm `Insights()` của `ui/SpendingGalleryTest.kt`. Thêm phép kiểm thanh che nội dung cho một màn gốc.
10. Chạy cổng kiểm thử của plan với `-Pscreenshots=compare`, và đọc kết quả của lượt `device-tests.yml` trên commit cuối. Gửi chủ sản phẩm ảnh `01-home`, `22-groups`, `25-requests` và `40-insights`, cùng các bản tối và bản chữ lớn đang có của chúng, lấy từ `app/build/outputs/roborazzi/*_actual.png`.

## Todo

- [ ] `TabNavigation.kt` và `TabNavigationTest`
- [ ] `RequestTotals`, `RequestTotalsTest` và bộ giữ theo Activity, tạo trong `AppNavHost`
- [ ] `AppNavBar`; bốn màn gốc có ô `bottomBar`, không còn nút lùi riêng; ba chỗ kiểm thử được sửa
- [ ] Trang chủ không còn nút nổi và bốn nút dẫn; nút giữa mang `SPLIT_SURFACE`; bốn nơi gọi `HomeScreen` được sửa
- [ ] Bills và Recurring thành một màn hai thẻ; `RecurringKey` bị bỏ
- [ ] Quy tắc đổi tài khoản; liên kết mở app không đổi
- [ ] Dấu của `sharedSurface`; mười hai chỗ tìm "Split a bill" và chỗ tìm tab Requests được đổi
- [ ] `BackNavigationTest` và `AppStartupTest` mở rộng
- [ ] Ảnh bốn màn gốc có thanh; phép kiểm thanh che nội dung cho một màn gốc
- [ ] Cổng kiểm thử qua, lượt `device-tests.yml` được đọc, ảnh gửi chủ sản phẩm

## Success Criteria

- Cổng kiểm thử của plan qua (với `-Pscreenshots=compare`). Các bài riêng của giai đoạn: `TabNavigationTest`, `RequestTotalsTest`, `BackNavigationTest`, `AppStartupTest` và `AppLinkTest`.
- Lượt `device-tests.yml` trên commit cuối được đọc và ghi lại: `AppLaunchTest`, `SplitFlowOnDeviceTest` và bốn hành trình `e2e` qua trên máy ảo API 34, và việc `profile` chạy trọn với `Journeys.kt` mới.
- `gradle/libs.versions.toml` và `gradle/verification-metadata.xml` không đổi.
- Chủ sản phẩm nhận ảnh bốn màn gốc có thanh, lấy từ `app/build/outputs/roborazzi/*_actual.png`; trên ảnh `01-home-large-text_actual.png` thanh không có chữ nào bị cắt.
- Thử tay trên máy ảo: hiệu ứng nở từ nút giữa sang màn chia hóa đơn còn nguyên, và vuốt lùi từ một tab xem trước Home.
- `git status --porcelain app/src/test/screenshots` rỗng.

## Risk Assessment

- Phần tử dùng chung của thanh không đứng yên khi chuyển tab: thanh mờ đi rồi hiện lại. Chấp nhận được.
- Hiệu ứng nở mất vì nút giữa nằm ngoài `Screen {}`: `sharedSurface` khi đó im lặng không làm gì. Phép kiểm tìm phần tử mang `SPLIT_SURFACE` ở cả hai cảnh bắt lỗi này.
- navigation3 ở lại bản 1.2.1. Bản này có một lỗi đã biết, ghi trong bản tra cứu: một màn có thể không được vẽ khi một điều hướng chen vào lúc đang lùi. Ứng xử của nó khi màn gốc ở chỉ số 1 bị thay cũng mới được suy từ mã nguồn của nhánh 1.3 alpha. Các bài ở phần Kiểm thử là bằng chứng thật; nếu bài bấm tab giữa lúc vuốt lùi hỏng, ghi lại và báo chủ sản phẩm, vì plan không nâng navigation3.
- Bỏ `RecurringKey` làm một ngăn xếp được lưu từ bản cũ, đang có khóa này, không khôi phục được. Việc đó chỉ có thể xảy ra ở lần mở đầu sau khi cập nhật, khi màn Recurring đang mở lúc cập nhật. Ứng phó: thử một lần trên máy ảo (cài bản hiện hành, mở Recurring, cài đè bản mới, mở lại app) và ghi kết quả; nếu app dừng thì báo chủ sản phẩm.
- Thẻ Recurring mất đường vào khi máy không còn hóa đơn nào: trang chủ chỉ hiện "See all" khi có hóa đơn, trong khi hóa đơn định kỳ được lưu riêng và còn lại sau khi hóa đơn gốc bị xóa, ví dụ một hóa đơn định kỳ đang tạm dừng. Hôm nay nút "Recurring bills" trên trang chủ lo việc đó. Ứng phó, là mặc định của plan mà chủ sản phẩm đổi được: trang chủ hiện "See all" khi máy có hóa đơn hoặc có hóa đơn định kỳ, nên màn Bills luôn mở được; giai đoạn 5 giữ quy tắc này khi dựng lại trang chủ.
- Thêm một lời gọi tới máy chủ cho mỗi lần mở app khi đã đăng nhập: đây là mặc định ghi trong plan, chủ sản phẩm đổi được.

## Rollback

Hoàn lại các commit của giai đoạn chỉ dùng được khi giai đoạn 4 chưa bắt đầu. Sau đó, lỗi được sửa bằng commit mới, vì các giai đoạn 5 đến 7 dựng trên thanh, `RequestTotals` và màn Bills hai thẻ. Không có dữ liệu lưu nào đổi dạng: ngăn xếp chỉ sống trong trạng thái lưu của Activity.
