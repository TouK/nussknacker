import { dia } from "jointjs";

import { MARKDOWN_EDITOR_NAME } from "./EspNode/stickyNote";
import { Events } from "./types";

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
        "keydown textarea": "selectAll",
        dblclick: "showEditor",
        mouseover: "stopPropagation",
        "click .sticky-note-markdown-editor": "stopPropagation",
        "mouseenter .sticky-note-markdown-editor": "stopPropagation",
        "mouseup .sticky-note-markdown-editor": "stopPropagation",
        "mouseout .sticky-note-markdown-editor": "stopPropagation",
        "mouseleave .sticky-note-markdown-editor": "stopPropagation",
    },

    remove: function (...args) {
        dia.ElementView.prototype.remove.apply(this, ...args);
        this.stopListening();
        document.removeEventListener("click", this.onClickedOutside, true);
        document.removeEventListener("click", this.onEscapePress, true);
    },

    render: function (...args) {
        dia.ElementView.prototype.render.apply(this, args);
        this.model.toBack();
        this.listenTo(this.model, "change:position", this.changePosition);
        return this;
    },

    changePosition: function () {
        this.model.toBack();
    },

    stopPropagation: function (evt) {
        evt.stopPropagation();
    },

    showEditor: function (evt) {
        evt.stopPropagation();
        this.model.toFront();
        this.model.attr(`${MARKDOWN_EDITOR_NAME}/props/disabled`, false);
        const textarea = evt.currentTarget.querySelector("textarea");
        textarea.focus({ preventScroll: true });
        evt.currentTarget.querySelector(".sticky-note-markdown").style.display = "none";

        this.onClickedOutside = (event) => {
            const isClickedOutside = !this.el.contains(event.target);
            if (isClickedOutside) {
                this.hideEditor({ target: textarea });
                document.removeEventListener("click", this.onClickedOutside);
                document.removeEventListener("keydown", this.onEscapePress);
            }
        };

        this.onEscapePress = (event) => {
            if (event.key === "Escape") {
                this.hideEditor({ target: textarea });
                document.removeEventListener("click", this.onClickedOutside);
                document.removeEventListener("keydown", this.onEscapePress);
            }
        };

        document.addEventListener("click", this.onClickedOutside, true);
        document.addEventListener("keydown", this.onEscapePress, true);
    },

    selectAll: function (evt) {
        if (evt.code === "KeyA") {
            if (evt.ctrlKey || evt.metaKey) {
                evt.preventDefault();
                evt.target.select();
            }
        }
    },

    hideEditor: function (evt) {
        this.model.toBack();
        this.model.trigger(Events.CELL_CONTENT_UPDATED, this.model, evt.target.value);
        this.model.attr(`${MARKDOWN_EDITOR_NAME}/props/value`, evt.target.value);
        this.model.attr(`${MARKDOWN_EDITOR_NAME}/props/disabled`, true);
        this.el.querySelector(".sticky-note-markdown").style.display = "block";
    },
});
