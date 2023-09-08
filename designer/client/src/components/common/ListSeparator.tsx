import React from "react";
import { Divider } from "@mui/material";
import { variables } from "../../stylesheets/variables";

export function ListSeparator(props: { dark?: boolean }): JSX.Element {
    return (
        <Divider sx={{ background: props.dark ? variables.panelHeaderBackground : variables.panelTitleTextColor, margin: "10px 0px" }} />
    );
}
