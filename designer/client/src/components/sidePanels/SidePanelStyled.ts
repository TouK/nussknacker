import { styled } from "@mui/material";
import { PanelSide } from "./SidePanel";

const SCROLL_THUMB_SIZE = 8;
export const SIDEBAR_WIDTH = 290;
export const PANEL_WIDTH = SIDEBAR_WIDTH + SCROLL_THUMB_SIZE;

type ScrollToggle = {
    side: PanelSide;
};

export const ScrollPanelContent = styled("div")<ScrollToggle>(({ side }) => ({
    width: PANEL_WIDTH,
    boxSizing: "border-box",
    minHeight: "100%",
    display: "flex",
    flexDirection: "column",
    pointerEvents: "none",
    alignItems: side === PanelSide.Left ? "flex-start" : "flex-end",
}));
