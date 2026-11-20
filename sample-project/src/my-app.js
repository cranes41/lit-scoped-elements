import { LitElement, html } from 'lit';
import { ScopedElementsMixin } from '@open-wc/scoped-elements/lit-element.js';
import { UiButton } from './components/ui-button.js';
import { UiIcon } from './components/ui-icon.js';
import { UserCard } from './features/user-card.js';
import { TeamPanel } from './features/team-panel.js';
import { renderToolbar, renderUsers } from './features/renderers.js';

/**
 * CASE 1 — `static scopedElements = {...}` (field form)
 *
 *  - Cmd/Ctrl+Click on <ui-button>, <ui-icon>, <user-card>… → opens the class.
 *  - Cmd/Ctrl+Click on the closing tag </ui-button> works too.
 *  - <ui-avatar> and <ui-tooltip> are underlined: missing from scopedElements.
 *    Alt+Enter → "Add 'ui-avatar' to scopedElements".
 *
 * EVENTS
 *  - Cmd/Ctrl+Click on @user-selected → new CustomEvent('user-selected') in user-card.js.
 *  - Cmd/Ctrl+Click on @panel-close   → new Event(PANEL_CLOSE) in base-panel.js
 *    (TeamPanel extends BasePanel; the name comes from a constant).
 *  - Cmd/Ctrl+Click on @click: nothing fires it in the project → standard WebStorm navigation.
 */
export class MyApp extends ScopedElementsMixin(LitElement) {
  static scopedElements = {
    'ui-button': UiButton,
    'ui-icon': UiIcon,
    'user-card': UserCard,
    'team-panel': TeamPanel,
  };

  static properties = { users: { type: Array } };

  constructor() {
    super();
    this.users = [
      { name: 'Ada Lovelace', initials: 'AL' },
      { name: 'Alan Turing', initials: 'AT' },
    ];
  }

  render() {
    return html`
      ${renderToolbar('Sample app')}
      <h1><ui-icon name="home"></ui-icon> Sample app</h1>

      <ui-button @click=${() => this.requestUpdate()}>Refresh</ui-button>

      <!-- Comments are ignored: <ui-not-checked></ui-not-checked> -->

      ${this.users.map(
        (user) => html`<user-card .user=${user} @user-selected=${this._onUserSelected}></user-card>`,
      )}

      <ui-avatar initials="XX"></ui-avatar>

      ${this._renderFooter()}

      ${renderUsers(this.users, this._onUserSelected)}

      <team-panel @panel-close=${() => console.log('closed')}></team-panel>
    `;
  }

  _onUserSelected(event) {
    console.log('user selected', event.detail);
  }

  /** Helper methods returning html`` are checked too. */
  _renderFooter() {
    return html`<footer><ui-tooltip>missing!</ui-tooltip></footer>`;
  }
}

customElements.define('my-app', MyApp);
