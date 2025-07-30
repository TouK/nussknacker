import { styled } from "@mui/material";

import type { PanelSide } from "../../actions/nk/ui/panelSide";
import { isLeft } from "../../actions/nk/ui/panelSide";
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
    alignItems: isLeft(side) ? "flex-start" : "flex-end",
}));
