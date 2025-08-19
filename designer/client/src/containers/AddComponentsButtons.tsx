import { AddBox } from "@mui/icons-material";
import { Fade, IconButton } from "@mui/material";
import { OverridableComponent } from "@mui/material/OverridableComponent";
import type SvgIcon from "@mui/material/SvgIcon/SvgIcon";
import React from "react";
import { useDragLayer } from "react-dnd";
import { useTranslation } from "react-i18next";

import { PanelSide } from "../actions/nk/ui/panelSide";
import { RECT_HEIGHT, RECT_WIDTH } from "../components/graph/EspNode/esp";
import { useGraph } from "../components/graph/GraphContext";
import { Overlay } from "../components/toolbarComponents/Overlay";
import { ComponentFilter } from "../components/toolbars/creator/ToolBox";
import { getScenario } from "../reducers/selectors/graph";
import { getToolbarsConfig } from "../reducers/selectors/toolbars";
import { ToolbarsSide } from "../reducers/toolbars";
import { useAppDispatch, useAppSelector } from "../store/storeHelpers";

function OpenButton({ sourceOnly, Icon = AddBox }: { sourceOnly?: boolean; Icon?: typeof SvgIcon }) {
    const { t } = useTranslation();
    const title = sourceOnly ? t("panels.creator.openSelectFirst", "add source node") : t("panels.creator.openSelect", "add new node");
    const graphGetter = useGraph();
    const dispatch = useAppDispatch();

    return (
        <IconButton
            title={title}
            onClick={() => {
                const paper = graphGetter().processGraphPaper;
                const center = paper.clientToLocalPoint(graphGetter().viewport.center());

                dispatch({
                    type: "OPEN_NODE_SELECTOR",
                    data: {
                        side: PanelSide.RightDynamic,
                        filters: sourceOnly ? [ComponentFilter.sourcesOnly] : [],
                        fromPoint: center.offset(RECT_WIDTH * -0.5, RECT_HEIGHT * -0.5),
                    },
                });
            }}
            disableFocusRipple
            color="primary"
            size="small"
            disableRipple={sourceOnly}
            disableTouchRipple={sourceOnly}
            sx={{
                borderRadius: 0,
                zoom: sourceOnly ? 3 : 1,
                opacity: sourceOnly ? 0.15 : 1,
            }}
        >
            <Icon fontSize="large" />
        </IconButton>
    );
}

export const AddComponentsButtons = () => {
    const scenario = useAppSelector(getScenario);
    const toolbars = useAppSelector(getToolbarsConfig);

    const isDragging = useDragLayer((monitor) => monitor.isDragging());

    if (!toolbars[ToolbarsSide.RightDynamic]?.find((t) => ["creator-panel", "creator-panel-dynamic"].includes(t.id))) {
        return null;
    }

    return (
        <>
            <Overlay gridArea="right" gridRow="top">
                <OpenButton />
            </Overlay>
            <Fade in={!scenario.scenarioGraph.nodes.length && !isDragging} unmountOnExit mountOnEnter>
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
                    <OpenButton sourceOnly />
                </Overlay>
            </Fade>
        </>
    );
};
