import { LitElement, html } from 'lit';
import { ScopedElementsMixin } from '@open-wc/scoped-elements/lit-element.js';
import { UiButton } from '../components/ui-button.js';
import { UiIcon } from '../components/ui-icon.js';

/** Nom d'événement dans une constante : la navigation le résout aussi. */
export const PANEL_CLOSE = 'panel-close';

/** Classe de base : ses scopedElements sont hérités par les sous-classes. */
export class BasePanel extends ScopedElementsMixin(LitElement) {
  static scopedElements = {
    'ui-button': UiButton,
    'ui-icon': UiIcon,
  };

  /** ÉVÉNEMENTS — Cmd+Click sur PANEL_CLOSE ici va à la constante (navigation JS standard). */
  close() {
    this.dispatchEvent(new Event(PANEL_CLOSE, { bubbles: true, composed: true }));
  }

  render() {
    return html`<section><ui-icon name="panel"></ui-icon><slot></slot></section>`;
  }
}
