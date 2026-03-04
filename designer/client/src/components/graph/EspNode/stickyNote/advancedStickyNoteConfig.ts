import type { CSSOthersObject } from "@emotion/serialize";
import type { Theme } from "@mui/material";
import { lighten } from "@mui/material";
import type { shapes } from "jointjs";
import { dia } from "jointjs";

import { blendDarken, blendLighten, getBorderColor } from "../../../../containers/theme/helpers";
import type { NodeType, StickyNoteNodeType } from "../../../../types/node";
import { stickyNoteIcon } from "../../../toolbars/creator/ComponentIcon";
import { Events } from "../../types";
import { StickyNoteType } from "../../utils/stickyNotesUtils";
import { CONTENT_PADDING } from "../esp";
import { MARKDOWN_EDITOR_NAME, STICKY_NOTE_CONSTRAINTS, stickyNotePath } from "./stickyNote";

export const STICKY_NOTE_ADVANCED_CONSTRAINTS = {
    DEFAULT_WIDTH: 180,
    DEFAULT_HEIGHT: 130,
    DEFAULT_COLOR: "#4D3B00",
} as const;

const { DEFAULT_COLOR, DEFAULT_HEIGHT, DEFAULT_WIDTH } = STICKY_NOTE_ADVANCED_CONSTRAINTS;

export const stickyNoteAdvancedAttributes = (stickyNote: StickyNoteNodeType, theme: Theme) => ({
    id: stickyNote.id,
    attrs: {
        size: {
            width: stickyNote.dimensions.width,
            height: stickyNote.dimensions.height,
        },
        body: {
            fill: getStickyNoteAdvancedBackgroundColor(theme, stickyNote.color).main,
            opacity: 1,
        },
        foreignObject: {
            width: stickyNote.dimensions.width,
            height: stickyNote.dimensions.height,
            color: theme.palette.getContrastText(getStickyNoteAdvancedBackgroundColor(theme, stickyNote.color).main),
            y: 0,
        },
        icon: {
            xlinkHref: stickyNoteIcon(),
            opacity: 1,
            color: theme.palette.getContrastText(getStickyNoteAdvancedBackgroundColor(theme, stickyNote.color).main),
        },
        border: {
            stroke: getStickyNoteAdvancedBackgroundColor(theme, stickyNote.color).dark,
            strokeWidth: 1,
        },
    },
    nodeData: {
        id: stickyNote.id,
    },
    definitionToCompare: {
        width: stickyNote.dimensions.width,
        height: stickyNote.dimensions.height,
        color: stickyNote.color,
        content: stickyNote.content,
        errors: stickyNote.errors,
    },
    rankDir: "R",
});

export const stickyNoteAdvancedResizeTool = (stickyNote: StickyNoteNodeType, theme: Theme, stickyNoteModel: shapes.devs.Model) => ({
    children: [
        {
            tagName: "path",
            selector: "handle",
            attributes: {
                d: "M 4 0 L 4 4 L 0 4 L 0 5 L 5 5 L 5 0 L 4 0",
                stroke: getStickyNoteAdvancedBackgroundColor(theme, stickyNote.color).light,
                cursor: "se-resize",
            },
        },
        {
            tagName: "rect",
            selector: "extras",
            attributes: {
                "pointer-events": "none",
                fill: "none",
                stroke: getStickyNoteAdvancedBackgroundColor(theme, stickyNote.color).light,
                "stroke-dasharray": "2,3",
                rx: 6,
                ry: 6,
            },
        },
    ],
    documentEvents: {
        mousemove: "onPointerMove",
        touchmove: "onPointerMove",
        mouseup: "onPointerUpCustom",
        touchend: "onPointerUpCustom",
        touchcancel: "onPointerUp",
    },
    getPosition: function (view: dia.ElementView) {
        const model = view.model;
        const { width, height } = model.size();
        return { x: width, y: height };
    },
    setPosition: function (view: dia.ElementView, coordinates: { x: number; y: number }) {
        const model = view.model;
        model.resize(
            Math.max(Math.min(STICKY_NOTE_CONSTRAINTS.MAX_WIDTH, Math.round(coordinates.x - 10)), STICKY_NOTE_CONSTRAINTS.MIN_WIDTH),
            Math.max(Math.min(STICKY_NOTE_CONSTRAINTS.MAX_HEIGHT, Math.round(coordinates.y - 10)), STICKY_NOTE_CONSTRAINTS.MIN_HEIGHT),
        );
    },
    onPointerUpCustom: function (evt: dia.Event) {
        this.onPointerUp(evt);
        stickyNoteModel.trigger(Events.CELL_RESIZED, stickyNoteModel);
    },
});

export function getStickyNoteAdvancedBackgroundColor(theme: Theme, color: string) {
    const isValidColor = CSS.supports("color", color);
    return theme.palette.augmentColor({
        color: {
            main: isValidColor ? color : DEFAULT_COLOR,
            dark: blendDarken(isValidColor ? color : DEFAULT_COLOR, 0.4),
            light: blendLighten(isValidColor ? color : DEFAULT_COLOR, 0.4),
        },
    });
}

export const stickyNotesAdvancedStyles = (theme: Theme): Record<string, any> => ({
    ".sticky-note-markdown": {
        ...(theme.typography.body2 as CSSOthersObject),
        width: "100%",
        height: "100%",
        paddingLeft: "10px",
        paddingRight: "10px",
    },
    ".sticky-note-markdown a": {
        color: theme.palette.primary.main,
        "&:hover": {
            color: lighten(theme.palette.primary.main, 0.25),
        },
        "&:focus": {
            color: theme.palette.primary.main,
            textDecoration: "none",
        },
    },
    ".sticky-note-markdown-editor": {
        ...(theme.typography.body2 as CSSOthersObject),
        backgroundColor: theme.palette.background.paper,
        resize: "none",
        width: "100%",
        height: "100%",
        borderStyle: "none",
        whiteSpace: "pre-line",
        overflow: "hidden",
    },
    ".sticky-note-markdown-editor:focus": {
        outline: `1px solid ${theme.palette.primary.main}`,
    },
    ".sticky-note-content": {
        width: "100%",
        height: "100%",
    },
    ".sticky-note-content:has(.sticky-note-markdown-editor:not(:disabled))": {
        padding: theme.spacing(0.75),
    },
    ".joint-sticky-note-remove-tool > circle": {
        fill: "#ca344c",
    },
    ".sticky-note-errors": {
        fontFamily: theme.typography.fontFamily,
        fontSize: theme.typography.body1.fontSize,
        color: theme.palette.text.primary,
    },
    ".sticky-note-error": {
        backgroundColor: theme.palette.error.main,
        marginTop: "5px",
        padding: "0px 5px",
    },
    ".sticky-note-markdown-editor:disabled": {
        display: "none",
    },
});

export const stickyNoteAdvancedDefaultDeps = (theme: Theme) => ({
    size: {
        width: DEFAULT_WIDTH,
        height: DEFAULT_HEIGHT,
    },
    attrs: {
        body: {
            refD: stickyNotePath,
            strokeWidth: 2,
            fill: DEFAULT_COLOR,
            filter: {
                name: "dropShadow",
                args: {
                    dx: 1,
                    dy: 1,
                    blur: 5,
                    opacity: 0.4,
                },
            },
        },
        foreignObject: {
            width: DEFAULT_WIDTH,
            height: DEFAULT_HEIGHT - CONTENT_PADDING * 4,
            y: CONTENT_PADDING * 4,
            fill: getBorderColor(theme),
        },
        border: {
            refD: stickyNotePath,
            stroke: getBorderColor(theme),
        },
    },
});

export const stickyNoteAdvancedBorder: dia.MarkupNodeJSON = {
    selector: "border",
    tagName: "path",
    className: "body",
    attributes: {
        width: DEFAULT_WIDTH,
        height: DEFAULT_HEIGHT,
        strokeWidth: 1,
        fill: "none",
    },
};

const dimensions = { width: DEFAULT_WIDTH, height: DEFAULT_HEIGHT };
export const advancedNoteModel: StickyNoteNodeType = {
    id: "StickyNoteToAdd",
    name: "StickyNoteToAdd",
    type: StickyNoteType,
    isDisabled: false,
    content: "#### ✏️ Double click to edit",
    dimensions: dimensions,
    color: DEFAULT_COLOR,
};

export const advancedNoteOffset = { x: DEFAULT_WIDTH * -0.8, y: DEFAULT_HEIGHT * -0.5 };

export const overrideAdvancedStickyNoteColorToDefault = (stickyNote: NodeType) => ({ ...stickyNote, color: DEFAULT_COLOR });

interface StickyNoteElementAdvanced extends dia.ElementView {
    _editorTextarea?: HTMLTextAreaElement;
    hideEditorOnClickedOutside: (event: MouseEvent) => void;
    hideEditorOnEscapePress: (event: KeyboardEvent) => void;
    changePosition: () => void;
    // stopPropagation: (evt: Event) => void;
    showEditor: (evt: Event) => void;
    selectAll: (evt: KeyboardEvent) => void;
    hideEditor: (evt: { target: HTMLTextAreaElement }) => void;
}

export const stickyNoteElementAdvanced: new () => StickyNoteElementAdvanced = dia.ElementView.extend({
    events: {
        "keydown textarea": "selectAll",
        dblclick: "showEditor",
        mouseover: "stopPropagation",
        // pointer events
        "pointerdown .sticky-note-markdown-editor": "stopPropagation",
        "pointerup .sticky-note-markdown-editor": "stopPropagation",
        "pointermove .sticky-note-markdown-editor": "stopPropagation",
        "pointerenter .sticky-note-markdown-editor": "stopPropagation",
        "pointerleave .sticky-note-markdown-editor": "stopPropagation",

        // mouse events
        "click .sticky-note-markdown-editor": "stopPropagation",
        "dblclick .sticky-note-markdown-editor": "stopPropagation",
        "mousedown .sticky-note-markdown-editor": "stopPropagation",
        "mouseup .sticky-note-markdown-editor": "stopPropagation",
        "mousemove .sticky-note-markdown-editor": "stopPropagation",
        "mouseenter .sticky-note-markdown-editor": "stopPropagation",
        "mouseleave .sticky-note-markdown-editor": "stopPropagation",
        "mouseover .sticky-note-markdown-editor": "stopPropagation",
        "mouseout .sticky-note-markdown-editor": "stopPropagation",
        "contextmenu .sticky-note-markdown-editor": "stopPropagation",
        "wheel .sticky-note-markdown-editor": "stopPropagation",

        // touch events
        "touchstart .sticky-note-markdown-editor": "stopPropagation",
        "touchend .sticky-note-markdown-editor": "stopPropagation",
        "touchmove .sticky-note-markdown-editor": "stopPropagation",
        "touchcancel .sticky-note-markdown-editor": "stopPropagation",

        // keyboard events
        "keydown .sticky-note-markdown-editor": "stopPropagation",
        "keyup .sticky-note-markdown-editor": "stopPropagation",
        "keypress .sticky-note-markdown-editor": "stopPropagation",

        // focus / input
        "focus .sticky-note-markdown-editor": "stopPropagation",
        "blur .sticky-note-markdown-editor": "stopPropagation",
        "input .sticky-note-markdown-editor": "stopPropagation",
        "change .sticky-note-markdown-editor": "stopPropagation",
        "compositionstart .sticky-note-markdown-editor": "stopPropagation",
        "compositionend .sticky-note-markdown-editor": "stopPropagation",

        // drag & drop
        "dragstart .sticky-note-markdown-editor": "stopPropagation",
        "dragover .sticky-note-markdown-editor": "stopPropagation",
        "drop .sticky-note-markdown-editor": "stopPropagation",
    },

    remove: function (this: StickyNoteElementAdvanced, ...args: unknown[]): void {
        dia.ElementView.prototype.remove.apply(this, args);
        this.stopListening();

        if (this.hideEditorOnClickedOutside) {
            document.removeEventListener("click", this.hideEditorOnClickedOutside, true);
        }

        if (this.hideEditorOnEscapePress) {
            document.removeEventListener("keydown", this.hideEditorOnEscapePress, true);
        }
    },

    render: function (this: StickyNoteElementAdvanced, ...args: unknown[]) {
        dia.ElementView.prototype.render.apply(this, args);
        this.model.toBack();
        this.listenTo(this.model, Events.CHANGE_POSITION, this.changePosition);
        return this;
    },

    changePosition: function (this: StickyNoteElementAdvanced): void {
        this.model.toBack();
    },

    stopPropagation: function (this: StickyNoteElementAdvanced, evt: Event): void {
        evt.stopPropagation();
    },

    showEditor: function (this: StickyNoteElementAdvanced, evt: Event): void {
        evt.stopPropagation();
        this.model.toFront();
        this.model.attr(`${MARKDOWN_EDITOR_NAME}/props/disabled`, false);
        this._editorTextarea = (evt.currentTarget as HTMLElement).querySelector("textarea") as HTMLTextAreaElement;
        this._editorTextarea.focus({ preventScroll: true });
        ((evt.currentTarget as HTMLElement).querySelector(".sticky-note-markdown") as HTMLElement).style.display = "none";

        document.addEventListener("click", this.hideEditorOnClickedOutside.bind(this), true);
        document.addEventListener("keydown", this.hideEditorOnEscapePress.bind(this), true);
    },

    hideEditorOnClickedOutside: function (this: StickyNoteElementAdvanced, event: MouseEvent): void {
        const isClickedOutside = !this.el.contains(event.target as Node);
        if (isClickedOutside) {
            this.hideEditor({ target: this._editorTextarea });
        }
    },

    hideEditorOnEscapePress: function (this: StickyNoteElementAdvanced, event: KeyboardEvent): void {
        if (event.key === "Escape") {
            this.hideEditor({ target: this._editorTextarea });
        }
    },

    selectAll: function (this: StickyNoteElementAdvanced, evt: KeyboardEvent): void {
        if (evt.code === "KeyA" && (evt.ctrlKey || evt.metaKey)) {
            evt.preventDefault();
            (evt.target as HTMLTextAreaElement).select();
        }
    },

    hideEditor: function (this: StickyNoteElementAdvanced, evt: { preventDefault: any; target: HTMLTextAreaElement }): void {
        this.model.toBack();
        this.model.trigger(Events.CELL_CONTENT_UPDATED, this.model, evt.target.value);
        this.model.attr(`${MARKDOWN_EDITOR_NAME}/props/value`, evt.target.value);
        this.model.attr(`${MARKDOWN_EDITOR_NAME}/props/disabled`, true);
        const markdownElement = this.el.querySelector(".sticky-note-markdown") as HTMLElement;
        if (markdownElement) {
            markdownElement.style.display = "block";
        }
    },
});
