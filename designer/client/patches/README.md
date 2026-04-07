### ts-spel:

https://github.com/JulianWielga/ts-spel/tree/nu-changes

`3b191375a2d57f69ed93f1fe6fbd10a4b50d60ee`

### @glideapps/glide-data-grid:

Enable column drag & drop from group headers for single-column groups.
The library only allows dragging columns by their regular header (`kind === "header"`).
This patch adds a separate branch for `kind === "group-header"` that initiates drag
only when the group contains exactly one column, so multi-column groups are unaffected.

### react-draggable:

Fix `findDOMNode` deprecation warning in React 18. `Draggable.findDOMNode()` used nullish
coalescing (`??`) causing fallback to `ReactDOM.findDOMNode(this)` even when `nodeRef` was
passed (with `current: null` during mount). Changed to match `DraggableCore.findDOMNode()`
which correctly uses a truthy check on `nodeRef` existence.

https://github.com/react-grid-layout/react-draggable/issues/749
