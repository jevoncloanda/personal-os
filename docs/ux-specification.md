# UX specification

## Phase 3 Schedule and notification timing

Schedule defaults to an agenda with overdue items, the selected date's next seven days and undated reminders. Month mode shows a six-week grid with distinct selected/today states and commitment markers, followed by selected-day content. Date navigation and Today are explicit. Events, reminder checkboxes and unpaid subscription forecasts remain visibly distinct; forecast taps open Subscriptions. Completing a reminder updates Schedule/Today reactively. Event/reminder links retain the exact nominal occurrence date.

Quick Add contains working New event and New reminder actions alongside existing Money and Subscription actions; Library remains explicitly a preview. Event forms support timed/all-day, multi-day ranges, notes/location, zone and bounded recurrence. Reminder forms support optional date/time, notes, recurrence and individual completion/reopen. Shared full-screen editors retain Save, discard confirmation, IME insets and restored drafts; native date/time pickers supplement typed input. Edit/delete controls explicitly apply to the entire series. Single-occurrence event editing and week mode are deferred.

Each record's Notification timing opens a shared preference editor, with on/off, multiple lead times, bounded custom minutes where relevant and local date-based time. Existing records start off. The screen explains best-effort delivery and why alerts help before a user presses Enable Android notifications; it does not request permission at first launch. Blocked permission/category status has Android settings access. Returning from Settings reconciles schedules. Reminders continue working without alerts; undated reminders cannot configure notifications. Subscription alerts never record payments or cancel services.

## Phase 2 subscriptions

Subscriptions is a Money child route, also reached from Upcoming payments in Today. Each Today renewal opens its detail; Quick Add → New subscription opens the editor. Four primary navigation destinations remain. List search and active/inactive/all filters sit below currency-separated monthly/annual billing-rate equivalents. These are labeled forecasts, never recorded spending. Detail groups billing/defaults, upcoming unpaid dates and paid history. Shared Inter, atmospheric panels, outline fallback icons and scrolling ledger rows retain the existing visual system.

Create/edit uses the shared full-screen editor with restored drafts, discard confirmation, keyboard-safe Save, precise currency input and typed ISO dates plus a native calendar picker. Changing the renewal date or frequency discloses reanchoring. Record payment separates billed occurrence from actual payment date and amount. History opens correction of the same Money expense or an explicitly confirmed undo. Cancel marks the local plan inactive, explains history retention and does not claim to cancel with the service provider. No logos, notifications or automatic charges are introduced.

## Phase 1.7 recovery (current)

Settings includes Backup & restore, opening a focused native route using existing Inter, atmospheric panels and Material controls. Export uses Android CreateDocument; restore uses OpenDocument. Both support picker cancellation without a write to personal storage. Import validates before showing creation metadata and all nine table counts. The preview explains replacement and recommends exporting current data first; a second native dialog requires an explicit acknowledgement before its destructive confirmation becomes enabled. No silent merging or automatic restart occurs.

Reading and export have cancellable progress. The short confirmed Room replacement completes atomically and is not canceled by its UI; system Back may return to the prior destination while it finishes. Success is reported after output close or transaction commit. Invalid/corrupt files preserve existing data; provider cleanup refusal is disclosed as a possible incomplete file. Raw exception dumps are not user copy. Appearance stays on the device, and process death discards the pending preview, requiring revalidation and confirmation. Security copy explicitly states that files are unencrypted and checksums do not identify the author.

## Phase 1.6 refinement (current)

The same approved finance reference and Hybrid Today hierarchy remain authoritative. Bundled Inter now supplies explicit scalable Material type roles; a small licensed Lucide native-vector subset supplies consistent outline icons. Financial display/title roles use tabular numerals. Colors, currency labels and accounting semantics are unchanged.

Money's five sections use native scrolling underline tabs. Its tighter balance header leads into compact expense/income/transfer actions and a monthly panel grouping the period selector with recorded spending and income. Supporting scope text distinguishes transfers and forecasts. Budget/category/account/history content retains existing calculations and order. Forms use start-aligned outlined choices with disclosure chevrons and the shared typography, while keeping numeric IME, validation, unsaved-change confirmation and restoration behavior.

Compact navigation has a 72dp content row plus native system insets, 22dp outline icons and a 48dp circular Quick Add. Font scale above 1.3 retains the separate labeled Quick Add and 80dp content row; expanded screens retain the rail. Actual Samsung captures at 1.5x app-local text verify these adaptations without changing phone settings. The plain efficient sky/mist and navy gradients are retained. See [Phase 1.6 handover](phase-1.6-handover.md) for executed evidence and limits; older visual details below are historical where superseded here.

## Phase 1.5 visual authority (current)

The user-approved reference is `design/references/finance-ui-direction.png`. The current direction contract is [the sky-blue design direction](design/phase-1.5-direction.md), superseding the Phase 1 palette/layout record below. Compose-native atmospheric backgrounds, translucent-looking rounded panels, tabular financial typography and a central Quick Add action adapt the reference to Android. No iPhone chrome, bank/AI claims, new domain workflows or pixel-for-pixel reproduction are included.

Quick Add now occupies its own position inside the native bottom navigation row, with four labeled domain destinations intact. At large font scales it moves to a separate row above those destinations; expanded screens use a rail action. It does not float over content. Money has a clearly denominated active-balance header, month navigation, actual spending/income, budget headroom, category spending shares and date-grouped history. Currencies remain separate. Material You affects supporting color roles while the blue atmosphere and primary identity remain stable.

Editors use the shared ground and translucent native text-field surfaces. Inline validation appears after a save attempt, while actionable form-level errors remain visible at the top. Dirty drafts ask whether to discard or keep editing on Back/Cancel; successful Save dismisses directly. Selection changes, text edits and archive toggles mark the draft dirty. This is conservative dirty tracking: editing then reverting a value still asks before discard. Saveable drafts and edit identities remain intact through Activity recreation.

## Phase 1 hybrid Today and Money (current)

Personal mode is the default. Date/greeting leads into Priorities (overdue/today reminders, ongoing/events within the next hour), then the monthly financial overview, later-today events and recurring-payment forecasts, active library and undated reminders when relevant. No empty calendar occupies a primary section. Empty priorities are compact and reassuring. Debug sample controls live in a dedicated Developer previews dialog and are unavailable in release. A sample-mode disclosure remains visible only while previewing Today.

Static primary colors are restrained blue; Material You, light and dark remain supported through semantic Material roles. Native typography, grouped rows and one budget container are retained. Compact section spacing advances the financial summary. Scroll content reserves space for Quick Add; navigation remains bar/rail by width.

Money uses native filter chips for Overview, History, Accounts, Budgets and Categories. Currency totals are separate, with no combined FX total. History has month navigation, visible note/account/category search and selected-filter summary; a focused Filters sheet holds kind/account/category/currency controls with 8dp separation. History has bounded page controls. Focused full-screen Material editor dialogs provide scroll/IME insets, labeled fields, currency precision help, native selection menus, validation and explicit Save/Cancel. Delete requires confirmation; archive is reversible. Error messages scroll into view. Date/time input is explicit ISO date/month and 24-hour local time. Native calendar/time pickers and broader localization are later polish, not implied by text inputs.

Quick Add opens working expense, income and transfer editors. Other actions explicitly say preview and open their foundation screens. Money always edits personal storage, even if Today is showing a sample preview. The older Phase 0 notes below are historical context, superseded by this section for implemented Money behavior.

## Navigation and screen inventory

Four primary destinations: Today, Money, Schedule, Library. Material navigation bar below 600 dp; rail at 600 dp and wider. Subscriptions is a child of Money, with an app-bar back arrow and system Back support. Appearance is an app-bar action. One Quick Add extended FAB opens a ModalBottomSheet. Primary destination state is restored through Navigation Compose.

Phase 0 screens: Today (populated, empty, loading, recoverable error), Money foundation, Subscriptions foundation, Schedule foundation, Library foundation, Quick Add sheet and Appearance dialog. Future editors belong to domains; do not cram full forms into today's list rows.

## Today hierarchy and behavior

Native top app bar -> debug-only data source banner -> greeting and local date -> Up next -> Pending reminders -> This month -> Upcoming payments -> Pick up where you left off. Calendar and reminders lead because they represent immediate commitments. Budget is one tonal surface; the other domains use readable rows and spacing rather than repeated decorative cards. At content width 720 dp, agenda and money/leisure become two columns. Content scrolls and leaves space above the FAB; no fixed-height text rows.

Events display local start time, title and useful location/duration detail; ongoing events say “Now”. Reminders are read-only in the prototype, with a static noninteractive pending icon and explicit overdue text. Subscription rows show exact currency and due label. Library uses type-appropriate progress units. Section actions navigate to the responsible foundation destination. The overview caps counts; full lists are deferred.

The current date and greeting use the device clock and timezone. Debug fixtures are deterministic for a supplied local date. They can naturally show fewer events after their scheduled times; empty sections do not invent activity. Previews use a fixed date. IDR sample spending is a fixture, never a literal in production rendering.

## Quick Add and state language

The sheet presents Expense or income, Transfer, Event or reminder, Subscription, and Library item. It explicitly states that forms are coming in the next phase. Selecting an item opens its foundation screen; no record is saved and no success feedback is fabricated. Dismissing the sheet returns to context. Appearance offers device/light/dark theme plus wallpaper colors on Android 12+; choices persist in DataStore.

Debug mode is visibly labeled “Demo · sample data, never saved”, with Populated/Empty/Personal controls. Personal mode reads actual Room data and carries a foundation-build label. No sample controls or sample records ship in release. No external permissions are requested by the prototype.

Loading uses a named Material progress indicator. Errors explain that data has not changed and offer Try again. Empty sections explain what will appear rather than showing zeros or invented progress. Foundation screens state their status and deferred capabilities. DataStore write failure uses a snackbar; do not wipe financial storage to recover.

## Direction contract

THESIS: A native daily companion, with actionable daily priorities before compact monthly finances, upcoming plans/payments and leisure. Refuse the grid of unrelated dashboard statistics.

OWN-WORLD: Material 3 components; blue-gray surfaces and restrained blue action/container roles in the static scheme, Roboto/Material type roles, grouped rows, one low-elevation budget surface. Wallpaper color can replace the static palette. Light supports daytime use; deliberate dark colors support evening use.

STORY: Read the next commitment, notice pending tasks, check spending headroom and upcoming renewals, then resume downtime. One Quick Add sheet is the common entry point across domains.

FIRST VIEWPORT: App bar, date/greeting, compact Priorities and the monthly financial overview. Debug source disclosure appears only in sample mode. A bottom navigation bar anchors four destinations and an extended FAB sits above it; scrolling reserves room to move content clear of the FAB. Wide screens retain rail and two content columns.

FORM: A code-led extension of the existing native Android Material 3 Operate surface. Authority is the user's Phase 1 attachment, section 1: “Preserve the current project and avoid replacing existing architecture unnecessarily”; section 3: “Redesign the existing Today screen without discarding working behavior” and “Refine the existing design toward a restrained blue-oriented palette while retaining Material You support”; section 12: “Work autonomously through the assigned scope” and “Do not pause for minor implementation decisions.” Those explicit constraints pin the hierarchy, platform, palette refinement and existing visual system. No new world/concept tournament or raster comp was requested or performed; no retrospective seed or approval is claimed. Signature is the priorities-to-budget reading rhythm with useful ledger rows and focused native editors.

FINISH: unreviewed and undocumented is unfinished; this build ends with the finish review, the verdict, DESIGN.md, and every shipping raster carrying its provenance

## Verification scope

Verify phone light/dark, empty state, scroll, all routes, sheet dismissal, stored appearance, system Back and 1.3× font. A wide emulator capture should check rail/two-column behavior if available. No browser rendering stands in for native proof. TalkBack, 2× text, physical-device gesture/performance and contrast across arbitrary dynamic wallpapers remain explicit release checks unless executed and recorded in the handover.
