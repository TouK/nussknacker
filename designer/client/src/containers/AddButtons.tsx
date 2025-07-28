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

    if (!toolbars[ToolbarsSide.RightDynamic]?.find((t) => t.id === "creator-panel2")) {
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
                    justifyItems: "center",
                    alignContent: "center",
                }}
            >
                <Fade in={!scenario.scenarioGraph.nodes.length}>
                    <Box>
                        <SidePanelToggleButton2 placeholder />
                    </Box>
                </Fade>
            </Overlay>
        </>
    );
};
