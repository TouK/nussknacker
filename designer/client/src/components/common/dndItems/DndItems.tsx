import type { DraggableChildrenFn } from "@hello-pangea/dnd";
import { Draggable } from "@hello-pangea/dnd";
import { alpha, Box } from "@mui/material";
import update from "immutability-helper";
import { cloneDeep } from "lodash";
import React, { useCallback, useRef } from "react";

import { DragHandle, DragHandlerContext } from "./DragHandle";
import type { DropTargetProps } from "./DropTarget";
import { DropTarget } from "./DropTarget";
import { FakeFormWindow } from "./FakeFormWindow";

type PassedProps = Omit<DropTargetProps, "renderClone" | "CloneWrapper" | "onDragEnd" | "onDragStart" | "onDragUpdate" | "droppableId">;

type OwnProps<I> = {
    disabled?: boolean;
    onChange: (value: I[]) => void;
    items: { item: I; el: React.JSX.Element }[];
    onDestinationChange?: (index: number | null) => void;
};

type DndItemsProps<I> = PassedProps & OwnProps<I>;

export function DndItems<I>({ items, onChange, onDestinationChange, disabled, ...passProps }: DndItemsProps<I>) {
    const moveItem = useCallback(
        (source: number, target: number) => {
            if (source >= 0 && target >= 0) {
                const previousFields = cloneDeep(items.map(({ item }) => item));
                const newFields = update(previousFields, {
                    $splice: [
                        [source, 1],
                        [target, 0, previousFields[source]],
                    ],
                });
                onChange(newFields);
            }
            onDestinationChange?.(null);
        },
        [items, onChange, onDestinationChange],
    );

    const droppableId = useRef(Date.now().toString());

    const renderDraggable: DraggableChildrenFn = useCallback(
        (p, s, r) => (
            <Box
                {...p.draggableProps}
                ref={p.innerRef}
                sx={(theme) => ({
                    display: "grid",
                    gridTemplateColumns: "1fr auto",
                    filter: s.isDragging ? `drop-shadow(0px 2px 6px ${alpha(theme.palette.common.black, 0.5)})` : "none",
                })}
                data-testid={`draggable:${r.source.index}`}
            >
                <DragHandlerContext.Provider value={p.dragHandleProps}>
                    {items[r.source.index].el}
                    {!disabled && <DragHandle active={s.isDragging} />}
                </DragHandlerContext.Provider>
            </Box>
        ),
        [disabled, items],
    );

    return (
        <DropTarget
            droppableId={droppableId.current}
            renderClone={renderDraggable}
            CloneWrapper={FakeFormWindow}
            onDragEnd={({ destination, source }) => moveItem(source?.index, destination?.index)}
            onDragStart={({ source }) => onDestinationChange?.(source?.index)}
            onDragUpdate={({ destination }) => onDestinationChange?.(destination?.index)}
            {...passProps}
        >
            {items.map((_, index) => (
                <Draggable key={index} draggableId={`${index}`} index={index} isDragDisabled={disabled}>
                    {renderDraggable}
                </Draggable>
            ))}
        </DropTarget>
    );
}
