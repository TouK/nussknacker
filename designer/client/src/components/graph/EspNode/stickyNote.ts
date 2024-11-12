import { Theme } from "@mui/material";
import { dia, shapes, util } from "jointjs";
import { getBorderColor } from "../../../containers/theme/helpers";
import { StickyNote } from "../../../common/StickyNote";
import { marked } from "marked";

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

const icon: dia.MarkupNodeJSON = {
    selector: "icon",
    tagName: "use",
    attributes: {
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

//TODO - I left it here since it can be some kind of solution to displaying and editing markdown on the graph.
//https://resources.jointjs.com/tutorial/foreign-object
//Maybe we should it different way...
const foreignObject = (stickyNote: StickyNote) => {
    const parsed = marked.parse(stickyNote.content + "**TODO** - dont forget to do *it*.. \n\n [x] check");
    return util.svg/* xml */ `
        <foreignObject @selector="foreignObject" >
           ${parsed}
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
                    width: STICKY_NOTE_WIDTH - ICON_SIZE - CONTENT_PADDING * 2,
                    height: STICKY_NOTE_HEIGHT - ICON_SIZE - CONTENT_PADDING * 2,
                    x: ICON_SIZE + CONTENT_PADDING,
                    y: ICON_SIZE + CONTENT_PADDING,
                    fill: getBorderColor(theme),
                    "pointer-events": "none",
                    ...theme.typography.caption,
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
    shapes.devs.Model.define(`stickyNote.Model`, defaults(theme), protoProps(theme, stickyNote)) as typeof shapes.devs.Model;
