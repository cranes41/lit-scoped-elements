import { html } from 'lit';
import { BasePanel } from './base-panel.js';
import { UiAvatar } from '../components/ui-avatar.js';
import { renderToolbar } from './renderers.js';

/**
 * CAS 3a — `...super.scopedElements`
 *
 *  - <ui-button> / <ui-icon> viennent de BasePanel → OK, Cmd+Click → UiButton / UiIcon.
 *  - <ui-avatar> est déclaré localement.
 *  - <ui-badge> est souligné.
 */
export class TeamPanel extends BasePanel {
  static scopedElements = {
    ...super.scopedElements,
    'ui-avatar': UiAvatar,
  };

  /**
   * ÉVÉNEMENTS — Cmd+Click sur 'user-selected' ci-dessous → user-card.js (là où il est émis).
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
 * CAS 3b — aucune déclaration propre : les scopedElements de BasePanel s'appliquent.
 *
 *  - <ui-button> OK, <ui-avatar> souligné.
 *  - Pas de quick fix ici (pas d'objet scopedElements dans cette classe).
 */
export class SimplePanel extends BasePanel {
  render() {
    return html`<ui-button>Close</ui-button><ui-avatar initials="?"></ui-avatar>`;
  }
}
