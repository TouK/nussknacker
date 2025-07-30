import type { PropsOf } from "@emotion/react";
import React from "react";

import { StyledCollapsibleScrollPanel } from "./CollapsibleScrollPanel";
import { SideContextProvider, useSidePanel } from "./SidePanelsContext";

type SidePanelProps = PropsOf<typeof StyledCollapsibleScrollPanel>;

export const SidePanel = ({ side, ...props }: SidePanelProps) => {
    const { isOpened, isCovered, toggleFullSize, ref } = useSidePanel(side);

    return (
        <SideContextProvider side={side}>
            <StyledCollapsibleScrollPanel
                ref={ref}
                onScrollToggle={toggleFullSize}
                isExpanded={isOpened}
                side={side}
                disabled={isCovered}
                {...props}
            />
        </SideContextProvider>
    );
};
