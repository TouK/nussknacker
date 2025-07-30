import { Box, Fade } from "@mui/material";
import React from "react";
import { useSelector } from "react-redux";

import { SidePanelToggleButton2 } from "../components/SidePanelToggleButton";
import { Overlay } from "../components/toolbarComponents/Overlay";
import { getScenario } from "../reducers/selectors/graph";
import { getToolbarsConfig } from "../reducers/selectors/toolbars";
import { ToolbarsSide } from "../reducers/toolbars";

export const AddButtons = () => {
    const scenario = useSelector(getScenario);
    const toolbars = useSelector(getToolbarsConfig);

    if (!toolbars[ToolbarsSide.RightDynamic]?.find((t) => ["creator-panel", "creator-panel-dynamic"].includes(t.id))) {
        return null;
    }

    return (
        <>
            <Overlay gridArea="right" gridRow="top">
                <SidePanelToggleButton2 />
            </Overlay>
            <Overlay
                gridRow="top/span 2"
                gridColumn="left/right"
                sx={{
                    display: "flex",
                    flexWrap: "wrap",
                    alignContent: "center",
                    justifyContent: "center",
                }}
            >
                <Fade in={!scenario.scenarioGraph.nodes.length} unmountOnExit mountOnEnter>
                    <Box>
                        <SidePanelToggleButton2 placeholder />
                    </Box>
                </Fade>
            </Overlay>
        </>
    );
};
