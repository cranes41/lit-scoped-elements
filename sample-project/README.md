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
| `src/features/edge-cases.js` | sans scoped elements, objet vide, balises globales, non vérifiés | `ui-button` (EmptyScoped), `sl-button` | — |

### Événements

| Où cliquer (Cmd/Ctrl+Click) | Résultat |
|---|---|
| `@user-selected` dans `src/my-app.js` | `new CustomEvent('user-selected')` dans `user-card.js` |
| `@panel-close` dans `src/my-app.js` | `new Event(PANEL_CLOSE)` dans `base-panel.js` (nom dans une constante, classe héritée) |
| `'user-selected'` dans `new CustomEvent(...)` de `user-card.js` | popup : `@user-selected` (my-app.js) + `addEventListener` (team-panel.js) |
| `'user-selected'` dans `addEventListener(...)` de `team-panel.js` | `user-card.js` |
| `@click` | aucun dispatch → navigation standard de WebStorm |

Quick fix : Alt+Entrée sur un élément souligné → *Add 'xxx' to scopedElements*. L'import n'est pas
ajouté : Alt+Entrée sur le nom de classe pour l'importer.

Pour masquer `sl-button` : *Settings → Editor → Inspections → JavaScript and TypeScript → Lit / Open WC*,
option *Ignored tag names* → `sl-.*`.

Ces attentes sont vérifiées automatiquement par `src/test/kotlin/io/github/litscoped/SampleProjectTest.kt`.

## Lancer dans le navigateur (optionnel)

```bash
npm install
npm start
```
