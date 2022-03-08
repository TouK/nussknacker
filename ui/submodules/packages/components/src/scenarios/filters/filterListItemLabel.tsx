import { NuIcon } from "../../common";
import React from "react";
import { Stack } from "@mui/material";

export function FilterListItemLabel({ name, icon }: { name: string; icon?: string }): JSX.Element {
    return icon ? (
        <Stack direction="row" spacing={1} alignItems="center">
            <span>{name}</span>
            <NuIcon
                src={icon}
                sx={{ color: "primary.main" }}
                style={{
                    fontSize: "1.2em",
                }}
            />
        </Stack>
    ) : (
        <>{name}</>
    );
}
