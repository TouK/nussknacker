import { Theme } from "@mui/material";
import { NodeType } from "../../../types";
import { dia, shapes, util } from "jointjs";
import { blendLighten, getBorderColor, getNodeBorderColor } from "../../../containers/theme/helpers";
import { getStringWidth } from "./element";
import { STICKY_NOTE_HEIGHT, STICKY_NOTE_WIDTH } from "../../StickyNotePreview";
import { StickyNote } from "../../../common/StickyNote";
import { getRoundedRectPath } from "./getRoundedRectPath";
import { BORDER_RADIUS, CONTENT_PADDING, iconBackgroundSize, iconSize, MARGIN_TOP, portSize } from "./esp";

const background: dia.MarkupNodeJSON = {
    selector: "background",
    tagName: "rect",
    className: "background",
    attributes: {
        width: STICKY_NOTE_WIDTH,
        height: STICKY_NOTE_HEIGHT,
        rx: BORDER_RADIUS,
    },
};

const iconBackground: dia.MarkupNodeJSON = {
    selector: "iconBackground",
    tagName: "path", //TODO: check if it's fast enough
    attributes: {
        d: getRoundedRectPath(iconBackgroundSize, [BORDER_RADIUS, BORDER_RADIUS, BORDER_RADIUS, BORDER_RADIUS], MARGIN_TOP),
    },
};

const border: dia.MarkupNodeJSON = {
    selector: "border",
    tagName: "rect",
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
        width: iconSize,
        height: iconSize,
        x: iconSize / 2,
        y: iconSize / 2,
    },
};

const content = (theme: Theme): dia.MarkupNodeJSON => ({
    selector: "content",
    tagName: "text",
    attributes: {
        x: iconBackgroundSize + CONTENT_PADDING,
        y: STICKY_NOTE_HEIGHT / 2,
        fill: getBorderColor(theme),
        "pointer-events": "none",
        ...theme.typography.caption,
    },
});

const help = (theme: Theme): dia.MarkupNodeJSON => ({
    selector: "help",
    tagName: "text",
    attributes: {
        x: STICKY_NOTE_WIDTH - CONTENT_PADDING,
        y: STICKY_NOTE_HEIGHT / 2,
        fill: getBorderColor(theme),
        ...theme.typography.caption,
        "font-size": "1.75em",
        "text-anchor": "end",
        cursor: "help",
    },
});

const portMarkup = (theme: Theme, stickyNote: StickyNote): dia.MarkupNodeJSON => ({
    selector: "port",
    tagName: "circle",
    attributes: {
        magnet: true,
        r: portSize,
        stroke: getNodeBorderColor(theme),
        fill: blendLighten(theme.palette.background.paper, 0.04),
        strokeWidth: 0.5,
        disabled: false,
    },
});

const refX = STICKY_NOTE_HEIGHT - getStringWidth("1") / 2;
const defaults = (theme: Theme) =>
    util.defaultsDeep(
        {
            size: {
                width: STICKY_NOTE_WIDTH,
                height: STICKY_NOTE_HEIGHT,
            },
            attrs: {
                content: {
                    textVerticalAnchor: "middle",
                },
                help: {
                    textVerticalAnchor: "middle",
                },
                border: {
                    stroke: getBorderColor(theme),
                },
                testResults: {
                    refX,
                    rx: 5,
                    z: 2,
                },
                testResultsSummary: {
                    textAnchor: "middle",
                    textVerticalAnchor: "middle",
                    refX,
                    z: 2,
                },
            },
            inPorts: [],
            outPorts: [],
            ports: {
                groups: {
                    in: {
                        position: { name: `top`, args: { dx: 90 } },
                        attrs: {
                            magnet: "passive",
                            type: "input",
                            z: 1,
                        },
                    },
                    out: {
                        position: { name: `bottom`, args: { dx: 90 } },
                        attrs: {
                            type: "output",
                            z: 1,
                        },
                    },
                },
            },
        },
        shapes.devs.Model.prototype.defaults,
    );

const protoProps = (theme: Theme, stickyNote: StickyNote) => {
    return {
        portMarkup: [portMarkup(theme, stickyNote)],
        portLabelMarkup: null,

        markup: [background, iconBackground, border, icon, content(theme), help(theme)],
    };
};

export const StickyNoteShape = (theme: Theme, stickyNote: StickyNote) =>
    shapes.devs.Model.define(`stickyNote.Model`, defaults(theme), protoProps(theme, stickyNote)) as typeof shapes.devs.Model;
