# Sample project – Lit Scoped Elements

Lit + `@open-wc/scoped-elements` components for trying the plugin by hand.

## Open with the plugin

From the plugin's root folder:

```bash
./gradlew runIde      # starts a sandbox WebStorm with the plugin, opened on this project
```

Or install the zip (`build/distributions/`) in your WebStorm and open this folder.

## What you should see

| File | Case | Underlined (not declared) | Cmd/Ctrl+Click to try |
|---|---|---|---|
| `src/my-app.js` | `static scopedElements = {}` field, `_renderFooter()` helper, nested template, comment | `ui-avatar`, `ui-tooltip` | `<ui-button>`, `</ui-button>`, `<user-card>` |
| `src/features/user-card.js` | getter + `...sharedElements` | `ui-button` | `<ui-badge>`, `<ui-icon>` → through the constant |
| `src/features/team-panel.js` | `...super.scopedElements` / inheritance only | `ui-badge` (TeamPanel), `ui-avatar` (SimplePanel) | `<ui-button>` → UiButton through BasePanel |
| `src/features/my-dashboard.ts` | TypeScript | `ui-avatar` | `<ui-badge>` |
| `src/features/renderers.js` | render functions outside any class, called by MyApp and TeamPanel | `ui-tooltip` (missing from both classes, one quick fix per class) | `<ui-button>`, `<user-card>`, `@user-selected` |
| `src/features/notifications.js` | global element (`customElements.define`), `this.fire()` helper, `Event` subclass, template literal name | none | `<app-toast>` → AppToast |
| `src/features/edge-cases.js` | no scoped elements, empty object, tags defined elsewhere, what is not checked | `ui-button` (EmptyScoped), `sl-button` | — |

### From scopedElements to the templates

| Where to click (Cmd/Ctrl+Click) | Result |
|---|---|
| `'ui-button'` key in the `scopedElements` of `src/my-app.js` | popup: MyApp's template + `renderToolbar()` (renderers.js) |
| `'ui-button'` key in `src/features/base-panel.js` | popup: 3 usages in team-panel.js (TeamPanel through `...super`, SimplePanel through inheritance) + renderers.js |

### Events

| Where to click (Cmd/Ctrl+Click) | Result |
|---|---|
| `@user-selected` in `src/my-app.js` | `new CustomEvent('user-selected')` in `user-card.js` |
| `@panel-close` in `src/my-app.js` | `new Event(PANEL_CLOSE)` in `base-panel.js` (name in a constant, inherited class) |
| `'user-selected'` in the `new CustomEvent(...)` of `user-card.js` | popup: `@user-selected` (my-app.js) + `addEventListener` (team-panel.js) |
| `'user-selected'` in the `addEventListener(...)` of `team-panel.js` | `user-card.js` |
| `@click` | nothing fires it → standard WebStorm navigation |
| `@toast-closed` in `src/features/notifications.js` | `this.fire('toast-closed')` in `app-toast.js` (helper) |
| `@toast-shown` in `src/features/notifications.js` | `new ToastShownEvent()` in `app-toast.js` (`Event` subclass) |
| `` `notify` `` in `src/features/notifications.js` | `@notify` in the same template |

### Other features (in `src/features/notifications.js`)

| Action | Result |
|---|---|
| `'ui-icon'` key | greyed out: never used. Alt+Enter → *Remove 'ui-icon' from scopedElements* |
| `@toast-vanished` | reported: nothing in the project fires it |
| `'never-heard'` | reported only when the *Event fired but never listened to* inspection is on (off by default) |
| type `<` in the template | completion: `ui-badge`, `ui-icon` |
| type `@` on `<app-toast …>` | completion: `toast-shown`, `toast-closed` |
| type `.` or an attribute name on `<app-toast …>` | property completion: `.message`, `message` |
| Shift+F6 on the `'ui-badge'` key | also renames `<ui-badge>…</ui-badge>` |
| Alt+F7 on the `'ui-badge'` key | lists the usages in templates |
| hover over `<ui-badge>` | class, file, and registration |
| gutter icons | on `scopedElements` keys, dispatches, and `@event` bindings |

Quick fix: Alt+Enter on an underlined element → *Add 'xxx' to scopedElements*, with the import of the class.

Event helpers and searching `node_modules`: *Settings → Languages & Frameworks → Lit Scoped Elements*.

To hide `sl-button`: *Settings → Editor → Inspections → JavaScript and TypeScript → Lit / Open WC*,
option *Ignored tag names* → `sl-.*`.

These expectations are checked automatically by `src/test/kotlin/io/github/litscoped/SampleProjectTest.kt`.

## Run in the browser (optional)

```bash
npm install
npm start
```
