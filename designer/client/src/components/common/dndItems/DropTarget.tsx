import type { DragDropContextProps, DraggableChildrenFn, DroppableProps } from "@hello-pangea/dnd";
import { DragDropContext, Droppable } from "@hello-pangea/dnd";
import type { ReactComponentLike } from "prop-types";
import type { PropsWithChildren } from "react";
import React, { useCallback } from "react";

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
>): React.JSX.Element {
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
