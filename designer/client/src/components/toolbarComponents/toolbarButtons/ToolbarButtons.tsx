import type { PropsOf } from "@emotion/react";
import type { PropsWithChildren } from "react";
import React, { createContext } from "react";

import { ToolbarButtonWrapper } from "./ToolbarButtonStyled";

export enum ButtonsVariant {
    xs = "xs",
    small = "small",
    label = "label",
    horizontal = "horizontal",
}

type Props = {
    variant?: ButtonsVariant;
} & PropsOf<typeof ToolbarButtonWrapper>;

export const ToolbarButtonsContext = createContext<{ variant: ButtonsVariant }>({ variant: ButtonsVariant.label });

export function ToolbarButtons({ variant = ButtonsVariant.label, ...props }: PropsWithChildren<Props>): JSX.Element {
    return (
        <ToolbarButtonsContext.Provider value={{ variant }}>
            <ToolbarButtonWrapper {...props} />
        </ToolbarButtonsContext.Provider>
    );
}
