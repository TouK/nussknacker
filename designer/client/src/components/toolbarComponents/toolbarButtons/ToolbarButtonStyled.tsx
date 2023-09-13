import { styled } from "@mui/material";
import { variables } from "../../../stylesheets/variables";
import { ButtonsVariant } from "./index";

export const Icon = styled("div")({
    flex: 1,
    lineHeight: 0,
    display: "flex",
    flexDirection: "column",
    width: parseFloat(variables.buttonSize) / 2,
});

export const Label = styled("div")<{
    variant: ButtonsVariant;
}>(({ variant }) => ({
    display: variant === ButtonsVariant.small ? "none" : "unset",
}));
