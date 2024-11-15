import { Theme } from "@mui/material";
import { dia, shapes, util } from "jointjs";
import { getBorderColor } from "../../../containers/theme/helpers";
import { StickyNote } from "../../../common/StickyNote";
import { marked } from "marked";
import { StickyNoteElement } from "../StickyNoteElement";
import { getStickyNoteIcon } from "../../toolbars/creator/ComponentIcon";

export const STICKY_NOTE_WIDTH = 300;
export const STICKY_NOTE_HEIGHT = 250;
export const BORDER_RADIUS = 3;
export const CONTENT_PADDING = 5;
export const ICON_SIZE = 20;
export const STICKY_NOTE_DEFAULT_COLOR = "#eae672";

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

const iconHref = getStickyNoteIcon();

const icon: dia.MarkupNodeJSON = {
    selector: "icon",
    tagName: "use",
    attributes: {
        opacity: 1,
        xlinkHref: iconHref,
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

const foreignObject = (stickyNote: StickyNote) => {
    const renderer = new marked.Renderer();
    renderer.link = function (href, title, text) {
        return `<a target="_blank" href="${href}">${text}` + "</a>";
    };
    const parsed = marked.parse(stickyNote.content, { renderer });
    return util.svg/* xml */ `
            <foreignObject @selector="foreignObject">
                        <textarea @selector="name" class="sticky-note-markdown-editor" name="name" autocomplete="off" disabled="disabled">${stickyNote.content}</textarea>
                        <div @selector="markdown" class="sticky-note-markdown">${parsed}</div>
            </foreignObject>
    `[0];
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
                    width: STICKY_NOTE_WIDTH - CONTENT_PADDING * 2,
                    height: STICKY_NOTE_HEIGHT - ICON_SIZE - CONTENT_PADDING * 4,
                    x: CONTENT_PADDING * 2,
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
