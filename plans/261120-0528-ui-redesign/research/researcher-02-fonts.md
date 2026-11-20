# Research: bundling Bricolage Grotesque and Be Vietnam Pro

Date: 2026-11-20. Tags: **[V]** I checked it myself (files downloaded and read with fontTools 4.68.0, rendered with Pillow, or source read); **[D]** it comes from documentation and was not tested here.

## Outcome first

- Bundle **six static TTFs** in `core/designsystem/src/main/res/font/`. Two are Bricolage Grotesque (Bold 700 and ExtraBold 800, the default 14pt optical size) and four are Be Vietnam Pro (Regular 400, Medium 500, SemiBold 600, Bold 700). Do not bundle the Bricolage variable file.
- **Be Vietnam Pro has no tabular figures** [V]: it has no `tnum` feature and its digits have different widths. So `fontFeatureSettings = "tnum"` does nothing for it. All amount styles, including `amountSmall`, which is built on `bodyLarge` today, must use Bricolage Grotesque. Bricolage has a complete `tnum` [V].
- Both fonts cover Vietnamese fully [V]. Be Vietnam Pro has no ฿ and no arrows [V]. Those characters fall back to the system font, so keep them in Bricolage text or accept the fallback.
- The six files make the APK about 0.33 MB bigger (measured estimate, see section 6).

## Repository context [V]

- Compose BOM `2026.09.00`, Robolectric `4.17`, Roborazzi `1.76.0`, AGP `9.1.1`, Kotlin `2.3.21` (`gradle/libs.versions.toml`). minSdk 26 and compileSdk 37 in `app` and `core/designsystem`.
- `core/designsystem/.../tokens/SvTypography.kt`: `SvTypography = Typography()` (the Material defaults, so no custom `FontFamily` yet). `amountLarge` is `headlineMediumEmphasized`, `amountMedium` is `titleMediumEmphasized` and `amountSmall` is `bodyLarge`, each with `.copy(fontFeatureSettings = "tnum")`.
- `core/designsystem` has no `src/main/res/` yet. Screenshot tests use `@GraphicsMode(NATIVE)` and `@Config(sdk = [34])`. `robolectric.properties` sets `sdk=34`. `app/build.gradle.kts` sets `unitTests.isIncludeAndroidResources = true` and `robolectric.pixelCopyRenderMode=hardware`.
- The app has no open-source licences screen or licences library.

## 1. Licence

- Both fonts use the **SIL Open Font License 1.1** [V]. The name table says so (IDs 13 and 14), Google Fonts `METADATA.pb` says `license: "OFL"`, and each family ships its `OFL.txt`.
- Copyright lines [V]: "Copyright 2022 The Bricolage Grotesque Project Authors (https://github.com/ateliertriay/bricolage)" and "Copyright 2021 The Be Vietnam Pro Project Authors (https://github.com/bettergui/BeVietnamPro)".
- **No Reserved Font Name is declared** in either `OFL.txt` [V]. A renamed or subsetted file would therefore not need a new name, but this plan does not modify the files.
- `fsType = 0` (installable embedding, no restriction) in every file [V].
- What a bundling app must do [D, OFL FAQ 1.20]:
  - Ship the copyright notice and the licence text with the fonts. A file in the app or a licences screen is enough.
  - Credit in the interface is not required. The FAQ calls an About-box mention good practice.
  - The fonts may be part of a paid app (FAQ 1.4) but may not be sold on their own.
  - Removing glyphs counts as modification (FAQ 2.6).
- Suggestion: put both `OFL.txt` files in the app as text, for example `core/designsystem/src/main/assets/licenses/` or a raw resource, and link them from settings or about.

## 2. Glyph coverage [V: cmap check of every file plus a Pillow render]

| Character | Bricolage Grotesque | Be Vietnam Pro |
|---|---|---|
| Vietnamese, all 134 precomposed letters (U+1EA0..1EF9, ă â đ ê ô ơ ư and capitals) | yes | yes |
| ₫ U+20AB, € , $ | yes | yes |
| ฿ U+0E3F | yes | **no** |
| × U+00D7, · U+00B7, … U+2026, − U+2212, – U+2013, NBSP U+00A0 | yes | yes |
| → U+2192, ← U+2190 | yes | **no** |
| U+202F narrow no-break space, U+2009 thin space, U+2007 figure space | yes | **no** |

- The stacked marks render correctly in both fonts [V, Pillow render of "QUÁN ỐC BA MIỀN", "Ốc hương rang muối", "Nước suối"]. The glyphs are precomposed (for example `uni1ED0` for Ố), and both fonts also have `ccmp`, `mark` and `mkmk` for marks typed as separate characters.
- Missing glyphs are drawn by Android's system fallback font, usually Roboto or Noto [D]. They show up in Roboto style next to Be Vietnam Pro.
- Watch for formatters that emit U+202F. For example, the French locale puts it before €. That character would fall back inside Be Vietnam Pro text, which is one more reason to set amounts in Bricolage.

## 3. Tabular figures

- **Bricolage Grotesque** [V]: GSUB has `tnum`, `pnum`, `lnum` and `onum`. `tnum` maps all ten digits to `.tf` glyphs of one advance: 622 units in the 14pt Bold, 629 in the 14pt ExtraBold, 580 in the variable default. The default digits are proportional (346 to 665 units), so the feature is needed and it works.
- **Be Vietnam Pro** [V]: GSUB has no `tnum` or `pnum`, and there are no `.tf` glyphs. Default digit advances range from 385 to 710 units in Regular. Measured at 30 px, "1111" is 46.2 px and "0000" is 81.1 px. The upstream repo file is byte-identical in size to the Google Fonts file (v1.002), so no newer build with `tnum` exists there.
- Compose with a bundled font [D, Compose API]: `TextStyle.fontFeatureSettings` becomes `Paint.setFontFeatureSettings`, which HarfBuzz applies to whatever typeface is set. That includes `res/font` fonts. A missing feature is ignored silently, so nothing errors when it has no effect. This was not run in a Robolectric test here.

## 4. Variable or static files

- **Bricolage Grotesque** [V]: the variable file `BricolageGrotesque[opsz,wdth,wght].ttf` is 408,496 bytes. Its axes are `opsz` 12..96 (default 96), `wght` 200..800 (default 800) and `wdth` 75..100 (default 100). Upstream also ships static files in `fonts/ttf/` in three optical sizes:
  - unprefixed, which I matched to opsz 14 by instancing the variable file and comparing advances: `BricolageGrotesque-Bold.ttf` is 116,260 bytes and `-ExtraBold.ttf` is 118,028 bytes;
  - `BricolageGrotesque12pt-*.ttf`, about 117 KB each;
  - `BricolageGrotesque96pt-*.ttf`, about 115 KB each. This cut is noticeably narrower: H advance 633 against 723.
- **Be Vietnam Pro** [V]: static files only, with no variable version on Google Fonts or upstream. Regular is 132,948 bytes, Medium 135,980, SemiBold 136,736 and Bold 140,300.
- Compose support [V, androidx-main source `PlatformTypefaces.android.kt`]:
  - `Font(resId, weight, style, variationSettings)` applies the axes on API 26 and later through `Paint.fontVariationSettings`.
  - Each weight still needs its own `Font(...)` entry with a matching `FontVariation.weight(..)`.
  - The source has a work-around for a typeface cache bug (b/353609778).
  - Docs [D]: variable fonts work on Android O (API 26) and later, which is exactly minSdk. `FontVariation` is still marked `@ExperimentalTextApi` in the docs sample.
- I found no documented Robolectric issue for variable fonts, but the search was thin [D]. An AOSP change to cache variation typefaces is noted as having broken Robolectric tests once. Variable-font rendering under Robolectric is **unverified**.
- **Recommendation: use static files.** Two Bricolage weights take 234 KB raw (119 KB compressed), against 408 KB (252 KB) for the variable file. Static files involve no experimental API and none of the variation code paths on API 26 to 27 or in Robolectric. The cost is that the optical size is fixed at 14pt. Google Fonts' web specimen uses automatic optical sizing, so mock-ups drawn in a browser at 28 px or more may look slightly narrower than the 14pt cut.

## 5. Robolectric native graphics and res/font

- [D/inferred, not run, because Gradle was not allowed] `@GraphicsMode(NATIVE)` runs the real Android text stack (Minikin, HarfBuzz and Skia) on the JVM. `ResourcesCompat.getFont` reads `res/font` from the merged resources, and Compose loads resource fonts through that same call [V, `AndroidFontLoader.android.kt`]. The project already has the setup this needs: `isIncludeAndroidResources = true`, NATIVE graphics and SDK 34.
- Set-up to add: nothing beyond putting the files in `res/font` of a module that the tests depend on. A library module's resources merge into `app`'s unit-test resources [D].
- Risk: if loading fails, Android may fall back to the default typeface without an error. The first screenshot should therefore be checked by eye, for example for Bricolage's Q tail and the 96/14pt shapes. All existing reference screenshots will change and must be recorded again with `-Pscreenshots=record`.

## 6. Effect on app size [V, sizes measured; compression estimated with zlib level 9]

| Set | Raw | Deflated (approx. APK cost) |
|---|---|---|
| Bricolage Bold + ExtraBold (14pt static) | 234 KB | 119 KB |
| Be Vietnam Pro 400/500/600/700 | 546 KB | 215 KB |
| **Recommended total (6 files)** | **780 KB** | **~334 KB** |
| Alternative: Bricolage variable file alone | 408 KB | 252 KB |

- AAPT compresses `.ttf` in `res/font` unless it is listed in `noCompress` [D]. The project has no `noCompress` setting [V].
- R8 does not shrink fonts. Subsetting, for example dropping Cyrillic, which neither font has, would save little, and it counts as modification under the OFL.

## 7. Where to get the files [V: all fetched over HTTPS from this environment through the proxy, HTTP 200]

- Google Fonts repository (the curated builds):
  - `https://github.com/google/fonts/tree/main/ofl/bricolagegrotesque/`, which has `BricolageGrotesque[opsz,wdth,wght].ttf`, `OFL.txt` and `METADATA.pb`. The variable file only.
  - `https://github.com/google/fonts/tree/main/ofl/bevietnampro/`, which has `BeVietnamPro-{Regular,Medium,SemiBold,Bold}.ttf` and `OFL.txt`.
- Upstream: `https://github.com/ateliertriay/bricolage`, which has `fonts/ttf/BricolageGrotesque-{Bold,ExtraBold}.ttf`, the 12pt and 96pt variants, `fonts/variable/` and `OFL.txt`. Google Fonts pins commit `84745e5b`.
  - `https://github.com/bettergui/BeVietnamPro`, which has `fonts/ttf/`. Google Fonts pins commit `804e62d8`.
- The raw URLs work, for example `https://raw.githubusercontent.com/ateliertriay/bricolage/main/fonts/ttf/BricolageGrotesque-Bold.ttf`. The `gh` API is not enabled for these repositories in this session, but raw downloads are enough.
- Downloaded copies for the implementer are in `/tmp/claude-0/-home-user-split-bill-poc/f8110399-95a0-5f4c-9986-bbd080ac93f5/scratchpad/fonts/` and `.../scratchpad/fonts2/`. This folder is temporary, so download again or pin a commit when implementing.

## 8. Recommendation

**Files.** Rename them to lowercase with underscores, as `res/font` requires. Put them in `core/designsystem/src/main/res/font/`:

| Resource file | Source file | Weight |
|---|---|---|
| `bricolage_grotesque_bold.ttf` | ateliertriay/bricolage `fonts/ttf/BricolageGrotesque-Bold.ttf` | 700 |
| `bricolage_grotesque_extrabold.ttf` | ateliertriay/bricolage `fonts/ttf/BricolageGrotesque-ExtraBold.ttf` | 800 |
| `be_vietnam_pro_regular.ttf` | google/fonts `ofl/bevietnampro/BeVietnamPro-Regular.ttf` | 400 |
| `be_vietnam_pro_medium.ttf` | google/fonts `ofl/bevietnampro/BeVietnamPro-Medium.ttf` | 500 |
| `be_vietnam_pro_semibold.ttf` | google/fonts `ofl/bevietnampro/BeVietnamPro-SemiBold.ttf` | 600 |
| `be_vietnam_pro_bold.ttf` | google/fonts `ofl/bevietnampro/BeVietnamPro-Bold.ttf` | 700 |

Also add both `OFL.txt` files, for example as `bricolage_grotesque_ofl.txt` and `be_vietnam_pro_ofl.txt` in assets or `res/raw`, and show them on an about or licences screen.

**Declaration** (sketch for `SvTypography.kt`):

```kotlin
val Bricolage = FontFamily(
    Font(R.font.bricolage_grotesque_bold, FontWeight.Bold),
    Font(R.font.bricolage_grotesque_extrabold, FontWeight.ExtraBold),
)
val BeVietnamPro = FontFamily(
    Font(R.font.be_vietnam_pro_regular, FontWeight.Normal),
    Font(R.font.be_vietnam_pro_medium, FontWeight.Medium),
    Font(R.font.be_vietnam_pro_semibold, FontWeight.SemiBold),
    Font(R.font.be_vietnam_pro_bold, FontWeight.Bold),
)
```

Build `SvTypography` from `Typography()` by copying each style with `fontFamily`. Display, headline and title styles, including the `*Emphasized` ones, use `Bricolage` with weight 700 or 800. Body and label styles use `BeVietnamPro`. Asking Bricolage for a weight below 700 picks the nearest bundled one, 700, so give every Bricolage style an explicit weight.

**Equal-width digits.** Keep `withEqualWidthDigits()` (`"tnum"`) and base every amount style on Bricolage. That includes `amountSmall`: base it on a `bodyLarge` copy with `fontFamily = Bricolage, fontWeight = Bold`, because `"tnum"` does nothing on Be Vietnam Pro. Numbers inside body text, such as quantities and dates, will have proportional digits, which is fine because they do not sit in columns.

**Tests.**
- Record all screenshots again and check by eye that both families appear and that ố, ầ, ữ and ₫ render.
- Add one component-catalog screenshot with a column of amounts ("1.111.111 ₫" above "8.888.888 ₫") in each amount style to prove that `tnum` lines them up.
- Add a JVM test that the text width of "1111" equals that of "8888" in each amount style, using `TextMeasurer` under Robolectric.
- Add one screenshot with €, $, ฿, ×, · and … in body text to make any fallback visible.

## Unresolved questions

1. Optical size of the titles. The 14pt static cut is wider than the 96pt display cut. If the mock-ups use the display look for large totals, add `BricolageGrotesque96pt-ExtraBold.ttf` (about 116 KB) as a separate family for the hero amount. The designer should decide.
2. Running a NATIVE Robolectric screenshot with `res/font` fonts was not done because Gradle was not allowed. The first implementation run must confirm it.
3. Where the licence texts are shown. The app has no licences screen yet.

Sources: developer.android.com/develop/ui/compose/text/fonts; openfontlicense.org/ofl-faq; androidx-main `compose/ui/ui-text/.../font/{AndroidFontLoader,PlatformTypefaces}.android.kt`; google/fonts `METADATA.pb` for both families; robolectric.org javadoc (ShadowNativeTypeface, TypefaceNatives); github.com/takahirom/roborazzi.
