import { html } from 'lit';

/**
 * CAS 9 — sous-fonctions de rendu, en dehors de toute classe.
 *
 * Le plugin retrouve les classes qui appellent ces fonctions (directement ou via une autre
 * fonction, dans n'importe quel fichier) et utilise LEURS scopedElements :
 *
 *  - renderToolbar() est appelée par MyApp (my-app.js) et TeamPanel (team-panel.js).
 *    <ui-button> et <ui-icon> sont déclarés dans les deux → OK, Cmd+Click → UiButton / UiIcon.
 *    <ui-tooltip> manque dans les deux → un seul avertissement qui cite les deux classes,
 *    et un quick fix par classe : "Add 'ui-tooltip' to MyApp.scopedElements", etc.
 *
 *  - renderUsers() → renderUserRow() → appelée par MyApp uniquement.
 *    Cmd+Click sur <user-card> → UserCard ; sur @user-selected → user-card.js.
 *
 *  - unusedRenderer() n'est appelée par aucune classe : rien n'est vérifié.
 */
export function renderToolbar(title) {
  return html`
    <header>
      <ui-icon name="menu"></ui-icon>
      <ui-button>${title}</ui-button>
      <ui-tooltip>Aide</ui-tooltip>
    </header>
  `;
}

const renderUserRow = (user, onSelect) =>
  html`<li><user-card .user=${user} @user-selected=${onSelect}></user-card></li>`;

export function renderUsers(users, onSelect) {
  return html`<ul>${users.map((user) => renderUserRow(user, onSelect))}</ul>`;
}

export function unusedRenderer() {
  return html`<not-checked-el></not-checked-el>`;
}
