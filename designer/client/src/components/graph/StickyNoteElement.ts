import { dia } from "jointjs";
import { Events } from "./types";
import { MARKDOWN_EDITOR_NAME } from "./EspNode/stickyNote";
import MarkupNodeJSON = dia.MarkupNodeJSON;

export interface StickyNoteDefaults {
    position?: { x: number; y: number };
    size?: { width: number; height: number };
    attrs?: Record<string, unknown>;
}

export interface StickyNoteProtoProps {
    markup: (dia.MarkupNodeJSON | MarkupNodeJSON)[];
    [key: string]: unknown;
}

export const StickyNoteElement = (defaults?: StickyNoteDefaults, protoProps?: StickyNoteProtoProps) =>
    dia.Element.define("stickyNote.StickyNoteElement", defaults, protoProps);

export const StickyNoteElementView = dia.ElementView.extend({
    events: {
        "click textarea": "stopPropagation",
        "keydown textarea": "selectAll",
        "focusout textarea": "onChange",
        "dblclick .sticky-note-content": "showEditor",
    },

    stopPropagation: function (evt) {
        evt.stopPropagation();
    },

    showEditor: function (evt) {
        evt.stopPropagation();
        this.model.attr(`${MARKDOWN_EDITOR_NAME}/props/disabled`, false);
        evt.currentTarget.querySelector("textarea").focus({ preventScroll: true });
    },

    selectAll: function (evt) {
        if (evt.code === "KeyA") {
            if (evt.ctrlKey || evt.metaKey) {
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
