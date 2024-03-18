import React from "react";
import { Divider } from "@mui/material";

export function ListSeparator(props: { dark?: boolean }): JSX.Element {
    console.log("separator");
    return (
        <Divider
            sx={(theme) => ({
                background: props.dark ? theme.palette.divider : theme.custom.colors.mutedColor,
                margin: "10px 0px",
            })}
        />
    );
}
