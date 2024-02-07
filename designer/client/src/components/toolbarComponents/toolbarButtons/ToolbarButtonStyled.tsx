import { styled } from "@mui/material";
import { variables } from "../../../stylesheets/variables";

export const Icon = styled("div")({
    flex: 1,
    lineHeight: 0,
    display: "flex",
    flexDirection: "column",
    width: parseFloat(variables.buttonSize) / 2,
});

export const ToolbarButtonWrapper = styled("div")(() => ({
    display: "flex",
    flexDirection: "row",
    flexWrap: "wrap",
}));
