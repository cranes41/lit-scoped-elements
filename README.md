# Lit Scoped Elements – WebStorm plugin

WebStorm support for [Open WC scoped elements](https://open-wc.org/docs/development/scoped-elements/) in Lit components.

## Features

```js
class MyCard extends ScopedElementsMixin(LitElement) {
  static scopedElements = { 'my-button': MyButton };   // Cmd+Click / Alt+F7 / Shift+F6 on the key

  render() {
    return html`
      <my-button @pressed=${this.onPress}>OK</my-button>   <!-- Cmd+Click: tag -> MyButton, @pressed -> dispatch -->
      <my-icon></my-icon>                                  <!-- warning: not declared in MyCard.scopedElements -->
    `;
  }
}
```

### Navigation
- **Tag → class**: Cmd/Ctrl+Click on `<my-button>` or `</my-button>` in an `` html`...` `` template opens the class
  registered in `scopedElements`. When no scoped registry has the tag, it opens the class registered globally with
  `customElements.define('my-button', …)` or `@customElement('my-button')`.
- **Key → templates**: Cmd/Ctrl+Click on a `scopedElements` key (`'my-button'`) lists the tags using it in the
  templates that entry applies to. Cmd+Click on the value (`MyButton`) still goes to the class.
- **Events**:
  - `@my-event` in a template → where it is fired. When the tag's class is known, its own dispatches (and its super
    classes') come first; otherwise every dispatch is listed, since events bubble.
  - the name in a dispatch → who listens (`@my-event` bindings and `addEventListener('my-event')`).
  - the name in `addEventListener('my-event')` → where it is fired.
- **Gutter icons** for the same links: on `scopedElements` keys, dispatch names, `addEventListener` names and
  `@event` attributes.
- **Hover** on a tag shows the class it resolves to, its file, and where it is registered.

### Refactoring
- **Find Usages** (Alt+F7) on a `scopedElements` key lists the tags using it.
- **Rename** (Shift+F6) a key renames the matching opening and closing tags in every template it applies to.

### Completion
- `<` in a template suggests the tags available in `scopedElements` (inherited and spread entries included).
- `@` on an element suggests the events its class fires.
- Attribute names on an element suggest its class's reactive properties (`static properties`, the
  `static get properties()` getter, or `@property()`, inherited ones included; `state` properties are left out):
  `.prop=${…}` bindings, `attribute=""` (the lowercased name or the `attribute` option), and `?attribute=${…}` for
  `Boolean` properties. Properties already bound on the element are not suggested again.

### Inspections (Settings → Editor → Inspections → JavaScript and TypeScript → Lit / Open WC)
- **Custom element is not declared in scopedElements**: the quick fix adds the entry with the real class (one
  registered elsewhere for that tag, a global definition, or a class named after the tag) and imports it.
  Globally defined elements are skipped unless you turn that option off. You can also ignore tag names by regex.
- **Unused scopedElements entry**: greys out entries no template uses, with a quick fix to remove them.
- **Event listened to but never fired** (weak warning): an `@my-event` binding that nothing in the project fires,
  often a typo. DOM events (`click`, `input`, …) are never reported.
- **Event fired but never listened to** (off by default).

### What the plugin understands
- `static scopedElements = {...}` and `static get scopedElements() { return {...}; }`, including getters with
  several `return` branches (each branch is a possible registry).
- Spreads: `...super.scopedElements`, `...OtherElement.scopedElements`, `...sharedConstObject`.
- Inheritance, including through mixins (`class A extends SomeMixin(Base)`), also when the mixin is applied in a
  variable (`const Mixed = SomeMixin(Base); class A extends Mixed {}`, imported or chained). A template in `Base.render()` also
  belongs to subclasses that don't redefine `render()` or call `super.render()`, with *their* `scopedElements`.
- **Sub render functions**: a template in a function or variable outside the class
  (`function renderRow() { return html`...` }`) belongs to every class calling it, directly or through other
  functions, in any file. A function no class calls is not checked.
- Ways to fire an event: `new CustomEvent('x')` / `new Event('x')`, helper calls such as `this.fire('x')`, and
  `Event` subclasses passing the name to `super('x')` (fired where they are instantiated).
- Event names: strings, `` `template` `` literals, and constants: variables, static fields, object properties
  (`Events.CHANGE`, also nested and `as const`), and enum members.
- Declared events, used when no dispatch is visible (for example in a library): JSDoc `@fires my-event` or
  `@event my-event` on the element class (the custom elements manifest convention), and entries of a TypeScript
  event map (`interface HTMLElementEventMap { 'my-event': CustomEvent }`). They count for navigation, completion,
  and the *never fired* inspection.
- JavaScript and TypeScript. Interpolations (`${...}`) and HTML comments in templates are ignored.

### Settings (Settings → Languages & Frameworks → Lit Scoped Elements)
- **Event helper functions**: functions whose first argument is a fired event's name (default `fire`, `emit`).
- **Event sources**: files and folders also searched for the code firing events, for example one package of
  `node_modules` (lighter than *Search libraries*) or a folder outside the project, such as a sibling package of a
  monorepo. Folders outside the project are indexed as the *Lit event sources* library (under *External Libraries*).
- **Search libraries**: also search `node_modules` for dispatches and global element definitions.

### Limits
- Tags built at runtime (`unsafeStatic`) aren't seen.
- A helper called with a constant event name is only found if its name contains `event`, `fire`, `emit`,
  `dispatch` or `listen`.

## Build

Requires JDK 25. Gradle downloads one if none is installed.

```bash
./gradlew buildPlugin            # -> build/distributions/lit-scoped-elements-<version>.zip
./gradlew test
./gradlew runIde                 # starts a sandbox WebStorm with the plugin
./gradlew runIde -Pscreenshots   # regenerates the Marketplace screenshots in build/screenshots, then exits
```

`-Pscreenshots` adds a development-only tour (`src/screenshots`) that runs on a copy of `sample-project`, triggers
each feature with the IDE's own actions, and paints the IDE windows to PNG. The published plugin never contains it.
The current screenshots are in [`docs/screenshots`](docs/screenshots).

To build against a local WebStorm instead of downloading it, and reuse its bundled JDK:

```bash
export JAVA_HOME=~/Applications/WebStorm.app/Contents/jbr/Contents/Home
./gradlew buildPlugin -PlocalIdePath=$HOME/Applications/WebStorm.app \
  -Porg.gradle.java.installations.paths=$JAVA_HOME
```

## Install

*Settings → Plugins → ⚙ → Install Plugin from Disk…* → select the zip from `build/distributions`.
