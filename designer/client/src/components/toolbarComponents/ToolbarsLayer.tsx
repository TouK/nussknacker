import React, { forwardRef, MutableRefObject, useCallback, useEffect, useMemo, useState } from "react";
import { ToolbarsSide } from "../../reducers/toolbars";
import { useDispatch, useSelector } from "react-redux";
import { moveToolbar, registerToolbars } from "../../actions/nk/toolbars";
import { ToolbarsContainer } from "./ToolbarsContainer";
import { PanelSide, SidePanel, SidePanelRef } from "../sidePanels/SidePanel";
import { Toolbar } from "./toolbar";
import { getCapabilities } from "../../reducers/selectors/other";
import { useSurvey } from "../toolbars/useSurvey";
import { DragAndDropContainer } from "./DragAndDropContainer";
import { styled } from "@mui/material";
import { ScenarioDescription } from "../../containers/ScenarioDescription";

export function useToolbarsVisibility(toolbars: Toolbar[]) {
    const { editFrontend } = useSelector(getCapabilities);
    const [showSurvey] = useSurvey();

    const hiddenToolbars = useMemo(
        () => ({
            "survey-panel": !showSurvey,
            "creator-panel": !editFrontend,
        }),
        [editFrontend, showSurvey],
    );

    return useMemo(() => toolbars.map((t) => ({ ...t, isHidden: hiddenToolbars[t.id] })), [hiddenToolbars, toolbars]);
}

type ToolbarsLayerProps = {
    toolbars: Toolbar[];
    configId: string;
};

export type ToolbarsLayerRef = {
    [k in PanelSide]: MutableRefObject<SidePanelRef>;
};

const ToolbarsLayer = forwardRef<ToolbarsLayerRef, ToolbarsLayerProps>(function ToolbarsLayer(props, forwardedRef): JSX.Element {
    const dispatch = useDispatch();
    const { toolbars, configId } = props;

    useEffect(() => {
        dispatch(registerToolbars(toolbars, configId));
    }, [dispatch, toolbars, configId]);

    const availableToolbars = useToolbarsVisibility(toolbars);

    const onMove = useCallback((from, to) => dispatch(moveToolbar(from, to, configId)), [configId, dispatch]);

    const [left, setLeft] = useState<number>(0);
    const [right, setRight] = useState<number>(0);
    const leftSetter = useCallback((i) => setLeft(() => (i?.isOpened ? i?.rect?.width : 0)), []);
    const rightSetter = useCallback((i) => setRight(() => (i?.isOpened ? i?.rect?.width : 0)), []);
    return (
        <DragAndDropContainer onMove={onMove}>
            <SidePanel ref={leftSetter} side={PanelSide.Left}>
                <StyledToolbarsContainer availableToolbars={availableToolbars} side={ToolbarsSide.TopLeft} />
                <StyledToolbarsContainer availableToolbars={availableToolbars} side={ToolbarsSide.BottomLeft} />
            </SidePanel>

            <ScenarioDescription left={left} right={right} />

            <SidePanel ref={rightSetter} side={PanelSide.Right}>
                <StyledToolbarsContainer availableToolbars={availableToolbars} side={ToolbarsSide.TopRight} />
                <StyledToolbarsContainer availableToolbars={availableToolbars} side={ToolbarsSide.BottomRight} />
            </SidePanel>
        </DragAndDropContainer>
    );
});

const StyledToolbarsContainer = styled(ToolbarsContainer)(({ theme, side }) => {
    const padding = `calc(${theme.spacing(0.375)} / 2)`;
    switch (side) {
        case ToolbarsSide.TopLeft:
        case ToolbarsSide.TopRight:
            return { paddingBottom: padding };
        case ToolbarsSide.BottomLeft:
        case ToolbarsSide.BottomRight:
            return { paddingTop: padding };
        default:
            return { paddingTop: padding, paddingBottom: padding };
    }
});

export default ToolbarsLayer;
