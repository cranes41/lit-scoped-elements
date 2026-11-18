# Lit Scoped Elements – WebStorm plugin

WebStorm support for [Open WC scoped elements](https://open-wc.org/docs/development/scoped-elements/) in Lit components.

## Features

**Go to the element class**: Cmd/Ctrl+Click (or *Go to Declaration*) on a custom element tag in an
`` html`...` `` template opens the class registered for that tag in `scopedElements`. This works on both
opening and closing tags and follows imports.

**Event navigation**
- Cmd/Ctrl+Click on `@my-event` in an `` html`...` `` template lists where the event is fired
  (`new CustomEvent('my-event')`, `new Event('my-event')`). When the tag maps to a known class (through
  `scopedElements`), only that class's dispatches (and its super classes') are shown. If it fires nothing,
  every dispatch in the project is listed, since the event may bubble up from a child.
- Cmd/Ctrl+Click on `'my-event'` in `new CustomEvent('my-event')` lists who listens: `@my-event=` bindings
  in templates and `addEventListener('my-event', ...)` calls.
- Cmd/Ctrl+Click on `'my-event'` in `addEventListener('my-event', ...)` lists where it is fired.
- Event names held in a constant (`new Event(PANEL_CLOSE)`, `const PANEL_CLOSE = 'panel-close'`) are resolved.
- Events with no dispatch in the project, such as `@click`, keep WebStorm's default navigation.

**Sub render functions**: templates written outside the class are supported too: a function such as
`function renderToolbar() { return html`...` }` or `const renderRow = (x) => html`...``, or a variable holding a
template. The plugin finds the classes that call them, directly or through other functions and in any file of the
project, and uses *their* `scopedElements` for navigation, event narrowing and the inspection. A function used by
several components must have its tags declared in each of them. The warning names the classes that are missing a
tag, with one quick fix per class. A function that no class uses is not checked.

**Inspection: "Custom element is not declared in scopedElements"**: checks every `` html`...` `` template in
the class (`render()` and helper methods) and reports custom elements missing from `scopedElements`.
The quick fix (Alt+Enter) adds `'my-tag': MyTag` to the declaration.

```js
class MyCard extends ScopedElementsMixin(LitElement) {
  static scopedElements = { 'my-button': MyButton };

  render() {
    return html`
      <my-button>OK</my-button>   <!-- Cmd+Click -> MyButton -->
      <my-icon></my-icon>         <!-- warning: not declared in MyCard.scopedElements -->
    `;
  }
}
```

The plugin understands:
- `static scopedElements = {...}` and `static get scopedElements() { return {...}; }`
- spreads: `...super.scopedElements`, `...OtherElement.scopedElements`, `...someConstObject`
- inherited `scopedElements` when the subclass doesn't declare its own
- JavaScript and TypeScript

Only classes that declare `scopedElements` or apply `ScopedElementsMixin` (directly or through a super class)
are checked. Interpolations (`${...}`) and HTML comments in templates are ignored. To skip globally registered
elements, add regular expressions under *Settings → Editor → Inspections → JavaScript and TypeScript →
Lit / Open WC → Ignored tag names*.

## Build

Requires JDK 25. Gradle downloads one if none is installed.

```bash
./gradlew buildPlugin            # -> build/distributions/lit-scoped-elements-<version>.zip
./gradlew test
./gradlew runIde                 # starts a sandbox WebStorm with the plugin
```

To build against a local WebStorm instead of downloading it, and reuse its bundled JDK:

```bash
export JAVA_HOME=~/Applications/WebStorm.app/Contents/jbr/Contents/Home
./gradlew buildPlugin -PlocalIdePath=$HOME/Applications/WebStorm.app \
  -Porg.gradle.java.installations.paths=$JAVA_HOME
```

## Install

*Settings → Plugins → ⚙ → Install Plugin from Disk…* → select the zip from `build/distributions`.
