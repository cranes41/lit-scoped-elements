import { LitElement, html } from 'lit';

export class UiAvatar extends LitElement {
  static properties = { initials: { type: String } };

  render() {
    return html`<span class="avatar">${this.initials}</span>`;
  }
}
