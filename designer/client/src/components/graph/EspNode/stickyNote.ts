import { Theme } from "@mui/material";
import { dia, shapes, util, V } from "jointjs";
import { getBorderColor } from "../../../containers/theme/helpers";
import { StickyNote } from "../../../common/StickyNote";
import { marked } from "marked";
import { StickyNoteElement } from "../StickyNoteElement";
import MarkupNodeJSON = dia.MarkupNodeJSON;
import DOMPurify from "dompurify";

export const STICKY_NOTE_WIDTH = 300;
export const STICKY_NOTE_HEIGHT = 250;
export const BORDER_RADIUS = 3;
export const CONTENT_PADDING = 5;
export const ICON_SIZE = 20;
export const STICKY_NOTE_DEFAULT_COLOR = "#eae672";
export const MARKDOWN_EDITOR_NAME = "markdown-editor";

const border: dia.MarkupNodeJSON = {
    selector: "border",
    tagName: "path",
    className: "body",
    attributes: {
        width: STICKY_NOTE_WIDTH,
        height: STICKY_NOTE_HEIGHT,
        strokeWidth: 1,
        fill: "none",
        rx: BORDER_RADIUS,
    },
};

const icon: dia.MarkupNodeJSON = {
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

const body: dia.MarkupNodeJSON = {
    selector: "body",
    tagName: "path",
};

const renderer = new marked.Renderer();
renderer.link = function (href, title, text) {
    return `<a target="_blank" rel="noopener noreferrer" href="${href}">${text}</a>`;
};
renderer.image = function (href, title, text) {
    // SVG don't support HTML img inside foreignObject
    return `<a target="_blank" rel="noopener noreferrer" href="${href}">${text} (attached img)</a>`;
};

const foreignObject = (stickyNote: StickyNote): MarkupNodeJSON => {
    let parsed;
    try {
        parsed = DOMPurify.sanitize(marked.parse(stickyNote.content, { renderer }));
    } catch (error) {
        console.error("Failed to parse markdown:", error);
        parsed = "Error: Could not parse content. See error logs in console";
    }
    const singleMarkupNode = util.svg/* xml */ `
            <foreignObject @selector="foreignObject">
                <div @selector="sticky-note-content" class="sticky-note-content">
                    <textarea @selector="${MARKDOWN_EDITOR_NAME}" class="sticky-note-markdown-editor" name="${MARKDOWN_EDITOR_NAME}" autocomplete="off" disabled="disabled"></textarea>
                    <div @selector="markdown" class="sticky-note-markdown">${parsed}</div>
                </div>
            </foreignObject>
    `[0];
    return singleMarkupNode as MarkupNodeJSON;
};

export const stickyNotePath = "M 0 0 L 10 0 C 10 2.6667 10 5.3333 10 8 C 10 10 9 10 8 10 L 0 10 L 0 0";

const defaults = (theme: Theme) =>
    util.defaultsDeep(
        {
            size: {
                width: STICKY_NOTE_WIDTH,
                height: STICKY_NOTE_HEIGHT,
            },
            attrs: {
                body: {
                    refD: stickyNotePath,
                    strokeWidth: 2,
                    fill: "#eae672",
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
                    width: STICKY_NOTE_WIDTH,
                    height: STICKY_NOTE_HEIGHT - ICON_SIZE - CONTENT_PADDING * 4,
                    y: CONTENT_PADDING * 4 + ICON_SIZE,
                    fill: getBorderColor(theme),
                },
                border: {
                    refD: stickyNotePath,
                    stroke: getBorderColor(theme),
                },
            },
        },
        shapes.devs.Model.prototype.defaults,
    );

const protoProps = (theme: Theme, stickyNote: StickyNote) => {
    return {
        markup: [body, border, foreignObject(stickyNote), icon],
    };
};

export const StickyNoteShape = (theme: Theme, stickyNote: StickyNote) =>
    StickyNoteElement(defaults(theme), protoProps(theme, stickyNote)) as typeof shapes.devs.Model;
