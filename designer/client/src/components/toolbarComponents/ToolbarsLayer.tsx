import React, { useCallback, useEffect, useMemo, useState } from "react";
import { DragDropContext, DropResult } from "react-beautiful-dnd";
import { ToolbarsSide } from "../../reducers/toolbars";
import { useDispatch, useSelector } from "react-redux";
import { moveToolbar, registerToolbars } from "../../actions/nk/toolbars";
import { ToolbarsContainer } from "./ToolbarsContainer";
import { SidePanel, PanelSide } from "../sidePanels/SidePanel";
import { Toolbar } from "./toolbar";
import { getCapabilities } from "../../reducers/selectors/other";
import { useSurvey } from "../toolbars/useSurvey";
import { cx } from "@emotion/css";

export const TOOLBAR_DRAGGABLE_TYPE = "TOOLBAR";

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

    const [isDragging, setIsDragging] = useState(false);

    useEffect(() => {
        dispatch(registerToolbars(toolbars, configId));
    }, [dispatch, toolbars, configId]);

    const onDragEnd = useCallback(
        (result: DropResult) => {
            setIsDragging(false);
            const { destination, type, reason, source } = result;
            if (reason === "DROP" && type === TOOLBAR_DRAGGABLE_TYPE && destination) {
                dispatch(moveToolbar([source.droppableId, source.index], [destination.droppableId, destination.index], configId));
            }
        },
        [configId, dispatch],
    );

    const onDragStart = useCallback(() => {
        setIsDragging(true);
    }, []);

    const availableToolbars = useToolbarsVisibility(toolbars);

    return (
        <DragDropContext onDragEnd={onDragEnd} onDragStart={onDragStart}>
            <SidePanel side={PanelSide.Left} className={cx("left", isDragging && "is-dragging-started")}>
                <ToolbarsContainer availableToolbars={availableToolbars} side={ToolbarsSide.TopLeft} className={"top"} />
                <ToolbarsContainer availableToolbars={availableToolbars} side={ToolbarsSide.BottomLeft} className={"bottom"} />
            </SidePanel>

            <SidePanel side={PanelSide.Right} className={cx("right", isDragging && "is-dragging-started")}>
                <ToolbarsContainer availableToolbars={availableToolbars} side={ToolbarsSide.TopRight} className={"top"} />
                <ToolbarsContainer availableToolbars={availableToolbars} side={ToolbarsSide.BottomRight} className={"bottom"} />
            </SidePanel>
        </DragDropContext>
    );
}

export default ToolbarsLayer;
