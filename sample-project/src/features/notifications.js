import { LitElement, html } from 'lit';
import { ScopedElementsMixin } from '@open-wc/scoped-elements/lit-element.js';
import { UiBadge } from '../components/ui-badge.js';
import { UiIcon } from '../components/ui-icon.js';
import '../components/app-toast.js';

/**
 * CAS 9 — éléments globaux, helpers d'émission, sous-classes d'Event, inspections d'usage.
 *
 *  NAVIGATION
 *  - Cmd/Ctrl+Click sur <app-toast>   → AppToast (défini par customElements.define).
 *  - Cmd/Ctrl+Click sur @toast-closed → this.fire('toast-closed') dans app-toast.js.
 *  - Cmd/Ctrl+Click sur @toast-shown  → new ToastShownEvent() dans app-toast.js.
 *  - Cmd/Ctrl+Click sur `notify`      → @notify plus bas (nom en template literal).
 *  - Survol de <ui-badge>             → classe, fichier, et où elle est enregistrée.
 *
 *  INSPECTIONS
 *  - 'ui-icon' dans scopedElements est grisé : jamais utilisé. Alt+Entrée → le retirer.
 *  - @toast-vanished est signalé : rien ne l'émet dans le projet.
 *  - 'never-heard' : personne ne l'écoute (inspection désactivée par défaut,
 *    Settings → Editor → Inspections → Lit / Open WC → "Event fired but never listened to").
 *
 *  COMPLÉTION
 *  - Taper `<` dans le template → propose ui-badge, ui-icon.
 *  - Taper `@` sur <app-toast …> → propose toast-shown, toast-closed.
 *
 *  RENOMMAGE
 *  - Shift+F6 sur la clé 'ui-badge' → renomme aussi <ui-badge>…</ui-badge> dans le template.
 */
export class NotificationCenter extends ScopedElementsMixin(LitElement) {
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
