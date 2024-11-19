import { dia } from "jointjs";
import { Events } from "./types";
import { MARKDOWN_EDITOR_NAME } from "./EspNode/stickyNote";

export const StickyNoteElement = (defaults?: any, protoProps?: any) =>
    dia.Element.define("stickyNote.StickyNoteElement", defaults, protoProps);

export const StickyNoteElementView = dia.ElementView.extend({
    events: {
        "change textarea": "onChange",
        "click textarea": "stopPropagation",
        "focus textarea": "stopPropagation",
        "focusout textarea": "onChange",
    },

    pointerdblclick: function (evt, x, y) {
        this.model.attr(`${MARKDOWN_EDITOR_NAME}/props/disabled`, false);
    },

    stopPropagation: function (evt, x, y) {
        evt.stopPropagation();
    },

    onChange: function (evt) {
        this.model.trigger(Events.CELL_CONTENT_UPDATED, this.model, evt.target.value);
        this.model.attr(`${MARKDOWN_EDITOR_NAME}/props/value`, evt.target.value);
        this.model.attr(`${MARKDOWN_EDITOR_NAME}/props/disabled`, true);
    },
});
