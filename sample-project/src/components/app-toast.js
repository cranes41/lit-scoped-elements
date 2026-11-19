import { LitElement, html } from 'lit';

/**
 * Élément GLOBAL : enregistré avec customElements.define, pas via scopedElements.
 *
 *  - Les templates qui utilisent <app-toast> ne sont pas soulignés (option
 *    *Ignore globally defined elements* de l'inspection, activée par défaut).
 *  - Cmd/Ctrl+Click sur <app-toast> ouvre cette classe.
 */
export class ToastShownEvent extends Event {
  /** Sous-classe d'Event : le nom vient de super(...). Cmd+Click sur 'toast-shown' → les écouteurs. */
  constructor() {
    super('toast-shown', { bubbles: true, composed: true });
  }
}

export class AppToast extends LitElement {
  static properties = { message: { type: String } };

  /** Helper d'émission (configurable dans Settings → Languages & Frameworks → Lit Scoped Elements). */
  fire(name, detail) {
    this.dispatchEvent(new CustomEvent(name, { detail, bubbles: true, composed: true }));
  }

  firstUpdated() {
    // Émis à l'instanciation de la sous-classe : @toast-shown mène ici.
    this.dispatchEvent(new ToastShownEvent());
  }

  _close() {
    // Émis par le helper : @toast-closed mène ici.
    this.fire('toast-closed', { message: this.message });
  }

  render() {
    return html`<output>${this.message}</output><button @click=${this._close}>×</button>`;
  }
}

customElements.define('app-toast', AppToast);
