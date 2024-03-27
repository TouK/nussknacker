import { css, styled } from "@mui/material";
import { FocusableStyled } from "./focusableStyled";

export const dragHovered = "dragHovered";
export const GraphStyled = styled(FocusableStyled)(({ theme }) => ({
    minHeight: "300px",
    minWidth: "300px",
    color: theme.palette.common.white,
    ".arrowMarker path": {
        fill: theme.palette.text.secondary,
    },
    " @media (hover: none)": {
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
            "&:hover": {
                opacity: 0,
                strokeOpacity: 0,
            },
        },
        ".connection": {
            stroke: theme.palette.text.secondary,
            "stroke-width": 1,
        },
        // simple method to get dragging state
        '&[style*="pointer"]': {
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
                },
            },
        },
    },
    ".joint-type-esp-model": {
        ".body .joint-port-body .background": {
            transition: "all 0.25s ease-in-out",
        },
    },
}));
