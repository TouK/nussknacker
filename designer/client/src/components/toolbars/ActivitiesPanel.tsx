import React from "react";
import { ToolbarPanelProps } from "../toolbarComponents/DefaultToolbarPanel";
import { ToolbarWrapper } from "../toolbarComponents/toolbarWrapper/ToolbarWrapper";

export const ActivitiesPanel = (props: ToolbarPanelProps) => {
    return (
        <ToolbarWrapper {...props} title={"Activities"}>
            works
        </ToolbarWrapper>
    );
};
