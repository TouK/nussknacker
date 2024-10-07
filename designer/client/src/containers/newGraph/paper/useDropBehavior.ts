import { useEffect } from "react";
import { useDrop } from "react-dnd";
import { DndTypes } from "../../../components/toolbars/creator/Tool";
import { PaperBehaviorProps, PaperContextType } from "./Paper";

export function useDropBehavior([{ paper, edgePan, panZoom }]: [PaperContextType], { interactive }: PaperBehaviorProps) {
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
            return edgePan.bindMoveWithEdge(panZoom?.panBy);
        }
    }, [edgePan, isDraggingOver, panZoom?.panBy]);
    return isDraggingOver;
}
