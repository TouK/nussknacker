import React, { useCallback } from "react";
import { DropBehavior } from "./paper/DropBehavior";
import { ClickToZoomBehavior, PanWithCellBehavior } from "./paper/PanWithCellBehavior";
import { PanZoomBehavior } from "./paper/PanZoomBehavior";
import { Paper } from "./paper/Paper";

export function InteractivePaper() {
    const domOverlays = useCallback(() => {
        // TODO: replace with checking children
        return Array.from(document.querySelectorAll("[data-testid='SidePanel'] .droppable .draggable-list"));
    }, []);

    return (
        <Paper sx={{ background: "#CCFFCC" }} interactive>
            <PanZoomBehavior interactive>
                <PanWithCellBehavior domOverlays={domOverlays} />
                <ClickToZoomBehavior />
            </PanZoomBehavior>
            <DropBehavior />
        </Paper>
    );
}
