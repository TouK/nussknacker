import { Collapse, styled } from "@mui/material";
import { getContrastColor, getDarkenContrastColor } from "../../containers/theme/helpers";

const __panelBorder = "--panel-Border";
const __panelHeaderText = "--panel-HeaderText";
const __panelColor = "--panel-Color";
const __panelText = "--panel-Text";

export const PanelHeader = styled("div")(({ theme }) => ({
    display: "flex",
    background: `var(${__panelBorder})`,
    color: `var(${__panelHeaderText})`,
    justifyContent: "space-between",
    padding: theme.spacing(0.25, 0.5),
    flexGrow: 0,
    ":focus": {
        background: theme.palette.action.focus,
    },
}));

export const Panel = styled("div")<{ expanded?: boolean; color?: string; width?: number | string }>(
    ({ expanded, color, width = 200, theme }) => ({
        [__panelColor]: color,
        [__panelBorder]: getDarkenContrastColor(color, 1.25),
        [__panelText]: getContrastColor(color),
        [__panelHeaderText]: getContrastColor(getDarkenContrastColor(color, 1.25)),
        pointerEvents: "auto",
        width,
        borderColor: `var(${__panelBorder})`,
        background: `var(${__panelColor})`,
        opacity: expanded ? 1 : 0.86,
        transition: theme.transitions.create("all", { easing: "ease-in-out" }),
        display: "flex",
        flexDirection: "column",
    }),
);

const PanelContent = styled("div")({
    userSelect: "text",
    display: "flow-root",
    color: `var(${__panelText})`,
});

export const CollapsiblePanelContent = PanelContent.withComponent(Collapse);
