import { LitElement, html, css } from 'lit';

export class UiButton extends LitElement {
  static styles = css`button { padding: 4px 12px; }`;

  render() {
    return html`<button><slot></slot></button>`;
  }
}
