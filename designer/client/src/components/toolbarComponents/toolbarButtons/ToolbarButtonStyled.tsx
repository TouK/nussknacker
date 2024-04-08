import { styled } from "@mui/material";
import { PANEL_BUTTON_SIZE } from "../../../stylesheets/variables";

export const Icon = styled("div")({
    flex: 1,
    lineHeight: 0,
    display: "flex",
    flexDirection: "column",
    width: PANEL_BUTTON_SIZE / 2,
});

export const ToolbarButtonWrapper = styled("div")(() => ({
    display: "flex",
    flexDirection: "row",
    flexWrap: "wrap",
}));
