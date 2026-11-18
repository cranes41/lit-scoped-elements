import { LitElement, html } from 'lit';
import { ScopedElementsMixin } from '@open-wc/scoped-elements/lit-element.js';
import { UiBadge } from '../components/ui-badge.js';
import { UiIcon } from '../components/ui-icon.js';

/**
 * CAS 4 — TypeScript
 *
 *  - Cmd+Click sur <ui-badge> → UiBadge.
 *  - <ui-avatar> est souligné.
 */
export class MyDashboard extends ScopedElementsMixin(LitElement) {
  static scopedElements = {
    'ui-badge': UiBadge,
    'ui-icon': UiIcon,
  };

  count: number = 42;

  override render() {
    return html`
      <ui-icon name="chart"></ui-icon>
      <ui-badge>${this.count}</ui-badge>
      <ui-avatar initials="TS"></ui-avatar>
    `;
  }
}
