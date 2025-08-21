import type { OnDragEndResponder, OnDragStartResponder, SensorAPI } from "@hello-pangea/dnd";
import { DragDropContext } from "@hello-pangea/dnd";
import { alpha, GlobalStyles, useTheme } from "@mui/material";
import type { PropsWithChildren } from "react";
import { useMemo } from "react";
import React, { useCallback, useState } from "react";
import { useDebouncedValue } from "rooks";

import type { ToolbarPosition } from "../../actions/nk/toolbars";
import { PendingPromise } from "../../common/PendingPromise";
import { EventTrackingSelector, EventTrackingType, useEventTracking } from "../../containers/event-tracking";
import { SIDEBAR_WIDTH } from "../../stylesheets/variables";
import getKeyboardSensor from "./sensors/use-keyboard-sensor";
import getMouseSensor from "./sensors/use-mouse-sensor";
import { DRAGGABLE_LIST_CLASSNAME, DRAGGING_FROM_CLASSNAME, DRAGGING_OVER_CLASSNAME, DROPPABLE_CLASSNAME } from "./ToolbarsContainer";

type Props = PropsWithChildren<{
    onMove: (from: ToolbarPosition, to: ToolbarPosition) => void;
}>;

export const TOOLBAR_DRAGGABLE_TYPE = "TOOLBAR";

export const DraggableIdContext = React.createContext<string | null>(null);

export function DragAndDropContainer({ children, onMove }: Props) {
    const animationDelay = 500;
    const [draggableId, setDraggableId] = useState<string | null>(null);
    const [debouncedDraggableId, immediatelySetDraggableId] = useDebouncedValue(draggableId, animationDelay / 4);
    const clearDraggableId = useCallback(() => {
        setDraggableId(null);
        immediatelySetDraggableId(null);
    }, [immediatelySetDraggableId]);

    const { trackEvent } = useEventTracking();

    const onDragEnd: OnDragEndResponder = useCallback(
        (result) => {
            trackEvent({ selector: EventTrackingSelector.ToolbarPanel, event: EventTrackingType.Move });
            clearDraggableId();
            const { destination, type, reason, source } = result;
            if (reason === "DROP" && type === TOOLBAR_DRAGGABLE_TYPE && destination) {
                const from: ToolbarPosition = [source.droppableId, source.index];
                const to: ToolbarPosition = [destination.droppableId, destination.index];
                onMove(from, to);
            }
        },
        [clearDraggableId, onMove, trackEvent],
    );

    const onDragStart: OnDragStartResponder = useCallback(({ draggableId }) => {
        setDraggableId(draggableId);
    }, []);

    const theme = useTheme();

    const { mouseSensor, keyboardSensor } = useMemo(() => {
        const delayPromiseGetter = (draggableId: string) => {
            setDraggableId(draggableId);
            const delayPromise = new PendingPromise<{ end: PendingPromise<void> }>();
            delayPromise.catch(() => clearDraggableId());
            setTimeout(() => {
                const endPromise = new PendingPromise<void>();
                endPromise.then(() => clearDraggableId());
                delayPromise.resolve({ end: endPromise });
            }, animationDelay);
            return delayPromise;
        };
        return {
            mouseSensor: (api: SensorAPI) => getMouseSensor(api, delayPromiseGetter),
            keyboardSensor: (api: SensorAPI) => getKeyboardSensor(api, delayPromiseGetter),
        };
    }, [clearDraggableId, animationDelay]);

    return (
        <DragDropContext
            sensors={[mouseSensor, keyboardSensor]}
            onDragEnd={onDragEnd}
            onDragStart={onDragStart}
            enableDefaultSensors={false}
        >
            <GlobalStyles
                styles={{
                    [`.${DRAGGABLE_LIST_CLASSNAME}`]: {
                        minHeight: draggableId ? "1em" : null,
                        minWidth: SIDEBAR_WIDTH,
                        position: "relative",
                    },
                }}
            />
            <GlobalStyles
                styles={{
                    [`.${DROPPABLE_CLASSNAME}`]: {
                        [`.${DRAGGABLE_LIST_CLASSNAME}::after`]: {
                            content: draggableId ? "''" : null,
                            transition: theme.transitions.create("all", { duration: theme.transitions.duration.standard }),
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
            <DraggableIdContext.Provider value={debouncedDraggableId}>{children}</DraggableIdContext.Provider>
        </DragDropContext>
    );
}
