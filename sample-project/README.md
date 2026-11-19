# Projet de test – Lit Scoped Elements

Composants Lit + `@open-wc/scoped-elements` pour tester le plugin à la main.

## Ouvrir avec le plugin

Depuis la racine du plugin :

```bash
./gradlew runIde      # lance un WebStorm sandbox avec le plugin, ouvert sur ce projet
```

Ou installe le zip (`build/distributions/`) dans ton WebStorm et ouvre ce dossier.

## Ce qu'on doit voir

| Fichier | Cas | Soulignés (non déclarés) | Cmd/Ctrl+Click à essayer |
|---|---|---|---|
| `src/my-app.js` | champ `static scopedElements = {}`, helper `_renderFooter()`, template imbriqué, commentaire | `ui-avatar`, `ui-tooltip` | `<ui-button>`, `</ui-button>`, `<user-card>` |
| `src/features/user-card.js` | getter + `...sharedElements` | `ui-button` | `<ui-badge>`, `<ui-icon>` → via la constante |
| `src/features/team-panel.js` | `...super.scopedElements` / héritage seul | `ui-badge` (TeamPanel), `ui-avatar` (SimplePanel) | `<ui-button>` → UiButton via BasePanel |
| `src/features/my-dashboard.ts` | TypeScript | `ui-avatar` | `<ui-badge>` |
| `src/features/renderers.js` | sous-fonctions de rendu hors classe, appelées par MyApp et TeamPanel | `ui-tooltip` (manque dans les 2 classes, un quick fix par classe) | `<ui-button>`, `<user-card>`, `@user-selected` |
| `src/features/notifications.js` | élément global (`customElements.define`), helper `this.fire()`, sous-classe d'`Event`, nom en template literal | aucun | `<app-toast>` → AppToast |
| `src/features/edge-cases.js` | sans scoped elements, objet vide, balises globales, non vérifiés | `ui-button` (EmptyScoped), `sl-button` | — |

### Depuis scopedElements vers les templates

| Où cliquer (Cmd/Ctrl+Click) | Résultat |
|---|---|
| clé `'ui-button'` dans `scopedElements` de `src/my-app.js` | popup : le template de MyApp + `renderToolbar()` (renderers.js) |
| clé `'ui-button'` dans `src/features/base-panel.js` | popup : 3 usages dans team-panel.js (TeamPanel via `...super`, SimplePanel par héritage) + renderers.js |

### Événements

| Où cliquer (Cmd/Ctrl+Click) | Résultat |
|---|---|
| `@user-selected` dans `src/my-app.js` | `new CustomEvent('user-selected')` dans `user-card.js` |
| `@panel-close` dans `src/my-app.js` | `new Event(PANEL_CLOSE)` dans `base-panel.js` (nom dans une constante, classe héritée) |
| `'user-selected'` dans `new CustomEvent(...)` de `user-card.js` | popup : `@user-selected` (my-app.js) + `addEventListener` (team-panel.js) |
| `'user-selected'` dans `addEventListener(...)` de `team-panel.js` | `user-card.js` |
| `@click` | aucun dispatch → navigation standard de WebStorm |

| `@toast-closed` dans `src/features/notifications.js` | `this.fire('toast-closed')` dans `app-toast.js` (helper) |
| `@toast-shown` dans `src/features/notifications.js` | `new ToastShownEvent()` dans `app-toast.js` (sous-classe d'`Event`) |
| `` `notify` `` dans `src/features/notifications.js` | `@notify` dans le même template |

### Autres fonctionnalités (dans `src/features/notifications.js`)

| Action | Résultat |
|---|---|
| clé `'ui-icon'` | grisée : jamais utilisée. Alt+Entrée → *Remove 'ui-icon' from scopedElements* |
| `@toast-vanished` | signalé : rien ne l'émet dans le projet |
| `'never-heard'` | signalé seulement si l'inspection *Event fired but never listened to* est activée (désactivée par défaut) |
| taper `<` dans le template | complétion : `ui-badge`, `ui-icon` |
| taper `@` sur `<app-toast …>` | complétion : `toast-shown`, `toast-closed` |
| Shift+F6 sur la clé `'ui-badge'` | renomme aussi `<ui-badge>…</ui-badge>` |
| Alt+F7 sur la clé `'ui-badge'` | liste les usages dans les templates |
| survol de `<ui-badge>` | classe, fichier et enregistrement |
| icônes dans la gouttière | sur les clés de `scopedElements`, les dispatches et les `@event` |

Quick fix : Alt+Entrée sur un élément souligné → *Add 'xxx' to scopedElements*, avec l'import de la classe.

Helpers d'émission et recherche dans `node_modules` : *Settings → Languages & Frameworks → Lit Scoped Elements*.

Pour masquer `sl-button` : *Settings → Editor → Inspections → JavaScript and TypeScript → Lit / Open WC*,
option *Ignored tag names* → `sl-.*`.

Ces attentes sont vérifiées automatiquement par `src/test/kotlin/io/github/litscoped/SampleProjectTest.kt`.

## Lancer dans le navigateur (optionnel)

```bash
npm install
npm start
```
