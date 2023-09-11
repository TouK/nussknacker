import React, { PropsWithChildren, createContext } from "react";
import styled from "@emotion/styled";

export enum ButtonsVariant {
    small = "small",
    label = "label",
}

type Props = {
    variant?: ButtonsVariant;
};

const ToolbarButtonWrapper = styled.div(() => ({
    display: "flex",
    flexDirection: "row",
    flexWrap: "wrap",
}));

export const ToolbarButtonsContext = createContext<{ variant: ButtonsVariant }>({ variant: ButtonsVariant.label });

export function ToolbarButtons(props: PropsWithChildren<Props>): JSX.Element {
    const { variant = ButtonsVariant.label } = props;

    return (
        <ToolbarButtonsContext.Provider value={{ variant }}>
            <ToolbarButtonWrapper>{props.children}</ToolbarButtonWrapper>
        </ToolbarButtonsContext.Provider>
    );
}
