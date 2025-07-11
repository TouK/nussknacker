import { Stack } from "@mui/material";
import type { PropsWithChildren } from "react";
import React from "react";

export const InlineButtonsWrapper = ({ children }: PropsWithChildren) => (
    <Stack
        component="span"
        direction="row"
        sx={(theme) => ({
            display: "inline-flex",
            verticalAlign: "text-bottom",
            "& svg": {
                fontSize: ".8em",
            },
            "& button": {
                color: theme.palette.primary.main,
                padding: theme.spacing(0.25),
                "&:focus-within": {
                    outline: "none",
                },
            },
        })}
    >
        {children}
    </Stack>
);
