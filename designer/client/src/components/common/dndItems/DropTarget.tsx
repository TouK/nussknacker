import { ReactComponentLike } from "prop-types";
import React, { PropsWithChildren, useCallback } from "react";
import { DragDropContext, DragDropContextProps, DraggableChildrenFn, Droppable, DroppableProps } from "@hello-pangea/dnd";

// TODO: get rid of renderClone to fix touch ux -> replace CloneWrapper with styles to fix clone translation
export function DropTarget({
    children,
    renderClone,
    CloneWrapper = "div",
    onDragEnd,
    onDragStart,
    onDragUpdate,
    ...props
}: PropsWithChildren<
    { CloneWrapper?: ReactComponentLike } & Pick<DragDropContextProps, "onDragEnd" | "onDragUpdate" | "onDragStart"> &
        Omit<DroppableProps, "children">
>): JSX.Element {
    const clone: DraggableChildrenFn = useCallback(
        (...args) => <CloneWrapper>{renderClone(...args)}</CloneWrapper>,
        [CloneWrapper, renderClone],
    );
    return (
        <DragDropContext onDragEnd={onDragEnd} onDragStart={onDragStart} onDragUpdate={onDragUpdate}>
            <Droppable {...props} renderClone={clone}>
                {(p) => (
                    <div ref={p.innerRef}>
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
