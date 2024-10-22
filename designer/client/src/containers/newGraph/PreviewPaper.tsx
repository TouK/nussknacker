import React from "react";
import { PanZoomBehavior } from "./paper/PanZoomBehavior";
import { Paper } from "./paper/Paper";

export function PreviewPaper() {
    return (
        <Paper sx={{ background: "#CCCCFF" }}>
            <PanZoomBehavior />
        </Paper>
    );
}
