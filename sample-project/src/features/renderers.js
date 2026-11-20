import { html } from 'lit';

/**
 * CASE 9 — render functions outside any class.
 *
 * The plugin finds the classes calling these functions (directly or through another
 * function, in any file) and uses THEIR scopedElements:
 *
 *  - renderToolbar() is called by MyApp (my-app.js) and TeamPanel (team-panel.js).
 *    <ui-button> and <ui-icon> are declared in both → OK, Cmd+Click → UiButton / UiIcon.
 *    <ui-tooltip> is missing from both → one warning naming both classes,
 *    and one quick fix per class: "Add 'ui-tooltip' to MyApp.scopedElements", etc.
 *
 *  - renderUsers() → renderUserRow() → called by MyApp only.
 *    Cmd+Click on <user-card> → UserCard; on @user-selected → user-card.js.
 *
 *  - unusedRenderer() is called by no class: nothing is checked.
 */
export function renderToolbar(title) {
  return html`
    <header>
      <ui-icon name="menu"></ui-icon>
      <ui-button>${title}</ui-button>
      <ui-tooltip>Help</ui-tooltip>
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
