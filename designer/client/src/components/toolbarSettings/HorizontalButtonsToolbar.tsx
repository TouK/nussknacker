import { alpha, Stack } from "@mui/material";
import type { PropsWithChildren } from "react";
import React from "react";

import { useDragHandler } from "../common/dndItems/DragHandle";
import { ButtonsVariant, ToolbarButtons } from "../toolbarComponents/toolbarButtons/ToolbarButtons";
import type { ToolbarConfig } from "./types";

type Props = PropsWithChildren<unknown>;

export const HorizontalToolbar = ({ children }: Props) => {
    const handleProps = useDragHandler();
    return (
        <Stack
            direction="row"
            sx={(theme) => ({
                alignItems: "normal",
                position: "relative",
                "::before": {
                    content: '""',
                    position: "absolute",
                    inset: 0,
                    zIndex: -1,
                    borderRadius: theme.spacing(0.75),
                    boxShadow: "0 0 2px rgba(0,0,0,0.8),0 0 20px rgba(0,0,0,0.8)",
                },
                ">*": {
                    borderRadius: theme.spacing(0.75),
                    padding: 0.3,
                    background: alpha(theme.palette.primary.main, 0.25),
                    backdropFilter: "blur(20px)",
                },
            })}
            {...handleProps}
        >
            {children}
        </Stack>
    );
};

export const HorizontalButtonsToolbar = ({ children }: PropsWithChildren<Omit<ToolbarConfig, "buttons">>) => {
    return (
        <HorizontalToolbar>
            <ToolbarButtons variant={ButtonsVariant.xs}>{children}</ToolbarButtons>
        </HorizontalToolbar>
    );
};
