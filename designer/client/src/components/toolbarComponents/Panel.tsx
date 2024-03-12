import { Collapse, darken, lighten, styled } from "@mui/material";

export const PanelHeader = styled("div")<{ color?: string }>(({ color, theme }) => ({
    display: "flex",
    background: lighten(color, 0.025),
    color: theme.typography.overline.color,
    justifyContent: "space-between",
    padding: theme.spacing(0.25, 0.5),
    flexGrow: 0,
    ":focus": {
        background: theme.palette.action.focus,
    },
}));

export const Panel = styled("div")<{ expanded?: boolean; color?: string; width?: number | string }>(
    ({ expanded, color, width = 200, theme }) => ({
        pointerEvents: "auto",
        width,
        borderColor: darken(color, 0.25),
        background: color,
        opacity: expanded ? 1 : 0.86,
        transition: theme.transitions.create("all", { easing: "ease-in-out" }),
        display: "flex",
        flexDirection: "column",
    }),
);

const PanelContent = styled("div")(({ theme }) => ({
    userSelect: "text",
    display: "flow-root",
    color: theme.typography.overline.color,
}));

export const CollapsiblePanelContent = PanelContent.withComponent(Collapse);
