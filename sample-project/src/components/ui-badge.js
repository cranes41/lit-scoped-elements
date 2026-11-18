import { LitElement, html } from 'lit';

export class UiBadge extends LitElement {
  render() {
    return html`<strong><slot></slot></strong>`;
  }
}
