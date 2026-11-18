import { LitElement, html } from 'lit';
import { ScopedElementsMixin } from '@open-wc/scoped-elements/lit-element.js';
import { UiAvatar } from '../components/ui-avatar.js';
import { sharedElements } from '../components/shared-elements.js';

/**
 * CAS 2 — `static get scopedElements()` (forme getter) + spread d'une constante
 *
 *  - <ui-icon> et <ui-badge> viennent de `...sharedElements` → OK, et Cmd+Click
 *    ouvre UiIcon / UiBadge.
 *  - <ui-button> est souligné (pas déclaré ici).
 */
export class UserCard extends ScopedElementsMixin(LitElement) {
  static get scopedElements() {
    return {
      ...sharedElements,
      'ui-avatar': UiAvatar,
    };
  }

  static properties = { user: { type: Object } };

  /**
   * ÉVÉNEMENTS — Cmd+Click sur 'user-selected' ci-dessous → liste les écouteurs :
   * `@user-selected` dans my-app.js et `addEventListener('user-selected')` dans team-panel.js.
   */
  _select() {
    this.dispatchEvent(
      new CustomEvent('user-selected', { detail: this.user, bubbles: true, composed: true }),
    );
  }

  render() {
    const { name, initials } = this.user ?? {};
    return html`
      <ui-avatar .initials=${initials} @click=${this._select}></ui-avatar>
      <span>${name}</span>
      <ui-badge>admin</ui-badge>
      <ui-icon name="mail"></ui-icon>
      <ui-button>Contact</ui-button>
    `;
  }
}
