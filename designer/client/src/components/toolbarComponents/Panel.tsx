import { Collapse, styled } from "@mui/material";
import { getLuminance } from "@mui/system/colorManipulator";

import { blendDarken, blendLighten } from "../../containers/theme/helpers";

export const PanelHeader = styled("div")<{ color?: string }>(({ color, theme }) => ({
    display: "flex",
    alignItems: "center",
    background: getLuminance(color) > 0.5 ? blendDarken(color, 0.15) : blendLighten(color, 0.15),
    color: theme.palette.getContrastText(color),
    justifyContent: "space-between",
    padding: theme.spacing(0.25, 0.5),
    flexGrow: 0,
    transition: theme.transitions.create("all", { easing: "ease-in-out" }),
    ":focus": {
        background: theme.palette.action.focus,
    },
    "@media (hover: none)": {
        padding: ".4em",
    },
}));

export const Panel = styled("div")<{ expanded?: boolean; color?: string; width?: number | string }>(
    ({ expanded, color, width = 200, theme }) => ({
        "--panelColor": color,
        pointerEvents: "auto",
        width,
        borderColor: blendDarken(color, 0.25),
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
