import type { DragDropContextProps, DraggableChildrenFn, DroppableProps } from "@hello-pangea/dnd";
import { DragDropContext, Droppable } from "@hello-pangea/dnd";
import { styled } from "@mui/material";
import type { ComponentProps, ElementType, PropsWithChildren } from "react";
import React, { useCallback } from "react";

interface TargetProps extends Pick<DragDropContextProps, "onDragEnd" | "onDragUpdate" | "onDragStart">, Omit<DroppableProps, "children"> {
    CloneWrapper?: ElementType;
    className?: string;
}

// TODO: get rid of renderClone to fix touch ux -> replace CloneWrapper with styles to fix clone translation
function Target({
    children,
    renderClone,
    CloneWrapper = "div",
    onDragEnd,
    onDragStart,
    onDragUpdate,
    className,
    ...props
}: PropsWithChildren<TargetProps>) {
    const clone: DraggableChildrenFn = useCallback(
        (...args) => <CloneWrapper>{renderClone(...args)}</CloneWrapper>,
        [CloneWrapper, renderClone],
    );
    return (
        <DragDropContext onDragEnd={onDragEnd} onDragStart={onDragStart} onDragUpdate={onDragUpdate}>
            <Droppable {...props} renderClone={clone}>
                {(p) => (
                    <div ref={p.innerRef} className={className}>
                        <div {...p.droppableProps}>
                            {children}
                            {p.placeholder}
                        </div>
                    </div>
                )}
            </Droppable>
        </DragDropContext>
    );
}

export type DropTargetProps = ComponentProps<typeof DropTarget>;
export const DropTarget = styled(Target)({});
