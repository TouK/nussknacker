import React from "react";
import { Divider } from "@mui/material";

export function ListSeparator(props: { dark?: boolean }): JSX.Element {
    return (
        <Divider
            sx={(theme) => ({
                background: props.dark ? theme.custom.colors.mineShaft : theme.custom.colors.mutedColor,
                margin: "10px 0px",
            })}
        />
    );
}
