---
title: "Phase 7: Chỉnh bằng lời với AI, thống kê, xuất file, hóa đơn định kỳ"
status: in-progress
phase: 7
priority: P2
effort: "9d"
dependencies: [4]
---

# Phase 7: Chỉnh bằng lời với AI, thống kê, xuất file, hóa đơn định kỳ

## Overview

<!-- Updated: 16/11/2026 - chủ sản phẩm chốt bốn điểm khi bắt đầu phase -->
> **Đã chốt ngày 16/11/2026:**
> - Mỗi lần chỉnh bằng lời tính 1/5 lượt vào cùng hạn mức AI với lần đọc hóa đơn. Số lượt còn lại hiện kèm phần lẻ, ví dụ 2,4. Trọng số đặt trên server.
> - Chỉnh bằng lời dùng Claude Haiku 5.5 cho tới khi bộ đánh giá cho số liệu khác.
> - Loại chi tiêu là danh sách 9 loại cố định: Food & drink, Groceries, Transport, Lodging, Entertainment, Shopping, Utilities, Rent, Other.
> - Bộ đánh giá được làm và thử với model giả trong phase này. Lần chạy với key Anthropic thật để sau, khi chủ sản phẩm đưa key qua form bảo mật.

Sau khi có kết quả, người dùng gõ hoặc nói một câu như "Lan không uống bia" hay "thêm 10% tip" và app cập nhật cách chia. Người dùng xem được chi tiêu theo tháng, theo người, theo loại, xuất ra CSV hoặc PDF, và đặt hóa đơn lặp lại như tiền nhà.

Tính năng v1.0 thuộc giai đoạn này: chỉnh bằng lời với AI; thống kê, xuất file và hóa đơn định kỳ.

## Context

- Hiện tại câu lệnh chỉ dùng được một lần, lúc đọc hóa đơn (`app/src/main/java/com/dantech/splitvibe/ui/split/SplitBillViewModel.kt:102`). Muốn đổi ý phải chạm tay từng món.
- Cổng AI của Phase 3 và `BillEditor` của Phase 4 là hai nền để giai đoạn này xây lên.
- Chưa có thống kê, xuất file hay lịch lặp nào.

## Key Insights

- Nguyên tắc của POC được giữ nguyên: **AI đề xuất, máy tính tiền**. Model không trả số tiền của từng người; nó trả một danh sách thao tác có cấu trúc, còn `:core:domain` áp thao tác và tính lại.
- Lệnh chỉnh sửa ngắn và đầu vào đã có cấu trúc, nên có thể dùng model rẻ hơn lần đọc hóa đơn. Bộ đánh giá quyết định.
- Nhận giọng nói dùng bộ nhận dạng của Android trên máy; app chỉ nhận về văn bản rồi đi cùng đường với câu gõ tay.
- Thống kê tính trên máy từ dữ liệu đã đồng bộ; không cần server.

## Requirements

Chức năng:
- **Chỉnh bằng lời:** ô nhập và nút micro ở màn hình kết quả. App hiện các thay đổi AI đề xuất dưới dạng danh sách trước và sau; người dùng chấp nhận tất cả, bỏ từng mục, hoặc hoàn tác sau khi áp.
- **Thao tác được hỗ trợ:** đổi người chia một món, đổi cách chia, thêm hoặc bỏ người, thêm phụ phí hoặc tip theo phần trăm hay số tiền, đổi người trả, sửa giá hoặc số lượng một món.
- **Giọng nói:** nhận dạng trên máy khi có; xin quyền micro đúng lúc bấm; không có quyền thì vẫn gõ được.
- **Thống kê:** tổng chi theo tháng, theo người, theo nhóm, theo loại; loại do AI gợi ý lúc đọc hóa đơn và sửa được.
- **Xuất file:** CSV (hóa đơn, món, phần mỗi người) và PDF tóm tắt, qua bảng chia sẻ hoặc lưu vào tệp.
- **Hóa đơn định kỳ:** mẫu hóa đơn kèm lịch tuần hoặc tháng; tới kỳ thì app tạo bản nháp và nhắc.

Phi chức năng:
- Mỗi lần chỉnh bằng lời tính vào hạn mức AI theo trọng số riêng, cấu hình trên server.
- Mọi thao tác AI đề xuất đều phải qua kiểm tra của `BillEditor`; thao tác không hợp lệ bị loại và người dùng được báo.

## Architecture

<!-- Updated: Validation Session 5 - Firebase bỏ khỏi Phase 3; AI qua proxy trên Cloudflare -->
Proxy: đường `POST /v1/split-revision` trên Worker của Phase 3.
- Yêu cầu: hóa đơn hiện tại và cách chia hiện tại ở dạng có cấu trúc (không gửi lại văn bản OCR), câu lệnh, ngôn ngữ.
- Trả lời: danh sách thao tác theo schema cố định, mỗi thao tác có loại và tham số, kèm một câu giải thích ngắn.
- Dùng chung phần xác định người gọi, `requestId`, cách giữ chỗ rồi chốt lượt, giới hạn theo IP và trần chi phí với `bill-analysis` (Phase 3).

`:core:domain`:
- `SplitEdit` (kiểu đóng các thao tác) và `applyEdits(bill, plan, edits)` trả về trạng thái mới cùng danh sách thao tác bị loại.
- `Insights`: các phép tổng hợp thuần túy trên danh sách hóa đơn.
- `Recurrence`: tính kỳ kế tiếp từ lịch và ngày bắt đầu.

App:
- `VoiceInput` bọc `SpeechRecognizer`.
- `Exporter` ghi CSV và PDF (`android.graphics.pdf.PdfDocument`).
- `RecurringBillWorker` dùng WorkManager để tạo bản nháp và thông báo khi tới kỳ. <!-- Red Team: nơi ghi theo lịch không lũy đẳng — 2026-11-14 --> Bản nháp có mã cố định `{templateId}_{periodKey}` và chỉ được tạo khi chưa tồn tại, nên hai máy cùng tài khoản không tạo trùng.
- Loại chi tiêu: thêm trường `category` vào schema trả lời của `analyzeBill` (kiểu liệt kê cố định); `BillAnalysisParser` đọc trường mới, vắng thì coi là "khác".

Vết mở rộng (ranh giới cổng AI): `reviseSplit` là biến thể thứ hai của cùng một cổng. Phần dùng chung là xác thực, hạn mức, log và cách gọi model; phần riêng là prompt, schema và trọng số hạn mức. Không tách dịch vụ mới.

## Hợp đồng chung

<!-- Updated: 16/11/2026 - chốt trước khi chia việc cho domain, proxy và app -->
Domain, proxy và app làm song song, nên ba phần dùng chung đúng các định dạng dưới đây.

### Loại chi tiêu

| Giá trị trên đường truyền | Tên hiện trên màn hình |
|---|---|
| `food_and_drink` | Food & drink |
| `groceries` | Groceries |
| `transport` | Transport |
| `lodging` | Lodging |
| `entertainment` | Entertainment |
| `shopping` | Shopping |
| `utilities` | Utilities |
| `rent` | Rent |
| `other` | Other |

- Câu trả lời của `bill-analysis` có thêm trường bắt buộc `category`, là một trong các giá trị trên.
- `BillAnalysisParser` coi trường vắng hay giá trị lạ là `other`.
- Trong domain: `enum class BillCategory`, và `Bill.category` mặc định là `OTHER`, nên hóa đơn đã lưu không có trường này đọc ra `OTHER`.

### `POST /v1/split-revision`

Cách xác định người gọi, `requestId`, cách giữ chỗ rồi chốt lượt, giới hạn theo IP, trần chi phí, cách truyền câu trả lời (các dòng chờ rồi câu trả lời cuối) và các mã lỗi giống `POST /v1/bill-analysis`.

Thân yêu cầu:

```json
{
  "requestId": "<uuid>",
  "instruction": "Lan không uống bia",
  "bill": {
    "merchant": "Pho 24",
    "currency": { "code": "VND", "fractionDigits": 0 },
    "items": [{ "id": "i1", "name": "Bia", "quantity": "3", "amountMinor": 90000 }],
    "charges": [{ "id": "c1", "label": "Service charge", "kind": "service_charge", "amountMinor": 14000 }],
    "totalMinor": 154000
  },
  "split": {
    "participants": [{ "id": "me", "name": "Dana" }, { "id": "p1", "name": "Lan" }],
    "whole": null,
    "items": { "i1": { "type": "equal", "among": ["me"], "except": [] } },
    "notSharing": [],
    "payments": [{ "participantId": "me", "amountMinor": 154000 }]
  }
}
```

- `instruction` dài 1 đến 500 ký tự (tính theo code point). `merchant` có thể là null.
- `kind` của phụ phí là một trong `tax`, `service_charge`, `tip`, `discount`, `rounding`, `other`.
- `whole` là null khi chia theo món. Nếu không, nó là một cách chia, ở đúng dạng mà domain đang ghi `Division`:
  - `{"type":"equal","among":[...],"except":[...]}`;
  - `{"type":"shares","shares":{...}}`;
  - `{"type":"percent","basisPoints":{...}}`;
  - `{"type":"amount","amounts":{...}}`.
- `items` dùng cùng các dạng đó. Món vắng mặt trong `items` được mọi người chia đều. App chỉ gửi `items` khi `whole` là null, để model không thấy cách chia theo món đang không có hiệu lực.
- App làm sạch chữ trước khi gửi như với link chia sẻ: ký tự điều khiển thành khoảng trắng, bỏ ký tự định dạng vô hình trừ ZWJ và ZWNJ, cắt còn 200 ký tự. Câu lệnh luôn đi thành một dòng.
- Proxy cũng tự chuẩn hóa thay vì từ chối: mỗi đoạn khoảng trắng hay ký tự điều khiển thành một khoảng trắng; mã tiền tệ nhận chuỗi ngắn từ 0 đến 8 ký tự; con số của từng người trong cách chia có thể âm hay vượt tổng, vì người dùng có thể đang gõ dở. <!-- Updated: 16/11/2026 - theo review: app và proxy đều làm sạch, không bên nào phụ thuộc bên kia -->

Câu trả lời cuối:

```json
{
  "edits": [{ "op": "item_sharers", "itemId": "i1", "participantIds": ["me"] }],
  "explanation": "Lan no longer shares the beer.",
  "question": null
}
```

- `edits` có tối đa 20 thao tác.
- `explanation` dài tối đa 200 ký tự, viết bằng ngôn ngữ của câu lệnh.
- `question` là một câu hỏi lại ngắn khi câu lệnh quá mơ hồ để làm; khi đó `edits` rỗng.

Các thao tác:

| `op` | Trường | Nghĩa |
|---|---|---|
| `item_sharers` | `itemId`, `participantIds` (không rỗng) | Món được chia đều cho đúng những người này |
| `item_shares` | `itemId`, `shares` (số nguyên từ 1) | Món được chia theo phần |
| `split_mode` | `mode`: `equal`, `by_item`, `by_shares`, `by_percent`, `by_amount` | Chia cả hóa đơn theo cách này, bắt đầu từ chia đều |
| `whole_shares` | `shares` (số nguyên từ 0) | Cả hóa đơn chia theo phần |
| `whole_percent` | `basisPoints` (số nguyên từ 0, cộng lại đúng 10000) | Cả hóa đơn chia theo phần trăm |
| `whole_amounts` | `amounts` (số nguyên từ 0, cộng lại đúng tổng hóa đơn) | Cả hóa đơn chia theo số tiền |
| `add_person` | `ref` (`new1` đến `new9`), `name` | Thêm người; các thao tác sau dùng `ref` làm mã người |
| `remove_person` | `participantId` (không phải `me`) | Bỏ một người khỏi hóa đơn |
| `set_sharing` | `participantId`, `sharing` | Người này có tham gia mà không chia (`false`), hay lại chia (`true`) |
| `add_charge` | `kind`, `label`, và đúng một trong `amountMinor` (từ 1) hay `percentBasisPoints` (1 đến 10000) | Thêm phụ phí; phần trăm tính trên tổng các món; `discount` trừ đi |
| `set_payer` | `participantId` | Người này trả cả hóa đơn |
| `item_amount` | `itemId`, `amountMinor` (từ 0) | Sửa thành tiền của món |
| `item_quantity` | `itemId`, `quantity` (chuỗi số thập phân lớn hơn 0) | Sửa số lượng, giữ đơn giá khi biết |

Quy tắc áp thao tác, trong `:core:domain`:
- Các thao tác được áp theo thứ tự. Mỗi thao tác hoặc được áp, hoặc bị loại kèm lý do: món, người hay `ref` không tồn tại; số không hợp lệ; bỏ `me`; tổng không khớp.
- `item_sharers` và `item_shares`, khi hóa đơn đang chia cả hóa đơn, chuyển sang chia theo món trước, như `split_mode` `by_item`: mọi món khác được mọi người chia đều, kể cả món màn hình từng nhớ cách chia. `item_amount` và `item_quantity` giữ nguyên cách chia. <!-- Updated: 16/11/2026 - theo review -->
- `split_mode` sang đúng cách đang dùng thì không đổi gì.
- `add_charge` cộng vào số đã trả: khi hóa đơn có tổng in trên bill, tổng đó cũng tăng (hay giảm, với `discount`) đúng bằng phụ phí.
- `item_amount` và `item_quantity` theo `BillEditor`: tổng in trên bill giữ nguyên, phần chênh được chia theo tỷ lệ như hiện nay.
- Người trả đi theo tổng mới như `SplitPlan.withBill` đang làm.
- Domain viết câu mô tả bằng tiếng Anh cho từng thao tác, để màn hình hiện danh sách trước và sau.

### Hạn mức có trọng số

- `limit`, `used` và `remaining` trong `/v1/usage` và trong thân lỗi trở thành số thập phân, tối đa hai chữ số lẻ, ví dụ `3`, `0.2`, `2.8`.
- Một lần đọc hóa đơn tốn 1. Một lần chỉnh tốn `revisionWeight`, đặt là 0,2 trong `policy.ts`.
- Một lượt gọi chỉ chạy khi `remaining` còn ít nhất bằng trọng số của nó.
- Proxy giữ số nguyên bên trong, theo phần trăm của một lượt.
- App đọc các số này dưới dạng thập phân và hiện chúng, bỏ các số 0 thừa.

### Lịch định kỳ

- Một lịch là hằng tuần (cùng thứ với ngày bắt đầu) hay hằng tháng (cùng ngày với ngày bắt đầu).
- Ngày hằng tháng không có trong tháng thì lùi về ngày cuối tháng. Mỗi kỳ tính từ ngày bắt đầu, không từ kỳ trước, nên ngày 31 trở lại khi tháng có ngày 31.
- Bản nháp của mỗi kỳ có mã là UUID dựng từ tên `{templateId}_{ngày của kỳ theo ISO}` (`Recurrence.draftId`), và chỉ được tạo khi chưa có. <!-- Updated: 16/11/2026 - proxy chỉ nhận mã hóa đơn dạng UUID khi hóa đơn vào nhóm, vào yêu cầu trả tiền hay lên link -->

## Files

Tạo mới:
- `proxy/src/split-revision.ts`, `proxy/src/revise-prompt.ts`, `proxy/eval/revise/` (bộ câu lệnh đánh giá)
- Trong `core/domain/`: `SplitEdit.kt`, `Insights.kt`, `Recurrence.kt` cùng test
- `app/src/main/java/com/dantech/splitvibe/assistant/SplitAssistant.kt`, `VoiceInput.kt`
- `app/src/main/java/com/dantech/splitvibe/ui/insights/`, `ui/recurring/`
- `app/src/main/java/com/dantech/splitvibe/export/Exporter.kt`
- `app/src/main/java/com/dantech/splitvibe/recurring/RecurringBillWorker.kt`

Sửa:
- `app/src/main/java/com/dantech/splitvibe/ui/split/ResultContent.kt` (ô chỉnh bằng lời, danh sách đề xuất)
- `proxy/src/schema.ts`, `proxy/src/prompt.ts` (trường `category`)
- `BillAnalysisParser.kt` trong `:core:domain` và test của nó
- Mẫu hóa đơn định kỳ nằm trên máy, nên không có gì để xóa trên proxy
- `app/src/main/AndroidManifest.xml` (quyền `RECORD_AUDIO`)

## Implementation Steps

1. Viết `SplitEdit`, `applyEdits` và test: mỗi thao tác giữ nguyên các bất biến về tiền của Phase 4.
2. Soạn bộ đánh giá ít nhất 40 câu lệnh (tiếng Anh, tiếng Việt và vài ngôn ngữ khác) kèm thao tác mong đợi; viết `reviseSplit`; chọn model theo số liệu.
3. Giao diện đề xuất trước và sau, chấp nhận, bỏ, hoàn tác; nối `VoiceInput`.
4. Thêm `category` vào schema, bộ đọc và fixture; `Insights` và màn hình thống kê.
5. `Exporter` cho CSV và PDF với test so tệp mẫu.
6. `Recurrence`, màn hình hóa đơn định kỳ, `RecurringBillWorker`, test với đồng hồ giả.
7. Ảnh chuẩn cho màn hình mới.

## Todo

- [x] `SplitEdit` và `applyEdits` với test bất biến
- [x] Bộ đánh giá câu lệnh chỉnh sửa và `reviseSplit`
- [x] Giao diện đề xuất, chấp nhận, bỏ, hoàn tác
- [x] Nhập bằng giọng nói
- [x] Trường `category`, `Insights`, màn hình thống kê
- [x] Xuất CSV và PDF
- [x] Hóa đơn định kỳ với WorkManager
- [x] Ảnh chuẩn cho màn hình mới

## Kết quả thực hiện

<!-- Updated: thực hiện ngày 16 và 17/11/2026 trên nhánh feat/phase-07-ai-assistant-insights, chưa merge vào main -->
Bảy trên tám tiêu chí đạt và có bằng chứng. Tiêu chí về bộ đánh giá chưa đạt: bộ câu lệnh và công cụ chạy đã xong và đã thử với model giả, còn lần chạy với key Anthropic thật để sau, theo quyết định của chủ sản phẩm.

| Tiêu chí | Kết quả | Bằng chứng |
|---|---|---|
| Áp bất kỳ dãy thao tác hợp lệ nào, tổng các phần vẫn bằng tổng hóa đơn; thao tác trỏ tới món hay người không tồn tại bị loại và được báo | Đạt | `SplitEditPropertyTest`: 1.500 hóa đơn và cách chia ngẫu nhiên, mỗi cái tới 20 thao tác, cả số lượng rất nhỏ và số tiền rất lớn. Sau mỗi thao tác, cách chia không có vấn đề thì các phần cộng đúng tổng và không âm; thao tác bị loại không đổi gì; món, người hay `ref` lạ bị loại kèm lý do |
| Model được chọn ra đúng dãy thao tác ở ít nhất 90% câu lệnh; báo cáo ghi độ chính xác, độ trễ và chi phí từng model | Chưa đạt | `proxy/eval/revise/` có 60 câu (27 tiếng Việt, 18 tiếng Anh, 5 tiếng Thái, 5 tiếng Indonesia, 5 tiếng Tây Ban Nha), chấm điểm và công cụ chạy (`npm run eval:revise`). Mỗi câu chạy ba lần cho mỗi model. Đã thử với model giả; lần chạy thật cần key Anthropic |
| "Lan không uống bia" trên hóa đơn mẫu bỏ Lan khỏi món bia, phần của ba người cập nhật đúng; hoàn tác trả lại trạng thái cũ | Đạt | `SplitAssistantFlowTest` dùng view model thật: phần mỗi người từ 84.000 thành 99.750 / 99.750 / 52.500, đúng kết quả của `SplitCalculator`; hoàn tác trả lại hóa đơn y như trước, kể cả trạng thái đã xong |
| Câu lệnh chứa chỉ dẫn lạ không tạo ra trạng thái vi phạm bất biến | Đạt | Domain loại số âm, phần trăm quá 100%, tổng không khớp và số tràn. App không áp gì từ câu trả lời độc hại và hiện lý do. Proxy ràng câu trả lời bằng schema, kiểm lại, và mã hóa mọi chữ nhúng vào prompt để tên không giả được thẻ. Bộ đánh giá có 6 câu tấn công |
| Từ chối quyền micro: ô gõ vẫn dùng được, không có lỗi | Đạt | `VoiceInputTest` và `SplitAssistantFlowTest`. Quyền chỉ được hỏi khi bấm micro; máy không có bộ nhận dạng thì không hiện nút micro |
| Thống kê tháng trên fixture 20 hóa đơn khớp bảng tính tay | Đạt | `InsightsTest` (domain) có bảng tính tay, reviewer đã tính lại từng số. `InsightsViewModelTest` kiểm phần app từ hóa đơn đã lưu tới màn hình, với ba kỳ và bảng tính tay |
| Tệp CSV và PDF xuất từ fixture khớp tệp mẫu; CSV mở bằng bảng tính với đúng dấu thập phân | Đạt | `CsvExportTest`: so từng byte với tệp mẫu ở cả hai quy ước, và đọc lại từng số như bảng tính ở `en-US` và `vi-VN`. `PdfSummaryTest`: so chữ thực sự vẽ ra và ảnh từng trang với tệp mẫu. Một test trên máy ảo kiểm tệp PDF thật |
| Với đồng hồ giả, hóa đơn hằng tháng tạo đúng một bản nháp mỗi kỳ, kể cả tháng thiếu ngày và khi hai máy cùng chạy | Đạt | `RecurringDraftsTest`: một hóa đơn ngày 31 chạy mỗi ngày từ 1/2027 tới 4/2028 tạo đúng một bản nháp mỗi kỳ, đúng ngày. Hai lượt chạy cùng lúc, 20 vòng, mỗi bản nháp vẫn chỉ một. Mẫu hóa đơn định kỳ nằm trên máy nên không đồng bộ giữa hai máy; mã bản nháp là UUID dựng từ mẫu và ngày, nên cùng một mẫu luôn cho cùng một mã |

Kiểm tra chung: 543 unit test Kotlin (app 342, domain 198, hệ thiết kế 3), 357 test proxy và 192 test trang web đều qua; ktlint, lint, `tsc` và bản release đều sạch. Checksum của WorkManager do workflow trên máy sạch dựng, khớp từng byte với tệp đã commit. Test trên máy ảo Android qua ở `0ebc0de`.

Ngày 17/11/2026, 73 commit từ `f478f46` trở đi được ký lại, vì sáu commit của phần AI trong app thiếu chữ ký nên GitHub sẽ ghi là chưa xác minh. Hash của các commit này đổi, còn cây mã, tác giả và message giữ nguyên. Các hash trong plan và các báo cáo đã đổi theo. Các lượt CI và gói triển khai nêu ở đây dùng hash cũ, cùng cây mã với hash mới.

Những gì đã làm:
- **Domain:** `BillCategory`, 13 thao tác `SplitEdit` với `SplitEdits.apply` và `describe`, bộ đọc câu trả lời, `SplitRevisionRequest`, `Insights`, `Recurrence`. Các quy tắc làm sạch chữ của proxy nay ở một chỗ (`ProxyText`), dùng chung cho link, yêu cầu trả tiền và chỉnh sửa.
- **Proxy:** `POST /v1/split-revision` trên Haiku 5.5; hạn mức tính theo phần trăm của một lượt, chỉnh một lần tốn 0,2; trường `category` trong lần đọc hóa đơn; bộ đánh giá. Hợp đồng ở [docs/proxy.md](../../docs/proxy.md).
- **App:**
  - ô chỉnh bằng lời trên màn kết quả, với danh sách thay đổi trước và sau, bỏ từng mục, áp và hoàn tác;
  - nhập bằng giọng nói;
  - số lượt hiện kèm phần lẻ;
  - chọn loại trong màn sửa;
  - màn thống kê, xuất ba tệp CSV hay một PDF;
  - hóa đơn định kỳ với WorkManager và thông báo.
  Cơ sở dữ liệu lên phiên bản 3. Kiến trúc ở [docs/architecture.md](../../docs/architecture.md).
- **Ba bản review độc lập**, đã sửa hết:
  - proxy: 1 lỗi nặng, 1 trung bình, 8 nhẹ ([báo cáo](../reports/code-reviewer-261116-1448-phase-7-proxy-review.md));
  - domain và phần AI của app: 2 nặng, 3 trung bình, 9 nhẹ ([báo cáo](../reports/code-reviewer-261116-1448-phase-7-domain-ai-app-review.md));
  - thống kê, xuất file và hóa đơn định kỳ: 2 nặng, 5 trung bình, 11 nhẹ ([báo cáo](../reports/code-reviewer-261116-1448-phase-7-insights-export-recurring-review.md)).
  Vài lỗi nhẹ được giữ có chủ ý, kèm lý do trong các báo cáo sửa, như tốc độ đọc lại hóa đơn của màn thống kê và cách chia trang PDF.

Triển khai lên Cloudflare ngày 17/11/2026:
- **Worker `split-vibe-proxy`:** gói dựng bằng đúng lệnh của CI (`npm run bundle`) từ commit `3d9e8af`. Không có migration D1 nào. Các `QuotaBook` tự đổi số lượt cũ sang phần trăm ở lần dùng đầu, và vẫn ghi kèm số lượt nguyên để bản cũ đọc được nếu phải lùi phiên bản. Các binding, biến, secret và lịch cron giữ nguyên.
- **Thử bản đang chạy:**
  - `/v1/usage` trả số lượt dạng số;
  - `/v1/split-revision` trả 503 `not_configured`, vì Worker chưa có key Anthropic;
  - trang `/s/`, tỷ giá và `sign_in_off` vẫn như trước.
- Một Worker và một KV namespace tạm dùng để chuyển gói lên đã bị xóa.

Các mặc định đã chọn, chủ sản phẩm có thể đổi:
- Lần chỉnh bị model từ chối hay quá dài vẫn tính 0,2 lượt, như lần đọc hóa đơn. Câu trả lời không đọc được thì được trả lại lượt.
- Khoản giữ chỗ chi phí của một lần chỉnh tính theo giá của chính model đó: khoảng 0,006 đến 0,013 USD thay vì 0,28 USD.
- Tỷ giá trong ngày được lưu vào hóa đơn khi người dùng hoàn tất, hay khi chọn lại tỷ giá trong ngày trên hóa đơn đã xong. Mở hóa đơn ra vẫn không ghi lại gì.
- Hóa đơn không có ngày được tính theo ngày lưu. Thống kê phân biệt người theo tên đã chuẩn hóa, và nhóm theo mã.
- Lịch định kỳ neo theo ngày của hóa đơn, hay ngày người dùng chọn cho bản nháp đầu. Người vào từ link và cảnh báo của lần đọc không vào mẫu. Bản nháp đã xóa không tạo lại.
- Sau một thời gian dài, mỗi hóa đơn định kỳ chỉ có một thông báo. Thông báo hiện tên khi máy đã mở khóa; trên màn hình khóa chỉ báo có hóa đơn tới kỳ.
- Đề xuất của trợ lý mất khi hệ thống đóng app; hỏi lại tốn thêm 0,2 lượt.

Còn lại trước khi đóng phase:
- Đặt key Anthropic, rồi chạy bộ đánh giá (`npm run eval:revise`, khoảng 540 lần gọi) để chọn model chỉnh sửa và kiểm schema với API thật. Nên chạy trước khi bật chỉnh bằng lời cho người dùng.
- Đặt `GOOGLE_CLIENT_ID` để thử trọn trên bản thật.

## Success Criteria

- Test bất biến: áp bất kỳ dãy thao tác hợp lệ nào, tổng các phần vẫn bằng tổng hóa đơn; thao tác trỏ tới món hoặc người không tồn tại bị loại và được báo.
- Bộ đánh giá: model được chọn cho ra đúng dãy thao tác ở ít nhất 90% câu lệnh; báo cáo ghi độ chính xác, độ trễ và chi phí từng model.
- Câu "Lan không uống bia" trên hóa đơn mẫu bỏ Lan khỏi món bia và phần của ba người cập nhật đúng; hoàn tác trả lại trạng thái cũ.
- Câu lệnh chứa chỉ dẫn lạ (ví dụ đòi đặt số tiền âm cho một người) không tạo ra trạng thái vi phạm bất biến.
- Từ chối quyền micro: ô gõ vẫn dùng được, không có lỗi.
- Thống kê tháng trên fixture 20 hóa đơn khớp bảng tính tay.
- Tệp CSV và PDF xuất từ fixture khớp tệp mẫu; CSV mở được bằng phần mềm bảng tính với đúng dấu thập phân.
- Với đồng hồ giả, hóa đơn định kỳ hằng tháng tạo đúng một bản nháp mỗi kỳ, kể cả tháng thiếu ngày và kể cả khi hai máy cùng tài khoản cùng chạy.

## Risks

| Rủi ro | Dấu hiệu nhận biết | Phản ứng đã định |
|---|---|---|
| Model hiểu sai câu lệnh mơ hồ | Bộ đánh giá dưới 90% | Model trả câu hỏi lại thay vì đoán; người dùng luôn duyệt trước khi áp |
| Nhận dạng giọng nói kém với tên riêng | Người thử phải sửa nhiều | Hiện văn bản nhận được để sửa trước khi gửi |
| WorkManager bị hệ thống trì hoãn | Bản nháp tới trễ | Khi mở app, kiểm tra và tạo bù các kỳ đã lỡ |
| Chi phí AI tăng vì chỉnh sửa nhiều lần | Số lượt `reviseSplit` mỗi hóa đơn cao | Trọng số hạn mức riêng, model rẻ, gộp nhiều thay đổi trong một câu |

## Security

- Câu lệnh và tên người là dữ liệu không tin cậy với model: đầu ra bị ràng bằng schema và mọi thao tác đi qua `applyEdits`.
- Âm thanh không rời máy khi nhận dạng trên máy có sẵn; nếu máy chỉ có nhận dạng qua mạng, app báo trước cho người dùng.
- Tệp xuất chỉ ghi vào nơi người dùng chọn hoặc đi qua bảng chia sẻ.

## Next

Phase 8 đặt hạn mức khác nhau cho bản miễn phí và Pro trên cả `analyzeBill` lẫn `reviseSplit`.
