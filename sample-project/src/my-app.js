import { LitElement, html } from 'lit';
import { ScopedElementsMixin } from '@open-wc/scoped-elements/lit-element.js';
import { UiButton } from './components/ui-button.js';
import { UiIcon } from './components/ui-icon.js';
import { UserCard } from './features/user-card.js';
import { TeamPanel } from './features/team-panel.js';

/**
 * CAS 1 — `static scopedElements = {...}` (forme champ)
 *
 *  - Cmd/Ctrl+Click sur <ui-button>, <ui-icon>, <user-card>… → ouvre la classe.
 *  - Cmd/Ctrl+Click sur la balise fermante </ui-button> fonctionne aussi.
 *  - <ui-avatar> et <ui-tooltip> sont soulignés : absents de scopedElements.
 *    Alt+Entrée → "Add 'ui-avatar' to scopedElements".
 *
 * ÉVÉNEMENTS
 *  - Cmd/Ctrl+Click sur @user-selected → new CustomEvent('user-selected') dans user-card.js.
 *  - Cmd/Ctrl+Click sur @panel-close   → new Event(PANEL_CLOSE) dans base-panel.js
 *    (TeamPanel hérite de BasePanel ; le nom vient d'une constante).
 *  - Cmd/Ctrl+Click sur @click : aucun dispatch dans le projet → navigation standard de WebStorm.
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
      <h1><ui-icon name="home"></ui-icon> Sample app</h1>

      <ui-button @click=${() => this.requestUpdate()}>Refresh</ui-button>

      <!-- Les commentaires sont ignorés : <ui-not-checked></ui-not-checked> -->

      ${this.users.map(
        (user) => html`<user-card .user=${user} @user-selected=${this._onUserSelected}></user-card>`,
      )}

      <ui-avatar initials="XX"></ui-avatar>

      ${this._renderFooter()}

      <team-panel @panel-close=${() => console.log('closed')}></team-panel>
    `;
  }

  _onUserSelected(event) {
    console.log('user selected', event.detail);
  }

  /** Les méthodes helper qui renvoient du html`` sont aussi vérifiées. */
  _renderFooter() {
    return html`<footer><ui-tooltip>missing!</ui-tooltip></footer>`;
  }
}

customElements.define('my-app', MyApp);
