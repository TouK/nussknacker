import { DragIndicator } from "@mui/icons-material";
import { Divider, Stack } from "@mui/material";
import React, { PropsWithChildren } from "react";
import { useDragHandler } from "../common/dndItems/DragHandle";
import { ButtonsVariant, ToolbarButtons } from "../toolbarComponents/toolbarButtons";
import { ToolbarConfig } from "./types";

export const HorizontalToolbar = ({ children }: PropsWithChildren) => {
    const handleProps = useDragHandler();
    return (
        <Stack
            direction="row"
            sx={(theme) => ({
                alignItems: "center",
                background: theme.palette.background.paper,
                boxShadow: theme.shadows[5],
                borderRadius: theme.spacing(0.5),
            })}
            {...handleProps}
        >
            <DragIndicator
                sx={(theme) => ({
                    color: theme.palette.action.disabled,
                    width: "12px",
                })}
            />
            <Divider orientation="vertical" flexItem />
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
