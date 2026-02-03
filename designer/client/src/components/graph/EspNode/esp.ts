/* eslint-disable i18next/no-literal-string */
import type { Theme } from "@mui/material";
import type { dia } from "jointjs";
import { shapes, util } from "jointjs";

import { blendLighten, getBorderColor, getNodeBorderColor } from "../../../containers/theme/helpers";
import type { NodeType } from "../../../types/node";
import { getStringWidth } from "./element";
import { getRoundedRectPath } from "./getRoundedRectPath";

export const RECT_WIDTH = 300;
export const RECT_HEIGHT = 60;
export const BORDER_RADIUS = 3;
export const MARGIN_TOP = 7;
export const CONTENT_PADDING = 10;
export const HELP_ICON_PADDING = 24;
export const iconBackgroundSize = RECT_HEIGHT;
export const iconSize = iconBackgroundSize / 2;
export const portSize = iconSize / 3;

const background: dia.MarkupNodeJSON = {
    selector: "background",
    tagName: "rect",
    className: "background",
    attributes: {
        width: RECT_WIDTH,
        height: RECT_HEIGHT,
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
        width: RECT_WIDTH,
        height: RECT_HEIGHT,
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
        y: RECT_HEIGHT / 2,
        fill: getBorderColor(theme),
        "pointer-events": "none",
        ...theme.typography.caption,
    },
});

const help = (theme: Theme): dia.MarkupNodeJSON => ({
    selector: "help",
    tagName: "text",
    attributes: {
        x: RECT_WIDTH - HELP_ICON_PADDING,
        y: RECT_HEIGHT / 2,
        fill: getBorderColor(theme),
        ...theme.typography.caption,
        "font-size": "1.75em",
        "text-anchor": "end",
        cursor: "help",
    },
});

const portMarkup = (theme: Theme, node: NodeType): dia.MarkupNodeJSON => ({
    selector: "port",
    tagName: "circle",
    attributes: {
        magnet: true,
        r: portSize,
        stroke: node.isDisabled ? "none" : getNodeBorderColor(theme),
        fill: blendLighten(theme.palette.background.paper, 0.04),
        strokeWidth: 0.5,
        disabled: node.isDisabled,
    },
});

const testResultsHeight = 24;
const testAssertionResultHeight = 24;
const testResults: dia.MarkupNodeJSON = {
    tagName: "g",
    selector: "testResultsGroup",
    children: [
        {
            selector: "testResults",
            tagName: "rect",
            className: "testResultsPlaceholder",
            attributes: {
                height: testResultsHeight,
                y: RECT_HEIGHT - testResultsHeight / 2,
            },
        },
        {
            selector: "testResultsSummary",
            tagName: "text",
            className: "testResultsSummary",
            attributes: {
                height: testResultsHeight,
                y: RECT_HEIGHT + testResultsHeight / 2 + 1 - testResultsHeight / 2,
            },
        },
    ],
    attributes: {
        noExport: "",
    },
};

const testAssertionResult: dia.MarkupNodeJSON = {
    tagName: "g",
    selector: "testAssertionResultGroup",
    children: [
        {
            selector: "testAssertionResult",
            tagName: "rect",
            className: "testAssertionResultPlaceholder",
            attributes: {
                height: testAssertionResultHeight,
                y: RECT_HEIGHT / 2 - 12,
            },
        },
        {
            selector: "testAssertionResultSummary",
            tagName: "text",
            className: "testAssertionResultSummary",
            attributes: {
                height: testAssertionResultHeight,
                y: RECT_HEIGHT / 2 + 4,
            },
        },
    ],
    attributes: {
        noExport: "",
    },
};

const refX = RECT_HEIGHT - getStringWidth("1") / 2;
const defaults = (theme: Theme) =>
    util.defaultsDeep(
        {
            size: {
                width: RECT_WIDTH,
                height: RECT_HEIGHT,
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
                testAssertionResult: {
                    refX: 1,
                    rx: 5,
                    z: 2,
                },
                testAssertionResultSummary: {
                    refX: 1,
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
const protoProps = (theme: Theme, node: NodeType) => {
    return {
        portMarkup: [portMarkup(theme, node)],
        portLabelMarkup: null,

        markup: [background, iconBackground, border, icon, content(theme), help(theme), testResults, testAssertionResult],
    };
};

export const EspNodeShape = (theme: Theme, node: NodeType) =>
    shapes.devs.Model.define(`esp.Model`, defaults(theme), protoProps(theme, node)) as typeof shapes.devs.Model;
