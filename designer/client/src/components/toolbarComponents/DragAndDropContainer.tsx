import { DragDropContext, OnDragEndResponder, OnDragStartResponder } from "@hello-pangea/dnd";
import { alpha, GlobalStyles, useTheme } from "@mui/material";
import React, { PropsWithChildren, useCallback, useState } from "react";
import { ToolbarPosition } from "../../actions/nk/toolbars";
import { EventTrackingSelector, EventTrackingType, useEventTracking } from "../../containers/event-tracking";
import { SIDEBAR_WIDTH } from "../../stylesheets/variables";
import { DRAGGABLE_LIST_CLASSNAME, DRAGGING_FROM_CLASSNAME, DRAGGING_OVER_CLASSNAME, DROPPABLE_CLASSNAME } from "./ToolbarsContainer";

type Props = PropsWithChildren<{
    onMove: (from: ToolbarPosition, to: ToolbarPosition) => void;
}>;

export const TOOLBAR_DRAGGABLE_TYPE = "TOOLBAR";

export const DraggableIdContext = React.createContext<string | null>(null);

export function DragAndDropContainer({ children, onMove }: Props) {
    const [draggableId, setDraggableId] = useState<string | null>(null);
    const { trackEvent } = useEventTracking();

    const onDragEnd: OnDragEndResponder = useCallback(
        (result) => {
            trackEvent({ selector: EventTrackingSelector.ToolbarPanel, event: EventTrackingType.Move });
            setDraggableId(null);
            const { destination, type, reason, source } = result;
            if (reason === "DROP" && type === TOOLBAR_DRAGGABLE_TYPE && destination) {
                const from: ToolbarPosition = [source.droppableId, source.index];
                const to: ToolbarPosition = [destination.droppableId, destination.index];
                onMove(from, to);
            }
        },
        [onMove, trackEvent],
    );

    const onDragStart: OnDragStartResponder = useCallback(({ draggableId }) => {
        setDraggableId(draggableId);
    }, []);

    const theme = useTheme();

    return (
        <DragDropContext onDragEnd={onDragEnd} onDragStart={onDragStart}>
            <GlobalStyles
                styles={{
                    [`.${DRAGGABLE_LIST_CLASSNAME}`]: {
                        minHeight: draggableId ? "1em" : null,
                        minWidth: SIDEBAR_WIDTH,
                        position: "relative",
                        // backgroundColor: theme.palette.background.paper,
                    },
                }}
            />
            <GlobalStyles
                styles={{
                    [`.${DROPPABLE_CLASSNAME}`]: {
                        [`.${DRAGGABLE_LIST_CLASSNAME}::after`]: {
                            content: draggableId ? "''" : null,
                            transition: theme.transitions.create(["all"], { duration: theme.transitions.duration.standard }),
                            position: "absolute",
                            top: 0,
                            left: 0,
                            right: 0,
                            bottom: 0,
                            backdropFilter: "blur(0.5px)",
                            background: theme.palette.action.selected,
                            outline: `3px dashed ${theme.palette.common.white}`,
                            outlineOffset: -4,
                        },
                        [`.${DRAGGING_FROM_CLASSNAME} > .${DRAGGABLE_LIST_CLASSNAME}::after`]: {
                            background: alpha(theme.palette.warning.light, 0.2),
                            outlineColor: theme.palette.warning.light,
                        },
                        [`.${DRAGGING_OVER_CLASSNAME} > .${DRAGGABLE_LIST_CLASSNAME}::after`]: {
                            background: alpha(theme.palette.info.main, 0.2),
                            outlineColor: theme.palette.info.main,
                        },
                    },
                }}
            />
            <DraggableIdContext.Provider value={draggableId}>{children}</DraggableIdContext.Provider>
        </DragDropContext>
    );
}
