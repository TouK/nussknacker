import { CSSProperties } from "react";
import { css, styled, Theme } from "@mui/material";
import { blend } from "@mui/system";
import { blendLighten } from "../../containers/theme/helpers";

const nodeHighlight = (strokeColor: CSSProperties["color"], backgroundFill: CSSProperties["color"]) =>
    css({
        ".body": {
            stroke: strokeColor,
            strokeWidth: 1,
        },
        ".background": {
            fill: backgroundFill,
        },
        ".joint-port-body": {
            strokeWidth: 1,
            fill: backgroundFill,
            stroke: strokeColor,
        },
    });

export const dragHovered = "dragHovered";

export const nodeValidationError = "node-validation-error";
export const nodeFound = "node-found";
export const nodeFoundHover = "node-found-hover";
export const nodeFocused = "node-focused";

const nodeStyles = (theme: Theme) => {
    return css({
        ".joint-type-esp-model": {
            "&, .body, .background, .joint-port-body": {
                strokeWidth: 0.5,
                transition: "filter 0.5s, fill 0.25s, stroke 0.25s",
            },
        },
        ".joint-layers": {
            willChange: "transform",
            transition: "none 0s ease 0s",
        },
        [`.${nodeValidationError}`]: nodeHighlight(
            theme.palette.error.main,
            blend(blendLighten(theme.palette.background.paper, 0.04), theme.palette.error.main, 0.3),
        ),
        [`.${nodeFocused}`]: nodeHighlight(
            theme.palette.secondary.light,
            blend(blendLighten(theme.palette.background.paper, 0.04), theme.palette.secondary.light, 0.15),
        ),
        [`.${nodeFocused}.${nodeValidationError}`]: nodeHighlight(
            theme.palette.secondary.light,
            blend(blendLighten(theme.palette.background.paper, 0.04), theme.palette.error.main, 0.3),
        ),
        [`.${nodeFound}`]: nodeHighlight(
            theme.palette.success.main,
            blend(theme.palette.background.paper, theme.palette.success.main, 0.15),
        ),
        [`.${nodeFocused}.${nodeFound}`]: nodeHighlight(
            theme.palette.secondary.light,
            blend(theme.palette.background.paper, theme.palette.success.main, 0.15),
        ),
        [`.${nodeFoundHover}.${nodeFound}`]: nodeHighlight(
            theme.palette.success.main,
            blend(theme.palette.background.paper, theme.palette.success.main, 0.4),
        ),
        [`.${nodeFocused}.${nodeFoundHover}.${nodeFound}`]: nodeHighlight(
            theme.palette.secondary.light,
            blend(theme.palette.background.paper, theme.palette.success.main, 0.4),
        ),
    });
};

export const GraphStyledWrapper = styled("div")(({ theme }) =>
    css([
        {
            color: theme.palette.common.white,
        },
        nodeStyles(theme),
        {
            ".element": {
                cursor: "pointer",
                "&:active": {
                    cursor: "grabbing",
                },
            },
            ".testResultsSummary": {
                fontSize: 13,
                fontWeight: "bold",
            },
            ".joint-paper-background": {
                overflowY: "auto",
                overflowX: "auto",
                backgroundColor: theme.palette.background.default,
            },
            ".arrowMarker path": {
                fill: theme.palette.text.secondary,
            },
            "@media (hover: none)": {
                ".joint-theme-default .joint-link .link-tool &:hover .connection-wrap": {
                    display: "none",
                    ".marker-arrowhead": {
                        opacity: 0,
                        transform: "scale(0.3)",
                    },
                },
            },
            ".joint-theme-default.joint-link": {
                ":hover": {
                    ".connection": {
                        stroke: theme.palette.primary.main,
                    },
                },
                ".marker-arrowhead": {
                    // small element with big stroke allows arrow to overflow link path
                    transform: "scale(0.1)",
                    strokeWidth: 45,
                    fill: theme.palette.primary.main,
                    stroke: theme.palette.primary.main,
                    transition: "all 0.25s ease-in-out",
                    "&:hover": {
                        fill: theme.palette.primary.main,
                        stroke: theme.palette.primary.main,
                    },
                },
                ".marker-arrowhead-group-source": {
                    display: "none",
                },
                ".connection-wrap": {
                    cursor: "default",
                    "&:hover": {
                        opacity: 0,
                        strokeOpacity: 0,
                    },
                },
                ".connection": {
                    stroke: theme.palette.text.secondary,
                    strokeWidth: 1,
                },
                // simple method to get dragging state
                '&[style*="pointer"]': {
                    ".connection": {
                        strokeWidth: "3",
                        strokeDasharray: "3 0 3",
                    },
                },
                [`&.${dragHovered}`]: {
                    ".connection": {
                        strokeWidth: "3",
                        strokeDasharray: "3 0 3",
                    },
                },
                ".link-tool": {
                    // TODO: fix this without css
                    '&[visibility="hidden"]': {
                        /*remove hidden tools from size calculations*/
                        display: "none",
                    },
                    ".tool-remove": {
                        cursor: "default",
                        filter: "saturate(0.75)",
                        transition: "all 0.25s ease-in-out",
                        "&:hover": {
                            cursor: "pointer",
                            circle: {
                                fill: theme.palette.error.main,
                            },
                        },
                        circle: {
                            fill: theme.palette.primary.main,
                            r: 8,
                        },
                        path: {
                            fill: theme.palette.background.default,
                            scale: "0.6",
                            transform: "translate(-16px, -16px)",
                        },
                    },
                },
            },
            ".sticky-note-markdown": {
                width: "100%",
                height: "100%",
                paddingLeft: "10px",
                paddingRight: "10px",
            },
            ".sticky-note-markdown-editor": {
                paddingLeft: "10px",
                paddingRight: "10px",
                backgroundColor: "rgba(0,0,0,0.1)",
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
            },
            ".sticky-note-content": {
                width: "100%",
                height: "100%",
            },
            ".sticky-note-markdown-editor:disabled": {
                display: "none",
            },
        },
    ]),
);
