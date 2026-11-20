---
title: "Phase 4: Tiền và ngày theo vùng của máy"
status: pending
phase: 4
priority: P1
effort: "3d"
dependencies: [3]
---

# Phase 4: Tiền và ngày theo vùng của máy

## Overview

Số tiền, các số khác và ngày mà app viết ra đi theo vùng của điện thoại; chữ số, ký hiệu tiền tệ và tên tháng vẫn theo chữ tiếng Anh của app. Giai đoạn này thêm một nguồn vùng dùng chung và sửa vài chỗ định dạng lẻ, để các giai đoạn 5 đến 7 dựng màn hình trên cách viết cuối cùng.

## Context

- Quyết định của chủ sản phẩm: mục 7 trong [plan](./plan.md), cùng câu hỏi 2 và 6 của Validation Log ở đó.
- Mọi chỗ app viết tiền, số và ngày hôm nay, kèm tệp và dòng: [rà tiền, số và ngày](./reports/scout-money-date-formats.md).
- Giai đoạn này không có bảng riêng. Các bảng trong [`boards/`](./boards/README.md) vẽ theo kiểu của máy đặt vùng Mỹ, một trong các vùng mà quy tắc dưới đây phủ.
- Nơi dùng kết quả: `SvHeroAmount` của giai đoạn 2 nhận ba phần của một số tiền; giai đoạn 5 đến 7 dùng các hàm ngày; trang web lặp lại quy tắc ở giai đoạn 9.

## Key Insights

- `BillCurrency.format(minor, locale = TEXT_LOCALE)` là hàm định dạng tiền duy nhất và vốn nhận một vùng; chỉ giá trị mặc định bị ghim vào `Locale.US` (`core/domain/src/main/kotlin/com/dantech/splitvibe/domain/TextLocale.kt:11`). Các chỗ gọi nó trong 16 tệp của `app/src/main`, mà bản rà đếm theo từng tệp, đều không truyền vùng, nên đổi giá trị mặc định là đủ và không chỗ gọi nào phải sửa.
- `TEXT_LOCALE` giữ vai trò vùng của chữ: quy tắc số nhiều và số đếm trong câu (`text/UiText.kt:51`, `:76-77`), tên tháng và tên thứ (`ui/recurring/RecurringScreen.kt:162`). Vùng của số là nguồn thứ hai, đọc `Locale.getDefault(Locale.Category.FORMAT)` vào lúc gọi. Android khởi động lại Activity khi người dùng đổi vùng, nên không cần cơ chế báo đổi nào khác.
- Số tiền đang chạy số được định dạng lại ở mỗi khung hình (`motion/SvAmount.kt:57`), và bộ định dạng được giữ theo luồng và theo vùng (`BillCurrency.kt:57`). Nguồn vùng vì thế trả về cùng một đối tượng `Locale` chừng nào vùng mặc định chưa đổi.
- Đã thử ngày 20/11/2026 trên JDK 21, với vùng ép chữ số Latinh bằng phần mở rộng `nu-latn` và ký hiệu lấy từ `Locale.US`: en-US cho "₫902,664"; vi-VN và de-DE cho "902.664 ₫", trong đó dấu cách là dấu cách không ngắt U+00A0; fr-FR nhóm bằng dấu cách hẹp không ngắt U+202F; ar-EG và fa-IR ra chữ số 0 đến 9 kèm một dấu định hướng vô hình ở đầu chuỗi. Không ép ký hiệu thì vi-VN viết đô la Mỹ là "US$" và baht là "฿".
- Cũng lần thử đó cho thấy JDK và máy thật không giống nhau: hi-IN ra "₫902,664" vì `java.text` của JDK chỉ nhóm theo một cỡ, và THB ở en-US ra "THB1,234.50" không có dấu cách. Trên máy, ICU quyết định cách nhóm và khoảng cách cạnh mã tiền tệ, đúng như plan đã chốt là theo vùng của máy.
- Mẫu ngày ngắn của tám vùng trên JDK 21 xếp vào ba lớp: tháng trước (en-US), ngày trước (vi-VN, de-DE, fr-FR, hi-IN, ar-EG), năm trước (ja-JP, fa-IR). Mẫu của ar-EG có dấu định hướng xen giữa, nên phép đọc thứ tự chỉ nhìn các chữ `d`, `M`, `y`.
- [UNVERIFIED] Android lấy dữ liệu vùng từ ICU, khác JDK và khác nhau giữa các bản Android. Thứ tự ngày, tháng, năm mà máy API 26 và API 34 trả về chưa được kiểm; bước 1 kiểm việc này trước mọi việc khác.
- Mọi ô nhập số đã nhận cả "." và ",": `BillCurrency.parse` (`BillCurrency.kt:32-48`) cùng ba biểu thức chính quy của `parsePercent` (`ui/split/DivisionInputs.kt:42-49`), `positiveQuantity` (`ui/editor/BillEditorContent.kt:344-352`) và `positiveRate` (`ui/split/SplitSheets.kt:388-395`). Không mã nào đọc ngược một số tiền đã định dạng kèm ký hiệu.
- Dưới Robolectric, vùng mặc định đi theo `qualifiers` của bài kiểm thử và là en-US khi bài không nêu vùng: Robolectric 4.17 gọi `Locale.setDefault` khi dựng môi trường (đã đọc trong mã của thư viện ngày 20/11/2026). Bài JUnit thuần thì theo vùng của máy chạy; `ui/split/PercentTextTest.kt` là một bài như vậy trong `:app`.
- Dòng "Amount: …" do `PaymentRequestBuilder` dựng đi cùng yêu cầu trả tiền và liên kết hóa đơn lên máy chủ (`PaymentPayload`, gửi từ `data/requests/RequestRepository.kt`), rồi hiện nguyên văn trong phần chi tiết thanh toán của trang web (`web/src/ui/payment-block.tsx`). Người mở trang vì thế thấy dòng đó theo kiểu của máy người gửi, cạnh số tiền mà trang tự viết theo vùng của trình duyệt từ giai đoạn 9. Trong app thì không: máy người nhận tự dựng chi tiết thanh toán. Máy chủ chỉ từ chối ký tự điều khiển (`lineTextOf` trong `proxy/src/input.ts`), nên dấu cách không ngắt và dấu định hướng đi qua được.
- Hai câu hỏi bản rà để ngỏ đã có lời đáp khi đọc mã: `RecordedSampleBillTest.kt:36-39` so với chữ in trên hóa đơn đã ghi, không qua hàm định dạng; `ShareStepper` (`ui/split/DivisionInputs.kt:115`) viết một số nguyên. Cả hai không đổi.
- Bốn bảng `Pro`, `ProMember`, `Settings` và `SettingsStates` viết "1 December" và "17 December 2026" như app hôm nay. Theo quy tắc dưới đây, máy đặt vùng Mỹ viết "December 1" và "December 17, 2026". Khác biệt này được nêu rõ khi gửi ảnh của giai đoạn cho chủ sản phẩm.

## Requirements

Số tiền:
- Dấu nhóm, dấu thập phân, vị trí của ký hiệu tiền tệ và khoảng trắng cạnh ký hiệu theo vùng của máy.
- Chữ số luôn là 0 đến 9. Ký hiệu hoặc mã tiền tệ giữ đúng cái app viết hôm nay, tức bản tiếng Anh: "₫", "$", "THB". Số chữ số thập phân vẫn là của chính tiền tệ.
- Ví dụ trên máy: Việt Nam "902.664 ₫", Mỹ "₫902,664", Đức "902.664 ₫", Ấn Độ "₫9,02,664".
- Mã không thuộc ISO vẫn đứng sau số và cách một dấu cách như hôm nay (`BillCurrency.kt:62`); phần số của nó theo vùng.

Số khác:
- Phần trăm, số lượng, tỉ giá và số lượt gọi AI có phần lẻ nhận dấu thập phân và dấu nhóm của vùng. Dấu "%" và chữ quanh số giữ chỗ của câu tiếng Anh.
- Số đếm nằm trong câu giữ nguyên như hôm nay; mọi số đếm đều dưới một nghìn.

Ngày:
- Thứ tự ngày, tháng, năm theo vùng. Tên tháng và tên thứ theo ngôn ngữ của chữ trong app, hôm nay là tiếng Anh. Chữ số là 0 đến 9 và năm theo lịch Gregory như hôm nay.
- Thứ tự đọc từ mẫu ngày ngắn của vùng, bằng `DateTimeFormatterBuilder.getLocalizedDateTimePattern` với `FormatStyle.SHORT`. Hàm này là Java thuần nên nằm được trong `:core:domain`.
- Có ba lớp thứ tự, mỗi lớp một bộ mẫu tiếng Anh cố định. Tháng và năm viết "November 2026" ở mọi lớp.

| Dạng | Ngày trước | Tháng trước | Năm trước |
|---|---|---|---|
| Ngắn | "13 Nov" | "Nov 13" | "Nov 13" |
| Có năm | "13 Nov 2026" | "Nov 13, 2026" | "2026 Nov 13" |
| Dài | "13 November" | "November 13" | "November 13" |
| Dài, có năm | "13 November 2026" | "November 13, 2026" | "2026 November 13" |

- Ngày ngắn bỏ năm khi ngày thuộc năm hiện tại; ngày của năm khác dùng dạng có năm, để một hóa đơn của năm trước không bị đọc nhầm. Đây cũng là mặc định của người lập plan. Hàm viết ngày ngắn nhận ngày hôm nay làm tham số, và các bài kiểm thử cùng bài chụp ảnh ghim ngày đó, để kết quả không đổi khi sang năm mới.
- Chủ sản phẩm đã chốt nguyên tắc (thứ tự theo vùng, tên tháng tiếng Anh) và dạng ngắn "13 Nov". Các ô còn lại của bảng do người lập plan điền theo nguyên tắc đó, để `ui/paywall/ProTexts.kt:59` và lớp năm trước có mẫu; chủ sản phẩm đổi được.

Ô nhập:
- Ô nhập hiện dấu thập phân của vùng và không nhóm chữ số.
- Việc đọc chữ gõ vào không đổi: `BillCurrency.parse` và ba biểu thức chính quy giữ nguyên, vẫn nhận cả "." và ",". Chỗ nhập nhằng đã biết cũng giữ nguyên: với tiền tệ có ba chữ số thập phân, "1.234" được đọc là một phẩy hai ba bốn chứ không phải một nghìn hai trăm ba mươi tư.

Không đổi:
- Tệp CSV, vốn đã theo vùng (`export/Exporter.kt:48`, `CsvExport.kt:22-23`).
- Số gửi lên máy chủ, vẫn là đơn vị nhỏ nhất và mã tiền tệ (`proxy/src/input.ts:189-211`, `proxy/src/requests.ts:125-141`), và văn bản thông báo đẩy của máy chủ (`proxy/src/push.ts:15-25`).
- Số tiền nằm trong mã QR và liên kết thanh toán: `PaymentRequestBuilder` viết chúng không qua `format`, và chúng phải giống hệt nhau ở mọi vùng.
- Chữ ngày in trên hóa đơn đã quét, khi app không đọc được nó thành một ngày: màn kết quả vẫn hiện nguyên văn như hôm nay (`ui/split/ResultContent.kt:518`). Khi đọc được, giai đoạn 5 hiện nó theo dạng có năm của bảng trên.
- Ngày của tỉ giá, đang được viết nguyên dạng "2026-11-14" như dữ liệu nhận về.

Đổi theo vì dùng chung hàm:
- Bản tóm tắt PDF, thẻ chia sẻ cùng lời tóm tắt đi kèm (`ui/split/RequestMoneyContent.kt:205-325`, `share/ShareCardRenderer.kt:61-62`), tin nhắn đòi tiền và nhắc nợ (`ui/requests/RequestMessages.kt:28-49`), chữ "Amount: …" đi kèm yêu cầu trả tiền, và thông báo tại máy về bản nháp định kỳ (`notifications/Notices.kt:117-118`). Thứ gì đã gửi đi thì mang kiểu viết của máy người gửi.
- Trang web lặp lại quy tắc này ở giai đoạn 9, lấy vùng từ trình duyệt.

Mới trong `:core:domain`:
- Một nguồn vùng: hàm đọc vùng mặc định cho định dạng vào lúc gọi và ép về chữ số Latinh.
- Một hàm trả số tiền thành ba phần `prefix`, `number`, `suffix` cho `SvHeroAmount`, vì ký hiệu đứng trước hay sau số tùy vùng. Ba phần ghép lại bằng đúng kết quả của `format`. Một phần có thể chỉ chứa ký tự vô hình: trên JDK 21, ar-EG cho phần đầu là một dấu định hướng và ký hiệu nằm ở phần cuối.
- Các hàm ngày theo bảng trên, nhận vùng làm tham số với giá trị mặc định là nguồn vùng.

## Files to Create / Modify

Domain, dưới `core/domain/src/main/kotlin/com/dantech/splitvibe/domain/`:
- Modify: `TextLocale.kt` (`:11`): giữ `TEXT_LOCALE`, thêm nguồn vùng.
- Modify: `BillCurrency.kt`: `format` (`:60`), `formatterFor` (`:65-77`) và `inputText` (`:51`); thêm hàm ba phần. `parse` (`:32-48`) không đổi.
- Modify: `ExchangeRate.kt` (`:48`, `:70-75`), `SplitCalculator.kt` (`:238`), `SplitEdits.kt` (`:50`, `:58`, `:333`, `:391`), `payments/PaymentRequestBuilder.kt` (`:60-62`).
- Create: một tệp cho ngày trong cùng gói (ví dụ `DateTexts.kt`): ba lớp thứ tự và các hàm ngày.
- Không sửa nhưng đổi theo vì gọi `currency.format`: `Payment.kt:45-47`, `SplitCalculator.kt:140`, `:144`, `Bill.kt:73-80`.
- Modify: `core/domain/build.gradle.kts`: tác vụ kiểm thử đặt `user.language=en` và `user.country=US`, để chữ viết sẵn trong kiểm thử có cùng nghĩa trên mọi máy.

App, dưới `app/src/main/java/com/dantech/splitvibe/`:
- Modify: `text/Dates.kt` (`:9-11`): `dateText` dùng dạng "có năm". Sáu nơi gọi nó không sửa: `ui/recurring/RecurringScreen.kt:180`, `ui/recurring/RepeatBillSheet.kt:107`, `ui/groups/GroupScreen.kt:430`, `ui/history/HistoryScreen.kt:219`, `export/PdfSummary.kt:199-201`, `notifications/Notices.kt:117-118`.
- Modify: `ui/claim/ClaimScreen.kt` (`:497-498`): dùng hàm ngày chung; tham số `locale` của màn trở thành vùng. Ba chỗ gọi trong `ui/ClaimGalleryTest.kt` truyền `Locale.US` và không phải sửa.
- Modify: `export/Exporter.kt` (`:142`): `monthName` dùng dạng "tháng và năm".
- Modify: `data/account/AccountStore.kt`: ngày lượt gọi quay lại (`:61`) dùng dạng "dài"; số lượt gọi (`:81`) dùng dấu của vùng.
- Modify: `ui/paywall/ProTexts.kt` (`:59`): dạng "dài, có năm".
- Modify: `ui/split/DivisionInputs.kt` (`:35`), `ui/split/ResultContent.kt` (`:564`), `ui/split/SplitSheets.kt` (`:364`), `ui/editor/BillEditorContent.kt` (`:219-222`, `:341`), `ui/groups/AddToGroupSheet.kt` (`:168`): phần trăm, số lượng, tỉ giá và số tiền, cả khi hiện lẫn trong ô nhập.
- Modify: `app/build.gradle.kts` (khối `unitTests.all`, trong các dòng 174-184): cùng hai thuộc tính vùng, vì `:app` có bài JUnit thuần.

Kiểm thử có kỳ vọng đổi:
- `app/src/test/java/com/dantech/splitvibe/text/NumbersInTextsTest.kt:105-114`: trên máy đặt tiếng Ba Tư và tiếng Ả Rập, ba kỳ vọng "₫250,993", "$12.50" và "Nov 19, 2026" đổi thành kiểm tính chất: chỉ có chữ số 0 đến 9, có ký hiệu "₫" hoặc "$", dấu nhóm và dấu thập phân của vùng, ngày theo lớp thứ tự của vùng với tên tháng tiếng Anh. Các câu có số đếm giữ nguyên kỳ vọng; tên bài và lời chú thích của lớp đổi theo. Phần `:136-169`, không màn nào hiện chữ số khác 0 đến 9, giữ nguyên.
- `core/domain/src/test/kotlin/com/dantech/splitvibe/domain/BillCurrencyTest.kt:75-93`: đoạn đặt vùng mặc định `fa-IR` và `ar-EG` rồi chờ "₫250,993" và "1 USD = 25,350 VND" đổi thành kiểm tính chất như trên; các dòng truyền vùng rõ ràng giữ nguyên. Bảng đọc số gõ vào ở `:104-134` giữ nguyên.
- `export/PdfSummaryTest.kt:101`, `:122`, `data/payments/PaymentPayloadTest.kt:55`, `:60`, `ui/ComponentBehaviourTest.kt:209`, `:216` và `recurring/RecurringBillWorkerTest.kt:109-110`: chữ viết sẵn giữ nguyên, nay là dạng của vùng Mỹ chứ không còn là dạng duy nhất. Hai tệp giữa đưa chuỗi vào làm dữ liệu mẫu, không qua hàm định dạng.
- Tìm thấy khi đọc lại mã, bản rà không nêu: các bài chờ "1 December" (`assistant/SplitAssistantTest.kt`, `llm/GatewayBillAnalyzerTest.kt`, `ui/settings/AccountMessagesTest.kt`, `ui/split/SplitAssistantFlowTest.kt`) đổi thành "December 1"; các bài chờ "17 December 2026" (`ui/paywall/ProTextsTest.kt`, `ui/paywall/ProScreenTest.kt`) đổi thành "December 17, 2026".
- Giữ nguyên nhờ vùng mặc định en-US: `ui/split/PercentTextTest.kt`, `ui/recurring/RecurringViewModelTest.kt`, `ui/insights/InsightsViewModelTest.kt`, `ui/requests/RequestMoneyViewModelTest.kt`, và trong domain `SplitEditsTest.kt`, `ExchangeRateTest.kt`, `SplitCalculatorTest.kt`. `export/CsvExportTest.kt:29-36`, `:139` không liên quan, và hành trình trên máy gọi chính hàm định dạng của app (`e2e/JourneySteps.kt:112-114`).

Kiểm thử mới:
- Create: bảng kiểm thử trong `core/domain/src/test/kotlin/com/dantech/splitvibe/domain/` (ví dụ `RegionFormatsTest.kt`) cho tám vùng en-US, vi-VN, de-DE, fr-FR, hi-IN, ja-JP, ar-EG, fa-IR.
- Chuỗi chính xác cho en-US, vi-VN và de-DE: số tiền VND và USD, phần trăm, tỉ giá, bốn dạng ngày và "tháng và năm".
- Chỉ tính chất cho fr-FR, hi-IN, ja-JP, ar-EG và fa-IR, vì Java và từng bản Android mang dữ liệu vùng khác nhau: chỉ có chữ số 0 đến 9, có ký hiệu, đúng dấu nhóm và dấu thập phân của vùng. Không khẳng định cỡ nhóm, ký tự vô hình, hay khoảng trắng cạnh một mã như THB.
- Cho cả tám vùng: ba phần ghép lại bằng `format`; `parse(inputText(x))` trả lại `x` với tiền tệ không, hai và ba chữ số thập phân; lớp thứ tự ngày đúng như mục Key Insights; mã thanh toán mà `PaymentRequestBuilder` dựng cho cùng một yêu cầu không đổi theo vùng. Bảng khẳng định ngay từ đầu rằng vùng mặc định của máy chạy kiểm thử là en-US.
- Create: bài kiểm thử trên máy trong `app/src/androidTest/java/com/dantech/splitvibe/e2e/` (ví dụ `DateOrderOnDeviceTest.kt`), mô tả ở bước 1.
- Modify: `app/src/test/java/com/dantech/splitvibe/ui/ScreenGalleryTest.kt`: thêm bài vẽ bốn màn chính trên máy đặt vùng Việt Nam, theo mẫu của bài cỡ chữ lớn (`ScreenGalleryTest.kt:184-190`), với qualifier `vi-rVN-w412dp-h915dp-xhdpi` và hậu tố tên tệp `-vi-vn`, là tên mà giai đoạn 8 dùng khi ghi ảnh tham chiếu.

## Implementation Steps

1. [UNVERIFIED] Việc đầu tiên, để gỡ điều chưa kiểm: viết trong `:core:domain` hàm xếp một vùng vào ba lớp thứ tự, và bài kiểm thử trên máy khẳng định vi-VN là ngày trước, en-US là tháng trước, ja-JP là năm trước. Bài nằm trong gói `com.dantech.splitvibe.e2e`, vì lượt chạy theo mức API chỉ chạy gói đó. Đẩy nhánh; đọc kết quả API 34 từ lượt `device-tests.yml` tự chạy, và kết quả API 26 từ một lượt chạy tay có chọn `api-levels`. Ma trận của lượt chạy tay có `continue-on-error` (`device-tests.yml:152`), nên đọc kết quả của chính việc API 26. Nếu một vùng ra lớp khác, dừng lại và báo trước khi làm tiếp.
2. Thêm nguồn vùng vào `TextLocale.kt`: đọc vùng mặc định cho định dạng vào lúc gọi, ép chữ số Latinh, giữ lại `Locale` đã dựng cho tới khi vùng mặc định đổi, và trả về `TEXT_LOCALE` khi vùng của máy không dựng lại được, để một vùng lạ không làm sập mọi chỗ viết số tiền.
3. Sửa `BillCurrency`: `format` và `formatterFor` lấy nguồn vùng làm mặc định và đặt ký hiệu tiếng Anh của tiền tệ vào bộ định dạng; thêm hàm ba phần; `inputText` viết số không nhóm với dấu thập phân của vùng. Không đụng tới `parse`. Thêm vào bài kiểm thử trên máy của bước 1 hai khẳng định mà kiểm thử chạy trên JDK không chứng minh được cho ICU của máy: số tiền VND có ký hiệu đứng sau "902.664" ở vi-VN và đứng trước "902,664" ở en-US, và ở ar-EG chỉ có chữ số 0 đến 9.
4. Sửa các số khác trong domain: `ExchangeRate.describe`, `rateText`, `percentText`, `SplitEdits` và `PaymentRequestBuilder` lấy nguồn vùng làm mặc định; số lượng ở `SplitEdits.kt:333` dùng dấu thập phân của vùng.
5. Thêm các hàm ngày theo bảng của mục Requirements, định dạng bằng mẫu cố định với `TEXT_LOCALE` để tên tháng và chữ số không đổi theo vùng.
6. Đặt vùng mặc định en-US cho tác vụ kiểm thử của `:core:domain` và `:app`, rồi viết bảng kiểm thử tám vùng.
7. Sửa các tệp của app trong danh sách: `dateText`, `ClaimScreen`, `Exporter.monthName`, `AccountStore`, `ProTexts`, và năm chỗ viết phần trăm, số lượng, tỉ giá, số tiền trong ô nhập.
8. Sửa các bài kiểm thử có kỳ vọng đổi theo danh sách ở mục Files to Create / Modify.
9. Thêm vào `ScreenGalleryTest` bài vẽ bốn màn chính trên máy đặt vùng Việt Nam; trước khi chụp, bài khẳng định tổng hóa đơn được viết kiểu Việt Nam. Ảnh của nó đọc từ `app/build/outputs/roborazzi/`; ảnh tham chiếu được ghi ở giai đoạn 8 cùng các ảnh khác.
10. Chạy cổng kiểm thử của plan, nhờ một lượt review độc lập và sửa các phát hiện. Trên commit cuối, đọc lại kết quả của bài kiểm thử trên máy ở API 34 và, qua một lượt chạy tay nữa, ở API 26. Gửi chủ sản phẩm ảnh bốn màn chính ở vùng Việt Nam cùng ảnh Settings và Pro ở vùng Mỹ.

## Todo

- [ ] Hàm xếp lớp thứ tự ngày và bài kiểm thử trên máy; kết quả API 34 và API 26 được đọc và ghi lại
- [ ] Nguồn vùng trong `TextLocale.kt`
- [ ] `BillCurrency`: mặc định theo vùng, ký hiệu tiếng Anh, hàm ba phần, `inputText`; hai khẳng định về số tiền trong bài kiểm thử trên máy
- [ ] Phần trăm, số lượng, tỉ giá, câu của domain theo vùng
- [ ] Các hàm ngày theo ba lớp thứ tự
- [ ] Vùng mặc định en-US cho hai tác vụ kiểm thử; bảng kiểm thử tám vùng
- [ ] Các tệp của app trong danh sách
- [ ] Các bài kiểm thử có kỳ vọng đổi
- [ ] Bài vẽ bốn màn chính ở vùng Việt Nam trong `ScreenGalleryTest`
- [ ] Cổng kiểm thử, review, kết quả trên máy của commit cuối, ảnh gửi chủ sản phẩm

## Success Criteria

- Cổng kiểm thử của plan qua (với `-Pscreenshots=compare`), gồm bảng kiểm thử tám vùng, `BillCurrencyTest`, `NumbersInTextsTest`, `PdfSummaryTest`, `RecurringBillWorkerTest`, `ProTextsTest` và `PercentTextTest`.
- Bài kiểm thử trên máy qua trên API 34 và API 26, mỗi kết quả đọc từ chính việc của nó. Khi đó dấu [UNVERIFIED] được gỡ khỏi tệp này và dòng tương ứng trong plan được cập nhật.
- Với vùng mặc định vi-VN, số tiền là "902.664 ₫" và ngày ngắn là "13 Nov"; với en-US, "₫902,664" và "Nov 13".
- Trong `app/src/main` và `core/domain/src/main`, không chỗ định dạng ngày nào còn dùng `Locale.ENGLISH` hay `ofLocalizedDate`.
- `git status --porcelain app/src/test/screenshots` rỗng; ảnh gửi chủ sản phẩm lấy từ `app/build/outputs/roborazzi/*_actual.png`.

## Risk Assessment

- Dấu định hướng vô hình và dấu cách hẹp không ngắt ở vài vùng làm hỏng phép so chuỗi hoặc chỗ xuống dòng: năm vùng ngoài en-US, vi-VN, de-DE chỉ được kiểm tính chất, và ảnh vùng Việt Nam được xem lại ở giai đoạn 8.
- Ký hiệu đổi bên làm vỡ bố cục của số tiền lớn: `SvHeroAmount` nhận ký hiệu ở cả hai bên, và hàm ba phần được kiểm ở cả tám vùng.
- Thứ tự ngày trên máy khác bản Java: dấu hiệu là bài kiểm thử trên máy của bước 1 hỏng. Ứng phó: lớp app đọc thứ tự bằng `android.text.format.DateFormat.getDateFormatOrder` rồi truyền vào hàm của `:core:domain`, và ghi lại vùng cùng bản Android bị lệch.
- Một bản Android không tôn trọng phần mở rộng `nu-latn`, nên máy đặt tiếng Ả Rập hiện chữ số riêng: dấu hiệu là khẳng định về ar-EG trong bài kiểm thử trên máy hỏng. Ứng phó: đặt thẳng chữ số 0 vào `DecimalFormatSymbols` của bộ định dạng.
- Chữ viết sẵn trong kiểm thử đổi nghĩa theo máy chạy: hai tác vụ kiểm thử ghim en-US, và bảng kiểm thử khẳng định vùng mặc định ngay từ đầu.
- Người nhận thấy số tiền theo kiểu của máy người gửi, và "71.500 ₫" có thể bị một người quen kiểu Mỹ đọc thành bảy mươi mốt phẩy năm: với tin nhắn, đây là điều đã được chấp nhận. Với dòng "Amount: …" trong chi tiết thanh toán trên trang web, nó đứng cạnh số tiền mà trang tự viết; nếu chủ sản phẩm không muốn hai kiểu cạnh nhau, riêng dòng đó của `PaymentRequestBuilder` giữ `TEXT_LOCALE`.
- Câu "They come back on December 1." trên máy đặt vùng Mỹ khác bốn bảng đã nêu: chủ sản phẩm xem ảnh ở bước 10 trước khi giai đoạn đóng.

## Rollback

Hoàn lại các commit của giai đoạn chỉ dùng được khi giai đoạn 5 chưa bắt đầu; sau đó lỗi của giai đoạn này được sửa bằng commit mới, vì các giai đoạn sau đã dựng trên nó. Không có dữ liệu lưu hay lời gọi máy chủ nào đổi dạng: số gửi đi vẫn là đơn vị nhỏ nhất và mã tiền tệ.
