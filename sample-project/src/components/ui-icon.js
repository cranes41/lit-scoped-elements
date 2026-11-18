import { LitElement, html } from 'lit';

export class UiIcon extends LitElement {
  static properties = { name: { type: String } };

  render() {
    return html`<span>[${this.name}]</span>`;
  }
}
