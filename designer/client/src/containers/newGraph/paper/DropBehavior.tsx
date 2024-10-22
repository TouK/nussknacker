import React, { useEffect, useImperativeHandle } from "react";
import { useDrop, XYCoord } from "react-dnd";
import { DndTypes } from "../../../components/toolbars/creator/Tool";
import { NodeType } from "../../../types";
import { createContextHook } from "../utils/context";
import { usePaper } from "./Paper";

type ContextType = boolean;
const Context = React.createContext<ContextType>(null);

export type DropBehaviorProps = React.PropsWithChildren<{
    enterEffect?: React.EffectCallback;
    onDrop?: (item: NodeType) => void;
    onMove?: (item: NodeType, offset: XYCoord, canDrop: boolean) => void;
    leaveEffect?: React.EffectCallback;
}>;

export const DropBehavior = React.forwardRef<ContextType, DropBehaviorProps>(function PanWithCellBehavior(
    { children, enterEffect, leaveEffect, onDrop, onMove },
    forwardedRef,
) {
    const { paper } = usePaper();

    const [{ isDraggingOver }, connectDropTarget] = useDrop<NodeType, void, { isDraggingOver: boolean }>({
        accept: [DndTypes.ELEMENT],
        drop: (item, monitor) => {
            onDrop?.(item);
        },
        hover: (item, monitor) => {
            const allowed = monitor.isOver() && monitor.canDrop();
            const offset: XYCoord = monitor.getClientOffset();
            onMove?.(item, offset, allowed);
        },
        canDrop: (item, monitor) => true,
        collect: (monitor) => ({
            isDraggingOver: monitor.isOver() && monitor.canDrop(),
        }),
    });

    useEffect(() => {
        connectDropTarget(paper?.el);
    }, [connectDropTarget, paper?.el]);

    useEffect(() => {
        return isDraggingOver ? enterEffect?.() : leaveEffect?.();
    }, [isDraggingOver, enterEffect, leaveEffect]);

    useImperativeHandle(forwardedRef, () => isDraggingOver, [isDraggingOver]);

    return <Context.Provider value={isDraggingOver}>{children}</Context.Provider>;
});

export const useDropBehavior = createContextHook(Context, DropBehavior);
