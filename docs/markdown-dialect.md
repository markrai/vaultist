# Vault Markdown dialect

Authoritative rules for Markdown Vault notes in Vaultist. The Go index parser ([`server/internal/markdown`](../server/internal/markdown)) implements these rules for metadata, links, slugs, and attachments. The Android display stack ([`ui/markdown`](../android/app/src/main/java/com/markrai/vaultist/ui/markdown)) handles block layout and inline presentation only; it must not grow index responsibilities.

Shared fixtures live in [`fixtures/markdown/`](../fixtures/markdown/) and are exercised by Go and Kotlin tests.

## Frontmatter

- Notes may start with a YAML frontmatter block delimited by `---` lines (or `...` as closing delimiter).
- Supported keys: `title` (string), `aliases` (YAML scalar or sequence of strings).
- If `title` is absent or blank after parse, the index falls back to the filename without extension.
- The body used for heading/link parsing begins after the closing delimiter.

## Standard Markdown

Goldmark parses the body for:

- ATX headings (`#` … `######`)
- Paragraphs, lists, blockquotes
- GFM task-list items on list lines: `- [ ]`, `- [x]`, `- [X]` (and `*`, `+`, or ordered `N. [ ]` forms). Android read-only display renders these as checkboxes; the index treats them as ordinary list lines.
- Fenced code blocks (`` ``` `` or `~~~`, run length ≥ 3)
- Standard Markdown links and images (`[text](url)`)

Standard external links (`http://`, `https://`, `//`, `mailto:`) are not indexed as vault links.
The Android display renderer still presents them as clickable web links for:

- Markdown links (`[label](https://…)`)
- Angle autolinks (`<https://…>`)
- Bare `http://` / `https://` / `mailto:` URLs in note body text


## Wiki links and embeds

| Form | Indexed as |
|---|---|
| `[[target]]` | Wiki link to note or asset |
| `[[target\|display]]` | Wiki link with display text |
| `[[target#fragment]]` | Wiki link with heading or anchor fragment |
| `![[target]]` | Wiki embed |
| `![[target#fragment]]` | Wiki embed with fragment |

- Targets are URL-decoded (`%20` → space, etc.).
- Pipe (`|`) separates target from optional display text.
- Hash (`#`) separates target from optional fragment (trimmed whitespace).

### Asset vs note

- Extensionless targets and `.md` targets are notes.
- Wiki embeds whose target has a non-`.md` extension are assets (images: `.png`, `.jpg`, `.jpeg`, `.webp`, `.gif`, `.svg`).
- Standard Markdown image links to non-Markdown paths are assets.

## Code exclusions

The wiki scanner does not run inside:

- Fenced code blocks (opening fence at indent ≤ 3 with run ≥ 3; closed by matching fence).
- Inline code spans (balanced `` ` `` runs on the same line).

Wiki-like text inside those regions is not indexed.

## Heading slugs

Computed at index time for each heading. **Server slugs are canonical** in API `Heading.slug`.

Algorithm (Go and Android display navigation must match):

1. Lowercase and trim.
2. Remove characters except Unicode letters, numbers, combining marks, spaces, and hyphens.
3. Replace each whitespace character with `-` (multiple adjacent spaces yield multiple hyphens).
4. Trim leading and trailing `-`.

Example: `First Heading` → `first-heading`; `A: Better Title` → `a-better-title`.

Android fragment scroll compares `headingSlug(heading.text)` to `headingSlug(fragment)` for wiki heading targets; use the same algorithm as the server.

## Parser responsibilities

| Layer | Package | Responsibility |
|---|---|---|
| Index parser | `server/internal/markdown` | Frontmatter, headings + slugs, all link occurrences, attachments, line/column/context |
| Display parser | `android/.../ui/markdown/MarkdownParser.kt` | Block structure only (headings, paragraphs, lists, GFM task lists, quotes, code) |
| Display renderer | `android/.../ui/markdown/MarkdownRenderer.kt` | Inline styling; vault links from server `Note.links`; web URLs via markdown / autolink / bare URL presentation |

Android does **not** re-parse wiki links for resolution. Future editing (Phase 8+) must treat the Go index parser as authoritative for note structure.

## Changing the dialect

1. Update this document.
2. Update [`fixtures/markdown/`](../fixtures/markdown/) samples and `expected.json` / `heading-slugs.json`.
3. Change Go parser first, then Android `headingSlug` or display rules if affected.
4. Run `go test ./internal/markdown/...` and Android `DialectFixturesTest`.
