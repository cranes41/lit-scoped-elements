import { LitElement, html } from 'lit';
import { ScopedElementsMixin } from '@open-wc/scoped-elements/lit-element.js';
import { UiBadge } from '../components/ui-badge.js';
import { UiIcon } from '../components/ui-icon.js';
import '../components/app-toast.js';

/**
 * CASE 10 — global elements, event helpers, Event subclasses, usage inspections.
 *
 *  NAVIGATION
 *  - Cmd/Ctrl+Click on <app-toast>   → AppToast (defined with customElements.define).
 *  - Cmd/Ctrl+Click on @toast-closed → this.fire('toast-closed') in app-toast.js.
 *  - Cmd/Ctrl+Click on @toast-shown  → new ToastShownEvent() in app-toast.js.
 *  - Cmd/Ctrl+Click on `notify`      → @notify below (name in a template literal).
 *  - Hover over <ui-badge>           → class, file, and where it is registered.
 *
 *  INSPECTIONS
 *  - 'ui-icon' in scopedElements is greyed out: never used. Alt+Enter → remove it.
 *  - @toast-vanished is reported: nothing in the project fires it.
 *  - 'never-heard': nothing listens to it (inspection off by default,
 *    Settings → Editor → Inspections → Lit / Open WC → "Event fired but never listened to").
 *
 *  COMPLETION
 *  - Type `<` in the template → suggests ui-badge, ui-icon.
 *  - Type `@` on <app-toast …> → suggests toast-shown, toast-closed.
 *
 *  RENAME
 *  - Shift+F6 on the 'ui-badge' key → also renames <ui-badge>…</ui-badge> in the template.
 */export class NotificationCenter extends ScopedElementsMixin(LitElement) {
  static scopedElements = {
    'ui-badge': UiBadge,
    'ui-icon': UiIcon,
  };

  _notify() {
    this.dispatchEvent(new CustomEvent(`notify`, { bubbles: true }));
    this.dispatchEvent(new CustomEvent('never-heard'));
  }

  render() {
    return html`
      <ui-badge>2</ui-badge>
      <app-toast
        message="Saved"
        @toast-closed=${this._notify}
        @toast-shown=${() => console.log('shown')}
        @toast-vanished=${() => console.log('never')}
      ></app-toast>
      <div @notify=${() => console.log('notified')}></div>
    `;
  }
}
