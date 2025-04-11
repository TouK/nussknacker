import type { PropsOf } from "@emotion/react";
import { styled } from "@mui/material";
import type { PropsWithChildren } from "react";
import React, { createContext } from "react";

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

export function ToolbarButtons({ variant = ButtonsVariant.label, ...props }: PropsWithChildren<Props>): JSX.Element {
    return (
        <ToolbarButtonsContext.Provider value={{ variant }}>
            <ToolbarButtonsContainer {...props} />
        </ToolbarButtonsContext.Provider>
    );
}
