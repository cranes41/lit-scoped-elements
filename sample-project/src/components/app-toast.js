import { LitElement, html } from 'lit';

/**
 * GLOBAL element: registered with customElements.define, not through scopedElements.
 *
 *  - Templates using <app-toast> are not underlined (the inspection's
 *    *Ignore elements defined globally* option, on by default).
 *  - Cmd/Ctrl+Click on <app-toast> opens this class.
 */
export class ToastShownEvent extends Event {
  /** Event subclass: the name comes from super(...). Cmd+Click on 'toast-shown' → the listeners. */
  constructor() {
    super('toast-shown', { bubbles: true, composed: true });
  }
}

export class AppToast extends LitElement {
  static properties = { message: { type: String } };

  /** Event helper (configurable in Settings → Languages & Frameworks → Lit Scoped Elements). */
  fire(name, detail) {
    this.dispatchEvent(new CustomEvent(name, { detail, bubbles: true, composed: true }));
  }

  firstUpdated() {
    // Fired by instantiating the subclass: @toast-shown leads here.
    this.dispatchEvent(new ToastShownEvent());
  }

  _close() {
    // Fired through the helper: @toast-closed leads here.
    this.fire('toast-closed', { message: this.message });
  }

  render() {
    return html`<output>${this.message}</output><button @click=${this._close}>×</button>`;
  }
}

customElements.define('app-toast', AppToast);
