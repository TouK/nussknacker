import React, { useEffect } from "react";
import { useDrop } from "react-dnd";
import { DndTypes } from "../../../components/toolbars/creator/Tool";
import { createContextHook, useContextForward } from "../utils/context";
import { usePanWithCellBehavior } from "./PanWithCellBehavior";
import { usePaper } from "./Paper";

type ContextType = boolean;
const Context = React.createContext<ContextType>(null);

export type DropBehaviorProps = React.PropsWithChildren<{ interactive?: boolean }>;

export const DropBehavior = React.forwardRef<ContextType, DropBehaviorProps>(function PanWithCellBehavior(
    { children, interactive },
    forwardedRef,
) {
    const { paper } = usePaper();
    const edgePan = usePanWithCellBehavior();

    const [{ isDraggingOver }, connectDropTarget] = useDrop({
        accept: DndTypes.ELEMENT,
        // drop: console.log,
        hover: (item, monitor) => {
            if (monitor.isOver() && monitor.canDrop()) {
                edgePan.mutableMousePosition.update(monitor.getClientOffset());
            }
        },
        canDrop: (item, monitor) => {
            if (interactive) {
                return true;
            }
        },
        collect: (monitor) => ({
            isDraggingOver: monitor.isOver() && monitor.canDrop(),
        }),
    });

    useEffect(() => {
        connectDropTarget(paper?.el);
    }, [connectDropTarget, paper?.el]);

    useEffect(() => {
        if (isDraggingOver) {
            return edgePan.bindMoveWithEdge();
        }
    }, [edgePan, isDraggingOver]);

    useContextForward(forwardedRef, isDraggingOver);

    return <Context.Provider value={isDraggingOver}>{children}</Context.Provider>;
});

export const useDropBehavior = createContextHook(Context, DropBehavior);
