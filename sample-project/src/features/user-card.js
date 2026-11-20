import { LitElement, html } from 'lit';
import { ScopedElementsMixin } from '@open-wc/scoped-elements/lit-element.js';
import { UiAvatar } from '../components/ui-avatar.js';
import { sharedElements } from '../components/shared-elements.js';

/**
 * CASE 2 — `static get scopedElements()` (getter form) + spread of a constant
 *
 *  - <ui-icon> and <ui-badge> come from `...sharedElements` → OK, and Cmd+Click
 *    opens UiIcon / UiBadge.
 *  - <ui-button> is underlined (not declared here).
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
   * EVENTS — Cmd+Click on 'user-selected' below → lists the listeners:
   * `@user-selected` in my-app.js and `addEventListener('user-selected')` in team-panel.js.
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
