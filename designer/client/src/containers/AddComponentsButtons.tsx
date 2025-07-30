import { AddBoxOutlined } from "@mui/icons-material";
import { Box, Fade, IconButton } from "@mui/material";
import React from "react";
import { useTranslation } from "react-i18next";
import { useSelector } from "react-redux";

import { PanelSide } from "../actions/nk/ui/panelSide";
import { RECT_HEIGHT, RECT_WIDTH } from "../components/graph/EspNode/esp";
import { useGraph } from "../components/graph/GraphContext";
import { Overlay } from "../components/toolbarComponents/Overlay";
import { globalEventBus } from "../components/toolbars/creator/globalEventBus";
import { ComponentFilter } from "../components/toolbars/creator/ToolBox";
import { getScenario } from "../reducers/selectors/graph";
import { getToolbarsConfig } from "../reducers/selectors/toolbars";
import { ToolbarsSide } from "../reducers/toolbars";

function OpenButton({ sourceOnly }: { sourceOnly?: boolean }) {
    const { t } = useTranslation();
    const title = sourceOnly ? t("panels.creator.openSelectFirst", "add source node") : t("panels.creator.openSelect", "add new node");
    const graphGetter = useGraph();

    return (
        <IconButton
            title={title}
            onClick={() => {
                const paper = graphGetter().processGraphPaper;
                const center = paper.clientToLocalPoint(graphGetter().viewport.center());
                globalEventBus.emit("openNodeSelector", {
                    side: PanelSide.RightDynamic,
                    filters: sourceOnly ? [ComponentFilter.sourcesOnly] : [],
                    fromPoint: center.offset(RECT_WIDTH * -0.5, RECT_HEIGHT * -0.5),
                });
            }}
            disableFocusRipple
            color="inherit"
            size="small"
            disableRipple={sourceOnly}
            disableTouchRipple={sourceOnly}
            sx={{
                borderRadius: 0,
                zoom: sourceOnly ? 3 : 1,
                opacity: sourceOnly ? 0.2 : 1,
            }}
        >
            <AddBoxOutlined fontSize="large" />
        </IconButton>
    );
}

export const AddComponentsButtons = () => {
    const scenario = useSelector(getScenario);
    const toolbars = useSelector(getToolbarsConfig);

    if (!toolbars[ToolbarsSide.RightDynamic]?.find((t) => ["creator-panel", "creator-panel-dynamic"].includes(t.id))) {
        return null;
    }

    return (
        <>
            <Overlay gridArea="right" gridRow="top">
                <OpenButton />
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
                        <OpenButton sourceOnly />
                    </Box>
                </Fade>
            </Overlay>
        </>
    );
};
