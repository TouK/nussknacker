import type { PropsOf } from "@emotion/react";
import { styled } from "@mui/material";
import type { PropsWithChildren } from "react";
import React, { createContext, forwardRef } from "react";

const ToolbarButtonsContainer = styled("div")(() => ({
    display: "flex",
    flexDirection: "row",
    flexWrap: "wrap",
}));

export enum ButtonsVariant {
    xs = "xs",
    small = "small",
    label = "label",
    horizontal = "horizontal",
}

export const ToolbarButtonsContext = createContext<{ variant: ButtonsVariant }>({ variant: ButtonsVariant.label });

type Props = {
    variant?: ButtonsVariant;
} & PropsOf<typeof ToolbarButtonsContainer>;

export const ToolbarButtons = forwardRef<HTMLDivElement, PropsWithChildren<Props>>(function ToolbarButtons({ variant, ...props }, ref) {
    return (
        <ToolbarButtonsContext.Provider value={{ variant: variant || ButtonsVariant.label }}>
            <ToolbarButtonsContainer {...props} ref={ref} />
        </ToolbarButtonsContext.Provider>
    );
});
