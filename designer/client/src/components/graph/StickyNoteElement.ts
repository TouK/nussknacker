import { dia } from "jointjs";
import { Events } from "./types";
import { MARKDOWN_EDITOR_NAME } from "./EspNode/stickyNote";

export const StickyNoteElement = (defaults?: any, protoProps?: any) =>
    dia.Element.define("stickyNote.StickyNoteElement", defaults, protoProps);

export const StickyNoteElementView = dia.ElementView.extend({
    events: {
        "change textarea": "onChange",
        "click textarea": "stopPropagation",
        "keydown textarea": "selectAll",
        "focusout textarea": "onChange",
        "dblclick .sticky-note-content": "showEditor",
    },

    stopPropagation: function (evt, x, y) {
        evt.stopPropagation();
    },

    showEditor: function (evt, x, y) {
        evt.stopPropagation();
        this.model.attr(`${MARKDOWN_EDITOR_NAME}/props/disabled`, false);
        evt.currentTarget.childNodes.item("textarea").focus({ preventScroll: true });
    },

    selectAll: function (evt, x, y) {
        if (evt.code === "KeyA") {
            if (evt.ctrlKey) {
                evt.preventDefault();
                evt.target.select();
            }
        }
    },

    onChange: function (evt) {
        this.model.trigger(Events.CELL_CONTENT_UPDATED, this.model, evt.target.value);
        this.model.attr(`${MARKDOWN_EDITOR_NAME}/props/value`, evt.target.value);
        this.model.attr(`${MARKDOWN_EDITOR_NAME}/props/disabled`, true);
    },
});
