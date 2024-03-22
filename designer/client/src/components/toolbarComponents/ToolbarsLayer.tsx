import React, { useCallback, useEffect, useMemo } from "react";
import { ToolbarsSide } from "../../reducers/toolbars";
import { useDispatch, useSelector } from "react-redux";
import { moveToolbar, registerToolbars } from "../../actions/nk/toolbars";
import { ToolbarsContainer } from "./ToolbarsContainer";
import { PanelSide, SidePanel } from "../sidePanels/SidePanel";
import { Toolbar } from "./toolbar";
import { getCapabilities } from "../../reducers/selectors/other";
import { useSurvey } from "../toolbars/useSurvey";
import { DragAndDropContainer } from "./DragAndDropContainer";
import { styled } from "@mui/material";

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

function ToolbarsLayer(props: { toolbars: Toolbar[]; configId: string }): JSX.Element {
    const dispatch = useDispatch();
    const { toolbars, configId } = props;

    useEffect(() => {
        dispatch(registerToolbars(toolbars, configId));
    }, [dispatch, toolbars, configId]);

    const availableToolbars = useToolbarsVisibility(toolbars);

    const onMove = useCallback((from, to) => dispatch(moveToolbar(from, to, configId)), [configId, dispatch]);

    return (
        <DragAndDropContainer onMove={onMove}>
            <SidePanel side={PanelSide.Left}>
                <TopToolbarsContainer availableToolbars={availableToolbars} side={ToolbarsSide.TopLeft} />
                <BottomToolbarsContainer availableToolbars={availableToolbars} side={ToolbarsSide.BottomLeft} />
            </SidePanel>

            <SidePanel side={PanelSide.Right}>
                <TopToolbarsContainer availableToolbars={availableToolbars} side={ToolbarsSide.TopRight} />
                <BottomToolbarsContainer availableToolbars={availableToolbars} side={ToolbarsSide.BottomRight} />
            </SidePanel>
        </DragAndDropContainer>
    );
}

const PaddedToolbarsContainer = styled(ToolbarsContainer)(({ theme }) => ({
    padding: `calc(${theme.spacing(0.375)} / 2) 0`,
}));

const BottomToolbarsContainer = styled(PaddedToolbarsContainer)({
    paddingBottom: 0,
});

const TopToolbarsContainer = styled(PaddedToolbarsContainer)({
    paddingTop: 0,
});

export default ToolbarsLayer;
