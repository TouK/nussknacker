import { styled } from "@mui/material";

import { PanelSide } from "../../actions/nk";
import { PANEL_WIDTH } from "../../stylesheets/variables";

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
