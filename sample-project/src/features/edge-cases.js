import { LitElement, html, css } from 'lit';
import { ScopedElementsMixin } from '@open-wc/scoped-elements/lit-element.js';
import { UiButton } from '../components/ui-button.js';

/**
 * CAS 5 — pas de scoped elements : aucune vérification.
 * <anything-goes> n'est PAS souligné.
 */
export class PlainElement extends LitElement {
  render() {
    return html`<anything-goes></anything-goes>`;
  }
}

/**
 * CAS 6 — scopedElements vide : tout est signalé.
 * Alt+Entrée sur <ui-button> → ajoute `'ui-button': UiButton` dans l'objet vide.
 */
export class EmptyScoped extends ScopedElementsMixin(LitElement) {
  static scopedElements = {};

  render() {
    return html`<ui-button>Go</ui-button>`;
  }
}

/**
 * CAS 7 — balises globales à ignorer.
 * <sl-button> est souligné par défaut. Ajouter `sl-.*` dans
 * Settings → Editor → Inspections → Lit / Open WC → Ignored tag names
 * pour le faire disparaître.
 */
export class WithGlobalElements extends ScopedElementsMixin(LitElement) {
  static scopedElements = { 'ui-button': UiButton };

  render() {
    return html`<ui-button>OK</ui-button><sl-button>Shoelace</sl-button>`;
  }
}

/**
 * CAS 8 — ce qui n'est PAS vérifié :
 *  - les templates css`` (pas html``)
 *  - le contenu des interpolations ${...} qui ne sont pas des templates html``
 *  - les templates hors classe
 */
export class NotChecked extends ScopedElementsMixin(LitElement) {
  static scopedElements = {};
  static styles = css`:host { --x: '<not-a-tag>'; }`;

  render() {
    return html`<div title=${'<not-a-tag-either>'}></div>`;
  }
}

export const outsideClass = () => html`<outside-el></outside-el>`;
