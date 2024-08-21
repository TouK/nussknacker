import React from "react";
import { StyledCollapsibleScrollPanel } from "./CollapsibleScrollPanel";
import { PropsOf } from "@emotion/react";
import { SideContextProvider, useSidePanel } from "./SidePanelsContext";

type SidePanelProps = PropsOf<typeof StyledCollapsibleScrollPanel>;

export const SidePanel = ({ side, ...props }: SidePanelProps) => {
    const { isOpened, toggleFullSize } = useSidePanel(side);

    return (
        <SideContextProvider side={side}>
            <StyledCollapsibleScrollPanel onScrollToggle={toggleFullSize} isExpanded={isOpened} side={side} {...props} />
        </SideContextProvider>
    );
};
