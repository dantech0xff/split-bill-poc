# Split Vibe web pages

The pages people open from a link without having the app, in one bundle that picks the page from
the path:

- `/s/#<token>`: a bill link. Everyone at the table opens it, types their name and taps the items
  they had; everyone sees the picks live. Once the payer settles the split in the app, the page
  shows what each person pays and how.
- `/r/#<token>`: a payment request, with how to pay and "Open in Split Vibe".
- `/g/#<token>`: a group invite, with how to get the app to join.

The routes they call, and the headers the Worker serves them with, are in
[docs/proxy.md](../docs/proxy.md#bill-links-and-the-web-pages). The token is read from the
link's fragment only and travels in request bodies and in the WebSocket protocol header, never
in a URL, in storage or in a log.

Preact and TypeScript, built with Vite. Colours, type, shapes, spacing and elevation come from
`src/tokens.css`, which a test of `core/designsystem` writes from the app's design system: do
not edit it by hand.

## Commands

Node 22 and npm, from `web/`:

```bash
npm ci
```

```bash
npm test          # unit tests, Vitest with happy-dom
npm run build     # typecheck, build into dist/, and check the build against the page's CSP
npm run size      # gzipped size of the initial download, against its 150 KB budget
```

`npm run build` writes `dist/index.html` and hashed scripts and styles under `dist/assets/`, which
the proxy's `npm run build:web` embeds into the Worker. Nothing else is served.
