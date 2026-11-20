import { LitElement, html } from 'lit';
import { ScopedElementsMixin } from '@open-wc/scoped-elements/lit-element.js';
import { UiButton } from '../components/ui-button.js';
import { UiIcon } from '../components/ui-icon.js';

/** Event name in a constant: navigation resolves it too. */
export const PANEL_CLOSE = 'panel-close';

/** Base class: its scopedElements are inherited by subclasses. */
export class BasePanel extends ScopedElementsMixin(LitElement) {
  static scopedElements = {
    'ui-button': UiButton,
    'ui-icon': UiIcon,
  };

  /** EVENTS — Cmd+Click on PANEL_CLOSE here goes to the constant (standard JS navigation). */
  close() {
    this.dispatchEvent(new Event(PANEL_CLOSE, { bubbles: true, composed: true }));
  }

  render() {
    return html`<section><ui-icon name="panel"></ui-icon><slot></slot></section>`;
  }
}
