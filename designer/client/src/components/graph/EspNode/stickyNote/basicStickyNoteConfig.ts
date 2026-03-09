import type { Theme } from "@mui/material";
import { alpha } from "@mui/material";
import type { shapes } from "jointjs";
import { dia } from "jointjs";
import type { CSSProperties } from "react";

import { getBorderColor } from "../../../../containers/theme/helpers";
import type { StickyNoteNodeType } from "../../../../types/node";
import { stickyNoteIcon } from "../../../toolbars/creator/ComponentIcon";
import { Events } from "../../types";
import { StickyNoteType } from "../../utils/stickyNotesUtils";
import { CONTENT_PADDING } from "../esp";
import { MARKDOWN_EDITOR_NAME, STICKY_NOTE_CONSTRAINTS, stickyNotePath } from "./stickyNote";

export const STICKY_NOTE_BASIC_CONSTRAINTS = {
    DEFAULT_WIDTH: 300,
    DEFAULT_HEIGHT: 250,
    ICON_SIZE: 20,
    DEFAULT_COLOR: "#eae672",
} as const;

const { DEFAULT_WIDTH, DEFAULT_COLOR, ICON_SIZE, DEFAULT_HEIGHT } = STICKY_NOTE_BASIC_CONSTRAINTS;

export const stickyNoteBasicAttributes = (stickyNote: StickyNoteNodeType, theme: Theme) => ({
    id: stickyNote.id,
    attrs: {
        size: {
            width: stickyNote.dimensions.width,
            height: stickyNote.dimensions.height,
        },
        body: {
            fill: getStickyNoteBasicBackgroundColor(theme, stickyNote.color).main,
            opacity: 1,
        },
        foreignObject: {
            width: stickyNote.dimensions.width,
            height: stickyNote.dimensions.height - ICON_SIZE - CONTENT_PADDING * 4,
            color: theme.palette.getContrastText(getStickyNoteBasicBackgroundColor(theme, stickyNote.color).main),
        },
        icon: {
            xlinkHref: stickyNoteIcon(),
            opacity: 1,
            color: theme.palette.getContrastText(getStickyNoteBasicBackgroundColor(theme, stickyNote.color).main),
        },
        border: {
            stroke: getStickyNoteBasicBackgroundColor(theme, stickyNote.color).dark,
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

export const stickyNoteBasicResizeTool = (stickyNote: StickyNoteNodeType, theme: Theme, stickyNoteModel: shapes.devs.Model) => ({
    children: [
        {
            tagName: "path",
            selector: "handle",
            attributes: {
                d: "M 4 0 L 4 4 L 0 4 L 0 5 L 5 5 L 5 0 L 4 0",
                stroke: getStickyNoteBasicBackgroundColor(theme, stickyNote.color).light,
                cursor: "se-resize",
            },
        },
        {
            tagName: "rect",
            selector: "extras",
            attributes: {
                "pointer-events": "none",
                fill: "none",
                stroke: getStickyNoteBasicBackgroundColor(theme, stickyNote.color).light,
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
    getPosition: function (view) {
        const model = view.model;
        const { width, height } = model.size();
        return { x: width, y: height };
    },
    setPosition: function (view, coordinates) {
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

export function getStickyNoteBasicBackgroundColor(theme: Theme, color: string) {
    const isValidColor = CSS.supports("color", color);
    return theme.palette.augmentColor({
        color: {
            main: isValidColor ? color : DEFAULT_COLOR,
        },
    });
}

export const stickyNotesBasicStyles = (theme: Theme): Record<string, CSSProperties | undefined> => ({
    ".sticky-note-markdown": {
        width: "100%",
        height: "100%",
        paddingLeft: "10px",
        paddingRight: "10px",
    },
    ".sticky-note-markdown-editor": {
        paddingLeft: theme.spacing(1),
        paddingRight: theme.spacing(1),
        backgroundColor: alpha(theme.palette.common.white, 0.3),
        color: theme.palette.common.black,
        fontFamily: theme.typography.fontFamily,
        fontSize: theme.typography.body1.fontSize,
        resize: "none",
        width: "100%",
        height: "100%",
        borderStyle: "none",
        borderColor: "Transparent",
        whiteSpace: "pre-line",
        overflow: "hidden",
    },
    ".sticky-note-markdown-editor:focus": {
        outline: "none",
        boxShadow: `0 0 0 2px ${theme.palette.primary.main}`,
    },
    ".sticky-note-content": {
        width: "100%",
        height: "100%",
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

export const stickyNoteBasicDefaultDeps = (theme: Theme) => ({
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
            height: DEFAULT_HEIGHT - ICON_SIZE - CONTENT_PADDING * 4,
            y: CONTENT_PADDING * 4 + ICON_SIZE,
            fill: getBorderColor(theme),
        },
        border: {
            refD: stickyNotePath,
            stroke: getBorderColor(theme),
        },
    },
});

export const stickyNoteBasicIcon: dia.MarkupNodeJSON = {
    selector: "icon",
    tagName: "use",
    attributes: {
        opacity: 1,
        width: ICON_SIZE,
        height: ICON_SIZE,
        x: ICON_SIZE / 2,
        y: ICON_SIZE / 2,
    },
};

export const stickyNoteBasicBorder: dia.MarkupNodeJSON = {
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
export const basicNoteModel: StickyNoteNodeType = {
    id: "StickyNoteToAdd",
    name: "StickyNoteToAdd",
    type: StickyNoteType,
    isDisabled: false,
    content: "",
    dimensions: dimensions,
    color: DEFAULT_COLOR,
};

export const basicNoteOffset = { x: DEFAULT_WIDTH * -0.8, y: DEFAULT_HEIGHT * -0.5 };

export const StickyNoteElementBasic = dia.ElementView.extend({
    events: {
        "click .sticky-note-markdown-editor": "stopPropagation",
        "keydown textarea": "selectAll",
        "focusout .sticky-note-markdown-editor": "onChange",
        "dblclick .sticky-note-content": "showEditor",
    },

    stopPropagation: function (evt: Event) {
        evt.stopPropagation();
    },

    showEditor: function (evt: Event) {
        evt.stopPropagation();
        this.model.attr(`${MARKDOWN_EDITOR_NAME}/props/disabled`, false);
        const textarea = (evt.currentTarget as HTMLElement).querySelector("textarea") as HTMLTextAreaElement;
        textarea.focus({ preventScroll: true });
        ((evt.currentTarget as HTMLElement).querySelector(".sticky-note-markdown") as HTMLElement).style.display = "none";
    },

    selectAll: function (evt: KeyboardEvent) {
        if (evt.code === "KeyA") {
            if (evt.ctrlKey || evt.metaKey) {
                evt.preventDefault();
                (evt.target as HTMLTextAreaElement).select();
            }
        }
    },

    onChange: function (evt: FocusEvent) {
        const target = evt.target as HTMLTextAreaElement;
        const currentTarget = evt.currentTarget as HTMLTextAreaElement;

        this.model.trigger(Events.CELL_CONTENT_UPDATED, this.model, target.value);
        this.model.attr(`${MARKDOWN_EDITOR_NAME}/props/value`, target.value);
        this.model.attr(`${MARKDOWN_EDITOR_NAME}/props/disabled`, true);
        const markdownElement = (currentTarget.parentElement as HTMLElement).querySelector(".sticky-note-markdown") as HTMLElement;
        markdownElement.style.display = "block";
    },
});
