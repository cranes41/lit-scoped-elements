import { LitElement, html, css } from 'lit';
import { ScopedElementsMixin } from '@open-wc/scoped-elements/lit-element.js';
import { UiButton } from '../components/ui-button.js';

/**
 * CASE 5 — no scoped elements: nothing is checked.
 * <anything-goes> is NOT underlined.
 */
export class PlainElement extends LitElement {
  render() {
    return html`<anything-goes></anything-goes>`;
  }
}

/**
 * CASE 6 — empty scopedElements: everything is reported.
 * Alt+Enter on <ui-button> → adds `'ui-button': UiButton` to the empty object.
 */
export class EmptyScoped extends ScopedElementsMixin(LitElement) {
  static scopedElements = {};

  render() {
    return html`<ui-button>Go</ui-button>`;
  }
}

/**
 * CASE 7 — tags defined outside the project.
 * <sl-button> is underlined by default. Add `sl-.*` to
 * Settings → Editor → Inspections → Lit / Open WC → Ignored tag names
 * to hide it.
 */
export class WithGlobalElements extends ScopedElementsMixin(LitElement) {
  static scopedElements = { 'ui-button': UiButton };

  render() {
    return html`<ui-button>OK</ui-button><sl-button>Shoelace</sl-button>`;
  }
}

/**
 * CASE 8 — what is NOT checked:
 *  - css`` templates (only html``)
 *  - the content of ${...} interpolations that are not html`` templates
 *  - templates outside a class that no class renders
 */
export class NotChecked extends ScopedElementsMixin(LitElement) {
  static scopedElements = {};
  static styles = css`:host { --x: '<not-a-tag>'; }`;

  render() {
    return html`<div title=${'<not-a-tag-either>'}></div>`;
  }
}

export const outsideClass = () => html`<outside-el></outside-el>`;
