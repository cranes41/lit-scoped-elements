import { LitElement, html } from 'lit';

export class UiAvatar extends LitElement {
  static properties = {
    initials: { type: String },
    size: { type: String },
    rounded: { type: Boolean, reflect: true },
    statusText: { type: String, attribute: 'status-text' },
    user: { attribute: false },
    _loading: { state: true },
  };

  render() {
    return html`<span class="avatar" title=${this.statusText ?? ''}>${this.initials}</span>`;
  }
}
