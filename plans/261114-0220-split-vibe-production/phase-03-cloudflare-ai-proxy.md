---
title: "Phase 3: Proxy AI trên Cloudflare"
status: in_progress
phase: 3
priority: P1
effort: "10d"
dependencies: [1]
---

# Phase 3: Proxy AI trên Cloudflare

<!-- Updated: Validation Session 5 - AI gọi qua proxy trên Cloudflare như skim-browser; Firebase bỏ khỏi Phase 3 -->

## Overview

App chạy được ở chế độ production mà không có API key nào trên máy. Hóa đơn được phân tích qua một proxy trên Cloudflare (một Worker), làm theo cách proxy của dự án skim-browser. Proxy giữ key Anthropic, dựng prompt và schema, tính lượt theo hạn mức 3 / 10 / 300, chặn lạm dụng theo IP và bằng trần chi phí theo ngày, và không ghi lại nội dung hóa đơn. Người dùng thử được ngay mà không cần đăng nhập; đăng nhập Google thì có hạn mức hằng tháng. Giai đoạn này không dùng Firebase.

## Context

- Hiện tại key Anthropic nằm trên máy: `app/build.gradle.kts` nhúng `DEFAULT_ANTHROPIC_API_KEY` vào bản debug, `data/settings/SettingsStore.kt` mã hóa và lưu key người dùng nhập, và `llm/ClaudeBillAnalyzer.kt` gọi thẳng API từ điện thoại. Cách này chỉ chấp nhận được cho POC.
- Luồng phân tích bị chặn khi không có key ở bốn nơi: `ui/split/SplitBillViewModel.kt` (`analyze`), `ui/split/NewSplitContent.kt`, `ui/split/SplitScreen.kt` và `ui/AppNavHost.kt` (đều dưới `app/src/main/java/com/dantech/splitvibe/`). Bộ phân tích được lấy qua `analyzerFor(AppSettings)` trong module Koin.
- Mẫu tham khảo: thư mục `proxy/` và `docs/proxy.md` của `dantech0xff/skim-browser`. Worker viết bằng TypeScript; key là secret của Worker; bộ đếm nằm trong Durable Objects; giới hạn theo IP đã băm kèm muối; trần chi phí theo bản cài, theo IP và toàn cục được giữ chỗ trước khi gọi rồi quyết toán theo số token thật; không có Play Integrity.
- Việc còn treo từ POC: yêu cầu thật của app chưa từng được API chấp nhận. Giai đoạn này đóng khoảng trống đó bằng một lần gọi thật qua proxy.

## Key Insights

- **Durable Objects giữ bộ đếm.** Mọi yêu cầu của cùng một người gọi chạy trên cùng một object, nên hai yêu cầu không thể cùng lấy lượt cuối. KV nhất quán muộn nên không dùng được cho việc này.
- **Mã cài đặt tạo lại được** bằng cách xóa dữ liệu app, nên lượt dùng thử theo bản cài không phải là trần cứng. Giới hạn theo IP và trần chi phí theo ngày mới là thứ chặn chi phí, như ở skim-browser. Hạn mức hằng tháng gắn với tài khoản Google, thứ không tạo lại miễn phí được.
- **Proxy tự kiểm ID token của Google** bằng khóa công khai của Google (chữ ký, `iss`, `aud` là web client ID của app, hạn dùng), rồi cấp một phiên do chính proxy ký. App gửi phiên đó ở các yêu cầu sau, nên không cần xin token Google mới cho mỗi lần đọc.
- **Worker tính tiền theo thời gian CPU**, không theo thời gian chờ model. Trong lúc chờ, proxy gửi đều đặn một dòng "đang chờ" để mạng di động và client HTTP không cắt kết nối.
- **Không có Play Integrity hay App Check**, như skim-browser. Lạm dụng bị chặn bằng hạn mức, giới hạn theo IP và trần chi phí; trần chi tiêu trong console của Anthropic vẫn đặt riêng.
- Ước lượng chi phí mỗi lần đọc hóa đơn (khoảng 5 nghìn token vào, 2 nghìn token ra): Opus 5.5 khoảng 0,06 USD, Sonnet 5.5 khoảng 0,03 USD, Haiku 5.5 khoảng 0,002 USD `[UNVERIFIED]`. Bộ đánh giá bên dưới đo số thật.

## Requirements

Chức năng:
- Proxy có bốn đường: `POST /v1/bill-analysis` (giao kèo bên dưới), `POST /v1/session` (ID token Google đổi lấy phiên), `GET /v1/usage` (số lượt còn lại), `DELETE /v1/account` (xóa mọi thứ proxy giữ về tài khoản).
- App thử được ngay không cần đăng nhập; trong Settings có đăng nhập Google qua Credential Manager, đăng xuất, xóa tài khoản và số lượt còn lại. Chính sách của Play đòi xóa tài khoản ngay trong app khi app có tài khoản.
- Công tắc trên proxy: tắt AI (app chuyển sang nhập tay) và phiên bản app tối thiểu.
- Bộ đánh giá chất lượng để chọn model bằng số liệu: dùng model rẻ nhất đạt ngưỡng.

<!-- Updated: Validation Session 1 - ranh giới miễn phí và Pro; Validation Session 5 - giữ ba mức khi chuyển sang Cloudflare -->
Hạn mức lượt AI (giá trị mặc định, đổi được trong cấu hình của proxy):

| Người gọi | Hạn mức | Chu kỳ |
|---|---|---|
| Chưa đăng nhập (theo mã cài đặt) | 3 lượt dùng thử | Một lần cho mỗi bản cài |
| Đã đăng nhập Google | 10 lượt | Mỗi tháng, tính theo giờ UTC |
| Pro (Phase 8) | 300 lượt, theo nguyên tắc dùng hợp lý | Mỗi tháng, tính theo giờ UTC |

<!-- Red Team: hạn mức lách được và trừ, hoàn lượt không an toàn — 2026-11-14 -->
Quy tắc tính lượt:
- Mỗi lần phân tích mang một `requestId` do client sinh. Proxy **giữ chỗ** một lượt theo `requestId` trước khi gọi model và **chốt** khi model đã trả về kết quả.
- Một lượt bị tính khi model đã trả về trọn câu trả lời, bất kể câu trả lời nói gì (kể cả "không phải hóa đơn", model từ chối, hay câu trả lời bị cắt ở giới hạn token) và kể cả khi client đã ngắt kết nối, miễn là câu trả lời đến trong vòng 25 giây sau lúc ngắt. Lượt được nhả khi model không trả về trọn câu trả lời: lỗi mạng, lỗi 5xx hoặc 429 của model, hết giờ chờ, luồng trả lời đứt giữa chừng, hay quá 25 giây sau khi client ngắt. Không có câu trả lời trọn vẹn thì client cũng không nhận được gì, nên việc nhả lượt không mở đường lách hạn mức.
- Vì sao là 25 giây: runtime của Workers chỉ giữ việc thêm 30 giây sau khi client ngắt rồi hủy nó. Proxy tự dừng model trước mốc đó để kịp nhả lượt, quyết toán chi phí và ghi log (kết quả `client_gone`). Muốn tính lượt cả với lần đọc kéo dài hơn thì phải chuyển lời gọi model ra khỏi yêu cầu (Queue hoặc Durable Object); hiện chưa làm.
- Gọi lại với cùng `requestId` không bị tính thêm lượt; mỗi `requestId` được chạy lại tối đa 2 lần. Mỗi lần chạy chỉ trả phần giữ chỗ của mình; lượt chỉ được nhả khi không còn lần chạy nào của yêu cầu đó đang chờ model.
- `requestId` gắn với một mã băm có muối của nội dung yêu cầu (văn bản OCR, câu lệnh, tiền tệ), giữ 24 giờ cùng `requestId`. Gửi lại cùng `requestId` cho một hóa đơn khác thì bị từ chối (400 `input`), để một lượt đã tính không đọc được nhiều hóa đơn. Mã băm không cho biết nội dung hóa đơn, như mã băm IP.
- Chỗ giữ quá 5 phút chưa chốt thì tự hết hạn và coi như đã nhả.

Chặn lạm dụng (theo skim-browser, số khởi đầu nằm trong `proxy/src/policy.ts`):
- Giới hạn số yêu cầu mỗi phút và mỗi ngày theo IP. IP lấy từ `CF-Connecting-IP`, băm kèm muối trước khi dùng làm tên object, và không được lưu. Địa chỉ IPv6 được tính theo khối /64, vì một thuê bao giữ cả khối đó và điện thoại đổi địa chỉ trong khối; địa chỉ IPv4, kể cả khi viết trong IPv6, tính theo chính nó.
- Trần chi phí trong mỗi khung 24 giờ: theo người gọi, theo IP và cho toàn proxy. Trước khi gọi model, proxy giữ chỗ chi phí tối đa có thể (độ dài prompt tính bằng byte UTF-8 cộng `max_tokens`, theo giá đắt nhất trong bảng giá); sau khi xong thì thay bằng chi phí thật theo số token của từng lần thử, kể cả lần chạy lại trên model dự phòng khi model chính từ chối. Model không có trong bảng giá bị tính theo giá niêm yết cao nhất (10 USD vào, 50 USD ra mỗi triệu token) và được ghi cảnh báo; suy luận giữ trong một vùng (khác `global`) tính 110% giá niêm yết. Mức giữ chỗ chỉ phủ một lần thử, nên một lượt chạy lại trên model dự phòng có thể vượt trần một chút. Lỗi trước khi model bắt đầu thì hoàn lại cả; model đã bắt đầu rồi mới lỗi thì giữ nguyên cả phần đã giữ chỗ, vì không biết đã bị tính bao nhiêu.
- Bị chặn thì trả 429 với mã lỗi và `Retry-After`.

Phi chức năng:
- App không chứa key Anthropic và không chứa lớp nào của Anthropic SDK, ở mọi biến thể build.
- Proxy không ghi nội dung hóa đơn, câu lệnh, tên người, mã cài đặt hay IP; tracing tắt để không giữ thân yêu cầu.
- Mọi lần gọi ghi lại model, số token, độ trễ và kết quả để tính chi phí.

## Architecture

```
Điện thoại                                      Cloudflare Worker `proxy/` (TypeScript)
 ML Kit OCR (trên máy)
 GatewayBillAnalyzer ── HTTPS ───────────────▶ POST /v1/bill-analysis
   X-SplitVibe-Install: mã cài đặt (UUID)        1. kiểm đầu vào, công tắc AI, phiên bản tối thiểu
   Authorization: Bearer <phiên> nếu đã           2. xác định người gọi: phiên Google hoặc mã cài đặt
   đăng nhập; requestId                           3. giới hạn theo IP; giữ chỗ chi phí theo người gọi, IP, toàn cục
   (hạn chờ phía client 150 giây)                4. giữ chỗ lượt theo requestId (Durable Object của người gọi)
                                                 5. dựng prompt và schema, gọi Anthropic (key là secret của Worker)
   ◀── NDJSON: {"type":"wait"} … {"type":"result"}  6. chốt hoặc nhả lượt, quyết toán chi phí; log số liệu, không log nội dung
 Đăng nhập Google (Credential Manager) ──────▶ POST /v1/session: kiểm ID token của Google, cấp phiên do proxy ký
 Settings ───────────────────────────────────▶ GET /v1/usage, DELETE /v1/account
```

Durable Objects:
- `QuotaBook`: một object cho mỗi người gọi (`install:<uuid>` hoặc `google:<sub băm>`), giữ số lượt đã chốt và các chỗ giữ theo `requestId`.
- `AbuseLedger`: giới hạn theo IP và trần chi phí theo người gọi, IP, toàn cục. Object hết khung thời gian thì tự xóa.

<!-- Updated: Phase 3 step 2 - bỏ `locale` khỏi giao kèo vì prompt không dùng; ghi rõ mã lỗi và dòng lỗi có `usage` -->
Giao kèo `bill-analysis` phiên bản 1 (mẫu: `app/src/sharedTest/resources/fixtures/sample_bill_request.json`, đúng thân yêu cầu app gửi cho hóa đơn mẫu):
- Header: `X-SplitVibe-Install: <uuid>` và `X-SplitVibe-Version: <versionCode>`; sau khi đăng nhập thêm `Authorization: Bearer <phiên>`. Có phiên thì phiên quyết định người gọi; phiên hỏng hoặc hết hạn bị từ chối (401 `session`) chứ không bị coi như bản cài, để người đã đăng nhập không bị chuyển sang lượt dùng thử mà không biết.
- Thân yêu cầu: `schemaVersion` (là 1), `requestId` (UUID), `ocrDocument` (đúng chuỗi `OcrLayoutEncoder.encode` viết, trong thẻ `<bill_ocr>`, tối đa 20.000 ký tự), `instruction` (tối đa 1.000 ký tự, rỗng khi người dùng không gõ gì), `localCurrency` (mã ISO 4217 viết hoa, hoặc rỗng). Trường lạ bị bỏ qua; thân yêu cầu tối đa 256 KiB.
- Trả lời 200, dạng NDJSON: một dòng `{"type":"wait"}` ngay khi bắt đầu và cứ 10 giây một dòng trong lúc chờ, rồi một dòng `{"type":"result","answer":…,"usage":…}`, trong đó `answer` đúng JSON mà `BillAnalysisParser` đang đọc và `usage` là `{limit, used, remaining, period, resetsAt}` sau lượt này; hoặc một dòng `{"type":"error","code":…}`. Mã của dòng lỗi: `refused` (model từ chối), `too_long` (câu trả lời bị cắt ở giới hạn token), `bad_response` (câu trả lời không đọc được); ba mã này đã bị tính lượt nên dòng lỗi kèm `usage`. Còn `upstream` (model lỗi), `busy` (model quá tải hoặc bị giới hạn, kể cả giữa chừng, và khi API bảo chờ quá 10 giây thì proxy không chờ), `timeout` (quá 120 giây) và `internal` thì không bị tính lượt. Sau dòng kết quả hay dòng lỗi thì không có dòng nào nữa.
- Lỗi trước khi bắt đầu trả lời: JSON `{"error":code}` với 400 `input` (đầu vào, hoặc `requestId` dùng lại cho hóa đơn khác), 401 `install` (mã cài đặt) hoặc `session` (phiên), 426 (app quá cũ), 429 (kèm `Retry-After` khi có thời điểm thử lại) với `quota_exhausted` (hết lượt, kèm `usage`), `retry_limit` (một `requestId` đã chạy đủ 3 lần), `rate_limited` (giới hạn theo IP), `spend_limited` (trần chi phí của người gọi hoặc của IP), `busy` (trần chi phí toàn proxy), 503 `ai_off` (AI đang tắt) và 503 `not_configured` (proxy thiếu muối băm hay key Anthropic).
- App ánh xạ mã lỗi vào `BillAnalyzerException.Kind`, thêm các loại hết lượt, chưa xác thực, AI đang tắt và cần cập nhật app.

Các đường còn lại:
- `POST /v1/session` với `{"idToken": …}` (ID token Google cấp cho app lúc đăng nhập) trả `{session, expiresAt, usage}`. Token phải do Google ký (khóa lấy từ `https://www.googleapis.com/oauth2/v3/certs`), `iss` là Google, `aud` là `GOOGLE_CLIENT_ID`, còn hạn; sai thì 401 `id_token`. Khi không lấy được khóa của Google (hết giờ, lỗi mạng) thì token không phán được: 503 `sign_in_off` và ghi log, chứ không báo token sai. Phiên là JWT HS256 do proxy ký, hạn 30 ngày, `sub` là mã băm có muối của `sub` Google. Khi chưa đặt `GOOGLE_CLIENT_ID` hoặc khóa ký ngắn hơn 32 byte thì 503 `sign_in_off`.
- `GET /v1/usage` trả `{usage, aiEnabled}` cho bản cài hoặc tài khoản đang gọi.
- `DELETE /v1/account` (cần phiên) xóa bộ đếm lượt và bộ đếm chi phí của tài khoản, trả 204.
- Công tắc là biến trong `wrangler.jsonc`: `AI_ENABLED` (chỉ đúng `"true"` mới bật, mọi giá trị khác là tắt) và `MIN_APP_VERSION` (yêu cầu thiếu header phiên bản bị coi là phiên bản 0). Cả hai chỉ chặn `bill-analysis`; đăng nhập, xem lượt và xóa tài khoản luôn chạy, kể cả với app cũ.

Prompt (`llm/BillPrompt.kt`) và schema (`llm/BillSchema.kt`) chuyển sang `proxy/src/prompt.ts` và `proxy/src/schema.ts` để sửa được mà không cần phát hành app. `BillAnalysisParser` nằm trong `:core:domain` và không đổi, nên các test của nó tiếp tục bảo vệ giao kèo.

<!-- Red Team: bản release không phân tích được sau khi bỏ key — 2026-11-14 -->
Thay cổng chặn `hasApiKey` bằng trạng thái "AI khả dụng": bản build có địa chỉ proxy, và proxy không báo AI đang tắt. `AppSettings` chỉ còn tiền tệ mặc định; model do proxy chọn. `ClaudeBillAnalyzer`, các test của nó và thư viện Anthropic Java bị **xóa khỏi app**. Phương án giữ chúng trong source set `debug` đã bị loại, vì chính nó gây ra lỗi "bản debug chạy được còn bản release thì không".

Địa chỉ proxy và web client ID của Google đến từ `local.properties` (`splitvibe.aiProxyUrl`, `splitvibe.googleWebClientId`), không nằm trong repo. Bản debug được dùng `http://127.0.0.1:8787` (`wrangler dev` cùng `adb reverse`); bản release bắt buộc `https`, và build dừng nếu không phải.

Vết mở rộng (ranh giới `BillAnalyzer`): biến thể kế tiếp có thật là hóa đơn nhiều trang ở Phase 4 và thao tác "chỉnh bằng lời" ở Phase 7, mỗi thao tác là một đường mới trên proxy dùng chung phần xác định người gọi, hạn mức và trần chi phí. `SplitFlowTest` được nối với một `BillAnalyzer` ghi sẵn đọc fixture bằng `BillAnalysisParser`.

## Files

Tạo mới:
- `proxy/` (`package.json`, `package-lock.json`, `tsconfig.json`, `wrangler.jsonc`, `vitest.config.ts`, `src/index.ts`, `src/policy.ts`, `src/input.ts`, `src/identity.ts`, `src/quota-book.ts`, `src/ledger.ts`, `src/spend.ts`, `src/respond.ts`, `src/session.ts`, `src/account.ts`, `src/switches.ts`, `src/prompt.ts`, `src/schema.ts`, `src/bill-analysis.ts`, `test/`)
- `app/src/sharedTest/resources/fixtures/sample_bill_request.json` (thân yêu cầu mẫu dùng chung cho test của app và của proxy)
- `proxy/eval/` (bộ chạy đánh giá và thư mục hóa đơn mẫu)
- `docs/proxy.md` (chạy ở máy, triển khai, secret)
- `app/src/main/java/com/dantech/splitvibe/llm/GatewayBillAnalyzer.kt` (đọc hóa đơn qua proxy, ánh xạ mã lỗi thành thông báo; đọc lại cùng hóa đơn sau một lần lỗi thì giữ `requestId` để không bị tính hai lượt)
- `app/src/main/java/com/dantech/splitvibe/data/proxy/ProxyApi.kt` (OkHttp: bốn đường của proxy, đọc NDJSON từng dòng, hủy coroutine thì hủy cuộc gọi)
- `app/src/main/java/com/dantech/splitvibe/data/account/` (mã cài đặt, phiên mã hóa bằng Keystore, số lượt còn lại, trạng thái AI; đăng nhập Google và xóa tài khoản ở bước 5)
- `app/src/test/java/com/dantech/splitvibe/TestProxy.kt` (client proxy thật trỏ vào MockWebServer, dùng cho test luồng và test ảnh chụp; câu trả lời NDJSON của hóa đơn mẫu nằm trong `Fixtures.proxyAnswer()`)
- `app/src/androidTest/java/com/dantech/splitvibe/llm/GatewayBillAnalyzerOnDeviceTest.kt`

Sửa:
- `app/build.gradle.kts`, `gradle/libs.versions.toml` (Credential Manager và thư viện Google ID; `AI_PROXY_URL` và `GOOGLE_WEB_CLIENT_ID` vào `BuildConfig`; bỏ `DEFAULT_ANTHROPIC_API_KEY` và thư viện Anthropic Java), `gradle/verification-metadata.xml`
- `data/settings/SettingsStore.kt` (bỏ `apiKey`, `model`, `hasApiKey`); `data/settings/KeystoreCipher.kt` giữ lại để mã hóa phiên
- `ui/settings/SettingsScreen.kt` (bỏ ô key và ô model; thêm tài khoản và số lượt còn lại)
- Bốn nơi đọc `hasApiKey` nêu ở phần Context
- `di/AppModule.kt`
- `.github/workflows/ci.yml` (job kiểm proxy), `.github/dependabot.yml` (npm trong `proxy/`), `.gitignore` (`proxy/.dev.vars`, `proxy/node_modules`)
- `app/src/test/java/com/dantech/splitvibe/ui/split/SplitFlowTest.kt`, `ui/ScreenGalleryTest.kt`, `ui/ReducedMotionTest.kt`, `BackNavigationTest.kt` (chuyển sang `RecordedBillAnalyzer` hoặc proxy giả)
- `README.md`, `docs/architecture.md`

Xóa:
- `llm/ClaudeBillAnalyzer.kt`, `llm/BillPrompt.kt`, `llm/BillSchema.kt`, `llm/BillAnalyzerProvider.kt`
- `app/src/test/java/com/dantech/splitvibe/llm/ClaudeBillAnalyzerTest.kt` và `LiveClaudeBillAnalyzerTest.kt`; các kiểm tra tương đương được viết lại trong `proxy/test/`
- `app/src/androidTest/java/com/dantech/splitvibe/llm/ClaudeBillAnalyzerOnDeviceTest.kt`, thay bằng một test trên thiết bị cho `GatewayBillAnalyzer`

## Implementation Steps

1. Dựng `proxy/` theo cấu trúc của skim-browser (Worker TypeScript, `wrangler`, vitest với plugin Cloudflare, msw cho mạng giả). Viết `quota-book.ts` và `ledger.ts` cùng test **trước**, rồi mới viết đường phân tích.
2. Chuyển prompt và schema sang TypeScript. Viết `POST /v1/bill-analysis`: kiểm đầu vào, gọi Anthropic qua SDK chính thức, gửi dòng chờ, quyết toán chi phí, chốt hoặc nhả lượt. Test với model giả và fixture dùng chung trong `app/src/sharedTest/resources/fixtures/`.
3. Viết `POST /v1/session` (kiểm ID token Google, cấp phiên), `GET /v1/usage`, `DELETE /v1/account`, công tắc AI và phiên bản tối thiểu; test với khóa thử tự sinh.
4. App: `GatewayBillAnalyzer`, mã cài đặt, phiên; trạng thái "AI khả dụng" thay `hasApiKey` ở bốn nơi; xóa `ClaudeBillAnalyzer`, `BillPrompt`, `BillSchema` và thư viện Anthropic Java.
5. App: đăng nhập Google bằng Credential Manager, đăng xuất, xóa tài khoản, số lượt còn lại trong Settings.
6. CI và tài liệu: job kiểm proxy (typecheck và test), Dependabot cho npm, `docs/proxy.md`.
7. Bộ đánh giá: ít nhất 30 hóa đơn mẫu chữ Latin, chạy từng model ứng viên qua proxy chạy ở máy, xuất báo cáo độ chính xác, độ trễ và chi phí vào `plans/reports/`. Cần key Anthropic thật.
8. Chủ sản phẩm triển khai: tài khoản Cloudflare, `wrangler deploy`, ba secret (key Anthropic, muối IP, khóa ký phiên), OAuth client của Google cho Android và cho web; rồi một lần gọi thật từ **bản `prodRelease`**.

## Todo

- [x] `quota-book.ts` và `ledger.ts` có test: giữ chỗ, chốt, nhả, hết hạn, gọi lại cùng `requestId`, giới hạn IP, trần chi phí
- [x] `bill-analysis` có kiểm đầu vào, structured output, `max_tokens`, dòng chờ, quyết toán chi phí
- [x] Prompt và schema chạy trên proxy, fixture dùng chung
- [x] `session`, `usage`, `account`, công tắc AI và phiên bản tối thiểu, có test
- [x] `GatewayBillAnalyzer`; trạng thái "AI khả dụng" thay `hasApiKey` ở bốn nơi
- [x] Xóa `ClaudeBillAnalyzer`, test của nó và thư viện Anthropic Java
- [ ] Đăng nhập Google, đăng xuất, xóa tài khoản, số lượt còn lại
- [ ] Job kiểm proxy trong CI, Dependabot cho npm, `docs/proxy.md`
- [ ] Bộ đánh giá và báo cáo chọn model
- [ ] Proxy đã triển khai và một lần gọi thật từ bản `prodRelease` thành công

## Success Criteria

- Mọi biến thể build không có key và không có lớp `com.anthropic`: quét các tệp dex trong AAB `prodRelease` và APK `devDebug`, kết quả phải là 0.
- Một lần gọi thật từ **bản `prodRelease`** qua proxy trả về đúng hóa đơn mẫu (tổng 902.664 ₫, 7 món). Mục "chưa kiểm chứng" của POC được đóng lại và README được cập nhật.
- Bản `prodRelease` không còn đường nào đòi API key: tìm chuỗi "API key" trong mã giao diện cho kết quả 0.
- Test của proxy:
  - bản cài chưa đăng nhập bị từ chối ở lượt thứ 4; tài khoản Google bị từ chối ở lượt thứ 11 trong tháng;
  - gọi lại cùng `requestId` không trừ thêm lượt; client ngắt kết nối giữa chừng vẫn bị tính lượt; lỗi 5xx của model thì không; chỗ giữ quá 5 phút được nhả;
  - vượt giới hạn IP hoặc một trong ba trần chi phí thì bị từ chối kèm `Retry-After`;
  - ID token sai chữ ký, hết hạn hoặc của app khác, và phiên bị sửa, đều bị từ chối;
  - xóa tài khoản thì proxy không còn giữ gì về tài khoản đó.
- Tắt AI trên proxy thì app chuyển sang nhập tay ngay ở yêu cầu kế tiếp.
- Báo cáo đánh giá nêu model được chọn, độ chính xác trên bộ mẫu, độ trễ p50 và p95, chi phí trung bình mỗi lần đọc.

## Risks

| Rủi ro | Dấu hiệu nhận biết | Phản ứng đã định |
|---|---|---|
| Model rẻ đọc sai hóa đơn khó | Bộ đánh giá: tỷ lệ khớp tổng dưới ngưỡng | Dùng model cao hơn làm mặc định hoặc leo thang khi phép đối chiếu trên máy báo lệch |
| Chi phí AI vượt dự tính | Trần chi phí toàn cục bị chạm | Hạ hạn mức hoặc trần trong `policy.ts`; app vẫn nhập tay được |
| Lượt dùng thử bị vét bằng cách xóa dữ liệu app | Nhiều mã cài đặt mới trên cùng một IP | Hạ giới hạn lượt thử theo IP, hoặc bỏ lượt thử khi chưa đăng nhập |
| Giới hạn theo IP chặn nhầm người dùng chung một IP (mạng di động dùng NAT) | Báo lỗi 429 vì IP từ người dùng thật | Nới giới hạn IP, dựa nhiều hơn vào trần chi phí |
| Xóa tài khoản rồi đăng nhập lại để có lại 10 lượt trong tháng (xóa là phải xóa hết, kể cả bộ đếm) | Nhiều lần xóa tài khoản trên cùng IP | Mức độ như vét lượt dùng thử bằng mã cài đặt mới; giới hạn IP và trần chi phí vẫn chặn chi phí |
| Mức giữ chỗ chi phí rộng hơn chi phí thật nhiều lần (giá đắt nhất nhân 16.000 token ra tối đa), nên trần bị chạm sớm khi nhiều lượt chạy cùng lúc | 429 `spend_limited` hoặc `busy` trong khi chi phí thật còn thấp | Nâng trần trong `policy.ts`, hoặc hạ `maxTokens` khi bộ đánh giá cho thấy câu trả lời ngắn hơn nhiều |
| Cấu hình đăng nhập Google sai (OAuth client, SHA-1) | Credential Manager báo lỗi cấu hình | Thử trên bản dev với SHA-1 của khóa debug trước khi phát hành |
| Gói miễn phí của Workers giới hạn thời gian CPU mỗi yêu cầu (10 ms) `[UNVERIFIED]` | Lỗi vượt CPU trong log của Worker | Chuyển sang gói Workers trả phí; đo bằng `wrangler tail` khi triển khai |
| Lần đọc kéo dài quá 25 giây sau khi app ngắt kết nối thì không được tính lượt | Log có kết quả `client_gone` | Chấp nhận (người dùng không nhận được gì); nếu cần tính cả lần này thì chuyển lời gọi model sang Queue hoặc Durable Object |
| Xóa tài khoản trong lúc một lần đọc còn chạy: lần đọc đó tạo lại bộ đếm của tài khoản khi xong | Bộ đếm của tài khoản đã xóa xuất hiện lại | Chấp nhận: bộ đếm chỉ là số lượt, gắn với mã băm của tài khoản |

## Security

- Key Anthropic chỉ nằm trong secret `ANTHROPIC_API_KEY` của Worker; ở máy thì trong `proxy/.dev.vars`, tệp git bỏ qua. Hai secret khác là `HASH_SALT` (muối để băm IP, `sub` của tài khoản Google và nội dung yêu cầu; ít nhất 16 byte, thiếu thì mọi đường trả 503 `not_configured`) và `SESSION_SIGNING_KEY` (ít nhất 32 byte); web client ID của Google là biến công khai `GOOGLE_CLIENT_ID`.
- IP được băm kèm muối và không được lưu; object theo IP tự xóa khi hết khung thời gian. Mã cài đặt là UUID ngẫu nhiên, không gắn với mã phần cứng của máy.
- Phiên do proxy ký, có hạn 30 ngày, gắn với `sub` của tài khoản Google; trên máy, phiên được mã hóa bằng Keystore.
- Repo công khai, nên prompt, schema và mã proxy đều đọc được: an toàn của hệ thống không dựa vào việc giấu chúng.
- Văn bản hóa đơn là dữ liệu không tin cậy: prompt coi nó là dữ liệu chứ không phải lệnh, đầu ra bị ràng bằng schema, và mọi phép tính tiền vẫn chạy trên máy.
- Không log nội dung hóa đơn, câu lệnh, tên người, mã cài đặt hay IP: mỗi yêu cầu chỉ để lại một dòng log với kết quả, loại người gọi, model, số token, chi phí và độ trễ. Log tự động của mỗi lần gọi (invocation logs) và tracing của Cloudflare đều tắt trong `wrangler.jsonc`, vì chúng ghi siêu dữ liệu của yêu cầu. Không có gì trong CI triển khai proxy.

## Next

Phase 4 lưu hóa đơn và bản nháp trên máy và thêm đường tỷ giá vào proxy. Phase 7 thêm đường "chỉnh bằng lời" dùng chung hạn mức. Phase 8 thêm hạn mức theo gói và kiểm giao dịch mua trên proxy. Mỗi giai đoạn để dữ liệu trên proxy phải thêm phần xóa tương ứng vào `DELETE /v1/account`.
