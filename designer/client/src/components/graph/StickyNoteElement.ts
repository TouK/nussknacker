import { dia } from "jointjs";
import type { FocusEvent } from "react";

import type { UserSettings } from "../../reducers/userSettings";
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

// Shared base functionality to reduce duplication
const StickyNoteElementBase = {
    stopPropagation: function (evt: Event): void {
        evt.stopPropagation();
    },

    selectAll: function (evt: KeyboardEvent): void {
        if (evt.code === "KeyA" && (evt.ctrlKey || evt.metaKey)) {
            evt.preventDefault();
            (evt.target as HTMLTextAreaElement).select();
        }
    },

    updateMarkdownVisibility: function (element: HTMLElement, visible: boolean): void {
        const markdownElement = element.querySelector(".sticky-note-markdown") as HTMLElement;
        if (markdownElement) {
            markdownElement.style.display = visible ? "block" : "none";
        }
    },

    focusTextarea: function (element: HTMLElement): HTMLTextAreaElement | null {
        const textarea = element.querySelector("textarea") as HTMLTextAreaElement;
        if (textarea) {
            textarea.focus({ preventScroll: true });
            return textarea;
        }
        return null;
    },

    handleContentChange: function (evt: FocusEvent<HTMLTextAreaElement> | { target: HTMLTextAreaElement }): void {
        this.model.trigger(Events.CELL_CONTENT_UPDATED, this.model, evt.target.value);
        this.model.attr(`${MARKDOWN_EDITOR_NAME}/props/value`, evt.target.value);
        this.model.attr(`${MARKDOWN_EDITOR_NAME}/props/disabled`, true);
    },
};

const StickyNoteElementBasic = dia.ElementView.extend({
    ...StickyNoteElementBase,

    events: {
        "click .sticky-note-markdown-editor": "stopPropagation",
        "keydown textarea": "selectAll",
        "focusout .sticky-note-markdown-editor": "onChange",
        "dblclick .sticky-note-content": "showEditor",
    },

    showEditor: function (evt: Event): void {
        evt.stopPropagation();
        this.model.attr(`${MARKDOWN_EDITOR_NAME}/props/disabled`, false);

        const currentTarget = evt.currentTarget as HTMLElement;
        const textarea = this.focusTextarea(currentTarget);
        if (textarea) {
            this.updateMarkdownVisibility(currentTarget, false);
        }
    },

    onChange: function (evt: FocusEvent<HTMLTextAreaElement>): void {
        this.handleContentChange(evt);

        const parentElement = evt.currentTarget.parentElement as HTMLElement;
        if (parentElement) {
            this.updateMarkdownVisibility(parentElement, true);
        }
    },
});

const stickyNoteElementAdvanced: typeof dia.ElementView = dia.ElementView.extend({
    ...StickyNoteElementBase,

    // Add proper property declarations
    onClickedOutside: null as ((event: MouseEvent) => void) | null,
    onEscapePress: null as ((event: KeyboardEvent) => void) | null,

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

    remove: function (...args: unknown[]): void {
        dia.ElementView.prototype.remove.apply(this, args);
        this.cleanup();
    },

    render: function (...args: unknown[]): typeof dia.ElementView {
        dia.ElementView.prototype.render.apply(this, args);
        this.model.toBack();
        this.listenTo(this.model, "change:position", this.changePosition);
        return this;
    },

    changePosition: function (): void {
        this.model.toBack();
    },

    showEditor: function (evt: Event): void {
        evt.stopPropagation();
        this.model.toFront();
        this.model.attr(`${MARKDOWN_EDITOR_NAME}/props/disabled`, false);

        const currentTarget = evt.currentTarget as HTMLElement;
        const textarea = this.focusTextarea(currentTarget);

        if (textarea) {
            this.updateMarkdownVisibility(currentTarget, false);
            this.setupOutsideClickHandlers(textarea);
        }
    },

    setupOutsideClickHandlers: function (textarea: HTMLTextAreaElement): void {
        this.cleanup(); // Clean up any existing handlers

        this.onClickedOutside = (event: MouseEvent): void => {
            const isClickedOutside = !this.el.contains(event.target as Node);
            if (isClickedOutside) {
                this.hideEditor({ target: textarea });
                this.cleanup();
            }
        };

        this.onEscapePress = (event: KeyboardEvent): void => {
            if (event.key === "Escape") {
                this.hideEditor({ target: textarea });
                this.cleanup();
            }
        };

        document.addEventListener("click", this.onClickedOutside, true);
        document.addEventListener("keydown", this.onEscapePress, true);
    },

    cleanup: function (): void {
        this.stopListening();
        if (this.onClickedOutside) {
            document.removeEventListener("click", this.onClickedOutside, true);
            this.onClickedOutside = null;
        }
        if (this.onEscapePress) {
            document.removeEventListener("keydown", this.onEscapePress, true);
            this.onEscapePress = null;
        }
    },

    hideEditor: function (evt: { target: HTMLTextAreaElement }): void {
        this.model.toBack();
        this.handleContentChange(evt);
        this.updateMarkdownVisibility(this.el, true);
    },
});

export const StickyNoteElementView = (userSettings: UserSettings) =>
    userSettings["node.advancedStickyNotes"] ? stickyNoteElementAdvanced : StickyNoteElementBasic;
