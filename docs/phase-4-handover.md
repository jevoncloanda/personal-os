# Phase 4 — Unified Entertainment Library

5 November 2026, Asia/Jakarta. Native offline Library for movies, TV series, games and books. No backend, login, network dependency, personal fixture writes, data clear or push.

## Shipped behavior

One extended Library entity, DAO, ViewModel and editor provide create/edit/save/cancel, detail navigation, confirmed deletion, ratings (1–5), notes, synopsis, subtitle, year, favorite and relevant dates. Manual duplicate titles are allowed. Media type stays fixed after creation. Drafts and filters survive Activity recreation; dirty drafts require discard confirmation. Covers have media-specific outline fallbacks.

Underlying statuses remain PLANNED/ACTIVE/COMPLETED/DROPPED with ON_HOLD appended. Labels adapt to watchlist/watching/watched, backlog/playing, and want-to-read/reading/finished. Reversal preserves progress, ratings, notes and dates. Entering active/completed fills a missing corresponding date; reopening a historical completion keeps a valid start/completion ordering. Explicit editor clearing removes a date. Progress never automatically completes a record.

- TV: manual current season/episode, optional total seasons and aggregate watched/total episodes. No episode catalog or inferred production status.
- Games: PC/PlayStation/Xbox/Nintendo/Other, optional manual hours (stored as integer minutes) and completion percentage. Unchanged rounded display input preserves original minute precision.
- Books: author/ISBN, current page and optional total pages. Known totals bound progress and produce percentages; unknown totals remain usable.
- Movies: watchlist/watching/watched, watched date, rating/review and optional cover. Original movie-minute, TV-episode and game-whole-hour fields retain their legacy meaning.

Library home includes title search, media/status filters, title/added/updated sorting, active highlights, backlog/recent completion summaries and a lazy collection with 60 rows plus lookahead per page. Room queries bound materialized rows; no full-collection Compose subscription or Paging dependency. Today observes the three most recently updated active records and links to details. Quick Add has one Add to Library action followed by the four-type chooser.

## Data and artwork safety

Room **v5**, exported schema `app/schemas/com.jevon.personalos.data.PersonalDatabase/5.json`. Explicit **4→5 ALTER** migration adds Library columns/indexes, preserves original seven fields and all other domains, defaults unknown added date to zero. Full 1/2/3/4→5 chains are tested. Provider identities are validated and unique per media kind; they cannot overwrite personal fields through a background refresh because no refresh client exists.

Backup **format 4/schema mapping 5** includes every portable Library field. Formats **1–3** remain readable with explicit defaults. Local cover files and cover references are excluded; restored items use fallback artwork. Appearance/Today preferences retain their existing device-local treatment. Atomic replacement, failure rollback, checksum, strict UTF-8/JSON and existing 8 MiB/20,000-record bounds remain. Text is limited to valid Unicode and 8,192 characters at the writer boundary. The bounded parser budget now accommodates the largest supported v4 row; a 15,000-item roundtrip guards the prior export/decode complexity mismatch.

Android OpenDocument copies content immediately into private JPEG files: source ≤5 MiB, JPEG/PNG/WebP, bounded dimensions/pixels, sampled decode, stored maximum dimension 512 and ≤1 MiB per image. No broad gallery permissions or retained temporary content URIs. Decode runs off the UI thread with an 8 MiB bitmap cache and missing/corrupt-file fallback. Normal discard, replacement/save and delete clean only unreferenced images, with a transactional reference check. Cleanup failure cannot invalidate a successful record save. Process death/interruption just after image publication can still leave an unreferenced private file; no background orphan sweep or artwork portability is claimed. Android automatic app backup remains disabled.

## Provider research and decision

No live metadata provider ships in this phase; all manual workflows are independent of internet. No static fixture is presented as live search and no credential/INTERNET permission was added.

- [TMDB authentication](https://developer.themoviedb.org/docs/authentication-application) requires credentials unavailable here. [FAQ](https://developer.themoviedb.org/docs/faq) describes attribution/noncommercial use; [rate limits](https://developer.themoviedb.org/docs/rate-limiting) remain changeable and require respecting 429 responses. No distributed secret or proxy workaround was introduced.
- [RAWG authentication](https://rawg.readme.io/reference/authentication) requires an API key unavailable here. Full current commercial/caching terms were not verified; no integration is claimed.
- [Open Library API guidelines](https://openlibrary.org/developers/api) support keyless user-driven lookup, request application identification/contact and registration, and document throttling (1 request/sec unidentified, 3 identified). No application contact/registration was configured. [Search](https://openlibrary.org/dev/docs/api/search) and [licensing](https://openlibrary.org/developers/licensing) were reviewed; book metadata and artwork rights are not assumed interchangeable. A future compliant lookup should identify the app, cache, debounce/cancel, respect limits/attribution and preview imports while preserving user-owned fields.

## Validation

| Gate | Actual result |
| --- | --- |
| JVM tests | 72 passed, zero failures/errors |
| Debug / Android-test / unsigned release APK | Passed |
| Android lint | Passed, zero errors, 20 advisory warnings (19 existing plus Bitmap KTX suggestion) |
| Samsung data/integration | 48 passed: Library 5, artwork 5, migrations 4, backup 6, Money 10, subscriptions 9, Schedule 8, read-only personal audit 1 |
| App-scoped crash inspection | UID 10037 AndroidRuntime error log returned no entries |
| Final Library UI / Quick Add | 2 passed: complete isolated workflow and all four installed-app Quick Add/cancel paths |

Commands use `rtk` and the existing `tools/gradle.ps1` SDK/JDK setup. Build gate: `testDebugUnitTest assembleDebug assembleDebugAndroidTest assembleRelease lintDebug`. Device runner: `tools/run-phase1-device.ps1`, `-DataOnly` for isolated data classes, and LibraryWorkflowTest/LibraryNavigationTest for UI. Reports are ignored under `app/build/reports/phase-4-*.txt`.

Installed on Samsung SM-S942B with `adb install -r`; never cleared app data. Fixture writes/restores use in-memory or UUID-named disposable review databases. The personal audit checked foreign keys, complete portable in-memory roundtrip and available prior counts. The installed-app navigation test compares complete personal snapshots before/after without saving fixtures. No notification harness or security setting was changed.

Initial UI attempts exposed ambiguous test selectors: detail/editor status controls, page/Quick Add labels and a title repeated in a noninteractive summary. Selectors now target the appropriate clickable node, and assertions require three stored active rows and populated Today. Final full run passed both tests (`phase-4-ui-final.txt`); earlier failures are retained in separate ignored reports. A later optional screenshot refresh lost its Compose hierarchy without an app runtime error; its screenshot-only edits were reverted to the passing test. One earlier lint attempt crashed when a new test file appeared during analysis; stable reruns passed without suppression.

Independent persistence/backup review found and fixed text/parser boundary mismatches; artwork review led to safe normal-lifecycle cleanup and guarded failure handling. Independent light/dark screenshot review found no material Library clipping or design-system mismatch. Default-scale screenshots do not establish TalkBack, 2× text, RTL, tablet/foldable or large-dataset performance certification. The review host's muted dark status-bar glyphs do not establish a shipping Activity defect.

Screenshots are local/ignored under `docs/screenshots/phase-4/`: `collection-light.png`, `book-detail-light.png`, `book-detail-dark.png`, `today-library.png`. They contain disposable fixtures only. The Today capture retains the keyboard and shows the upper dashboard; it is not visual proof of the Library rows (the passing UI/query assertions provide that proof). Reopening the isolated Activity tests disk-backed persistence; no cold-process personal-data edit is needed to fill this gate. Offline functionality uses no network code; phone radio/security settings were not altered.

## Commits and next phase

Source/test commit: **9d54cfa** — `feat(library): track entertainment offline`. Documentation commit is identified by `git log -1 --format=%h -- docs/phase-4-handover.md` (a commit cannot embed its own hash). The pre-existing untracked NotificationDeviceTest.kt remains untouched and uncommitted. No push.

Recommended next phase: use the offline Library with real personal entries, then decide whether identified Open Library search or a provider credential model justifies a small compliant metadata-import slice. Keep image portability, orphan recovery and accessibility/device-size expansion explicit follow-ups.
