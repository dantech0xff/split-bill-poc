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

App có bốn tab Home, Groups, Requests và Insights với thanh nổi phía dưới và nút "Split a bill" ở giữa. Mỗi tab giữ lịch sử riêng, nút lùi luôn đi qua Home trước khi thoát, và các liên kết mở app dựng đúng ngăn xếp.

## Context

- Điều hướng hiện tại: [rà hệ thiết kế](./reports/scout-design-system-navigation.md), mục 6; [rà màn hình](./reports/scout-screens-and-state.md), mục 0.
- Cách làm được khuyến nghị và nguồn: [tra cứu Navigation 3](./research/researcher-01-navigation3-bottom-bar.md).
- Bảng tham chiếu trên canvas: `Main`, `Groups`, `Requests`, `Insights` (thanh ở bốn màn gốc), `Group` và `NewSplit` (không có thanh).

## Key Insights

- App đang có một ngăn xếp chỉ biết đẩy (`AppNavHost.kt:146`, `open()` ở dòng 554). Các màn Groups, Requests, Insights tự gỡ mình khỏi ngăn xếp khi lùi; với tab, việc đó phải thành "về Home".
- Công thức `multiplestacks` ở thẻ `1.2.0` của android/nav3-recipes giữ một ngăn xếp lưu được cho mỗi tab, tab đang chọn, và đưa cho `NavDisplay` một danh sách phẳng gồm ngăn xếp của Home cộng ngăn xếp của tab hiện tại. Nhờ danh sách phẳng, vuốt lùi từ một tab xem trước được Home.
- Thanh phải được vẽ bên trong bốn màn gốc, không bọc quanh `NavDisplay`: như vậy nó tự ẩn ở các màn luồng, đi theo cử chỉ vuốt lùi, và nút giữa giữ được hiệu ứng nở `SPLIT_SURFACE` (`HomeScreen.kt:88`, `SplitScreen.kt:134`).
- Mỗi bước của luồng chia hóa đơn đã có thanh đáy riêng, nên thanh điều hướng không được xuất hiện ở đó.
- `SettingsKey`, `ProKey` và `PaymentMethodsKey` được mở từ nhiều nơi, nên cùng một khóa có thể nằm trong hai ngăn xếp. Với lifecycle 2.10.0 điều này chưa được chứng minh là an toàn.

## Requirements

- Bốn khóa gốc theo thứ tự cố định: `HomeKey`, `GroupsKey`, `RequestsKey`, `InsightsKey`.
- Chuyển tab giữ nguyên lịch sử của từng tab. Bấm lại tab đang mở đưa tab về màn gốc.
- Lùi ở màn gốc của một tab khác Home đưa về Home; lùi ở Home thoát app.
- Nút giữa gọi `startNewSplit()` rồi đẩy `SplitKey` lên ngăn xếp của tab hiện tại; lùi khỏi luồng trả về đúng tab đó.
- Cài đặt mở từ nút tài khoản trên Home. `SettingsKey`, `ProKey`, `PaymentMethodsKey` được đẩy lên ngăn xếp của tab đang đứng.
- Thanh chỉ hiện ở bốn màn gốc. Mục Requests mang số yêu cầu đang chờ người dùng xử lý.
- Liên kết mở app:

| `AppLink` | Tab | Ngăn xếp của tab |
|---|---|---|
| `Group(id)` | Groups | `GroupsKey`, `GroupKey(id)` |
| `JoinGroup(token)` | Groups | `GroupsKey`, `JoinGroupKey(token)` |
| `Requests` | Requests | `RequestsKey` |
| `OpenRequest(token)` | Requests | `RequestsKey`, `RequestLinkKey(token)` |
| `OpenBill(id)` | Home | `HomeKey`, `SplitKey` qua `openBill` hiện có |

- Nội dung của màn gốc có lề đáy đủ để hàng cuối không nằm dưới thanh; thanh nằm trên vùng cử chỉ của hệ thống.
- Trợ năng: mục của thanh có vai `Tab`, trạng thái chọn và nhãn; nút giữa có nhãn "Split a bill".

## Files to Create / Modify

- Create: `app/src/main/java/com/dantech/splitvibe/ui/NavigationState.kt`, `Navigator.kt` (từ công thức, thêm `open`, `remove`, `replace`, `openIn`, `popToRoot`).
- Create: `app/src/main/java/com/dantech/splitvibe/ui/TabRoot.kt` (bọc màn gốc, vẽ `SvNavBar`).
- Create: `app/src/test/java/com/dantech/splitvibe/ui/NavigatorTest.kt`.
- Modify: `app/src/main/java/com/dantech/splitvibe/ui/AppNavHost.kt` (trạng thái điều hướng, `NavDisplay(entries = …, onBack = …)`, mọi lời gọi `backStack.open` và `backStack.remove`, xử lý `AppLink`).
- Modify: `ui/home/HomeScreen.kt` (bỏ nút nổi và ba nút sang Groups, Requests, Insights; nút tài khoản), `ui/groups/GroupsScreen.kt`, `ui/requests/RequestsScreen.kt`, `ui/insights/InsightsScreen.kt` (bỏ nút lùi của màn gốc).
- Modify: `gradle/libs.versions.toml` và `gradle/verification-metadata.xml` nếu nâng navigation3 lên 1.2.2.
- Modify: `app/src/test/java/com/dantech/splitvibe/BackNavigationTest.kt`, `AppStartupTest.kt`, `ui/AppLinkTest.kt`; `app/src/androidTest/java/com/dantech/splitvibe/AppLaunchTest.kt`, `e2e/PaymentRequestJourneyTest.kt`, `e2e/JourneySteps.kt`; `baseline-profile/src/main/java/com/dantech/splitvibe/baselineprofile/Journeys.kt`.

## Implementation Steps

1. Thử nâng navigation3 từ 1.2.1 lên 1.2.2, bản chỉ sửa lỗi "màn hình không vẽ khi một điều hướng chen vào lúc đang lùi". Việc này cần tải gói mới và thêm mã băm vào `gradle/verification-metadata.xml` theo quy trình hiện có, không nới kiểm tra. Nếu không tải hay không kiểm được gói, giữ 1.2.1 và ghi lại lỗi đã biết.
2. Thêm `NavigationState` và `Navigator`. `remove(key)` không bao giờ gỡ khóa gốc: với khóa gốc nó gọi `goBack()`. `open(key)` giữ luật không đẩy trùng khóa đang ở đỉnh.
3. Trong `AppNavHost`, thay ngăn xếp đơn bằng trạng thái mới; giữ `SharedTransitionLayout`, các hiệu ứng mờ và `Screen {}`. Thay từng lời gọi `backStack.*` bằng lời gọi cùng nghĩa của `navigator`.
4. Thêm `TabRoot {}` cho bốn màn gốc. Thanh mang một phần tử dùng chung tên "bottom-bar" để đứng yên khi chuyển tab; nút giữa mang `SPLIT_SURFACE`.
5. Bấm lại tab đang mở gọi `navigator.popToRoot(tab)`.
6. Đổi `AppLink` sang `navigator.openIn(tab, keys)` theo bảng; giữ luật chỉ áp một lần khi `savedInstanceState == null`.
7. Viết `NavigatorTest` cho mọi luật ở mục Requirements, không cần giao diện.
8. Mở rộng `BackNavigationTest` và `AppStartupTest`: lùi từ tab về Home rồi thoát; vuốt lùi từ Groups xem trước Home, hủy thì ở lại, xong thì về Home; lịch sử từng tab còn sau khi chuyển; ngăn xếp và tab đang chọn còn sau `recreate()`; thanh có ở bốn màn gốc và không có ở Split, Claim, Settings, Pro, PaymentMethods; nút giữa mở luồng từ mọi tab và lùi về đúng tab; mỗi `AppLink` dựng đúng ngăn xếp; `SettingsKey` mở từ Home và từ Groups cùng lúc không lỗi và mỗi bản giữ trạng thái riêng; bấm tab liên tục khi đang lùi không để màn trắng.
9. Sửa các bài kiểm thử trên máy ảo và hành trình ghi hồ sơ khởi động: tìm mục của thanh bằng vai `Tab` cùng nhãn, vì "Requests" giờ vừa là nhãn tab vừa là tiêu đề màn.

## Todo

- [ ] Quyết định về navigation3 1.2.2 được ghi lại
- [ ] `NavigationState`, `Navigator`, `TabRoot`
- [ ] `AppNavHost` dùng điều hướng mới
- [ ] Liên kết mở app theo bảng
- [ ] `NavigatorTest` và các bài kiểm thử lùi, khôi phục, liên kết
- [ ] Kiểm thử trên máy ảo và hành trình hồ sơ khởi động được sửa

## Success Criteria

- `./gradlew :app:testProdDebugUnitTest` qua khi bỏ các bài so ảnh, gồm `NavigatorTest`, `BackNavigationTest`, `AppStartupTest`, `AppLinkTest`.
- `AppLaunchTest` và `PaymentRequestJourneyTest` qua trên máy ảo API 34.
- Thử tay: hiệu ứng nở từ nút giữa sang màn chia hóa đơn còn nguyên; vuốt lùi từ một tab xem trước Home.

## Risk Assessment

- Cùng một khóa trong hai ngăn xếp gây lỗi trạng thái: dấu hiệu là bài kiểm thử `SettingsKey` ở bước 8 hỏng. Ứng phó trong plan: giữ một bản cho mỗi khóa, tức nếu khóa đã có ở ngăn xếp khác thì chuyển sang tab đó. Nâng lifecycle lên 2.11 kéo theo nâng AGP, nên chỉ làm khi chủ sản phẩm đồng ý.
- Phần tử dùng chung của thanh không đứng yên khi chuyển tab: thanh sẽ mờ đi rồi hiện lại. Chấp nhận được; phương án nâng cấp là bộ trang trí cảnh của Navigation 3.
- Hiệu ứng nở mất vì nút giữa nằm ngoài `Screen {}`: `sharedSurface` khi đó im lặng không làm gì. Bài thử tay ở Success Criteria và ảnh `14-back-swipe-preview` bắt lỗi này.
- Ứng xử của 1.2.x được suy từ mã nguồn nhánh 1.3 alpha: các bài kiểm thử ở bước 8 là bằng chứng thật.

## Rollback

Hoàn lại các commit của giai đoạn đưa app về một ngăn xếp. Không có dữ liệu lưu nào đổi dạng, vì trạng thái điều hướng chỉ sống trong trạng thái lưu của Activity.
