import { html } from 'lit';
import { BasePanel } from './base-panel.js';
import { UiAvatar } from '../components/ui-avatar.js';
import { renderToolbar } from './renderers.js';

/**
 * CASE 3a — `...super.scopedElements`
 *
 *  - <ui-button> / <ui-icon> come from BasePanel → OK, Cmd+Click → UiButton / UiIcon.
 *  - <ui-avatar> is declared locally.
 *  - <ui-badge> is underlined.
 */
export class TeamPanel extends BasePanel {
  static scopedElements = {
    ...super.scopedElements,
    'ui-avatar': UiAvatar,
  };

  /**
   * EVENTS — Cmd+Click on 'user-selected' below → user-card.js (where it is fired).
   */
  connectedCallback() {
    super.connectedCallback();
    this.addEventListener('user-selected', (event) => console.log('selected', event.detail));
  }

  render() {
    return html`
      ${renderToolbar('Team')}
      <ui-icon name="team"></ui-icon>
      <ui-button @click=${this.close}>Close</ui-button>
      <ui-avatar initials="AL"></ui-avatar>
      <ui-badge>3</ui-badge>
      <ui-button>Invite</ui-button>
    `;
  }
}

/**
 * CASE 3b — no declaration of its own: BasePanel's scopedElements apply.
 *
 *  - <ui-button> OK, <ui-avatar> underlined.
 *  - No quick fix here (this class has no scopedElements object).
 */
export class SimplePanel extends BasePanel {
  render() {
    return html`<ui-button>Close</ui-button><ui-avatar initials="?"></ui-avatar>`;
  }
}
