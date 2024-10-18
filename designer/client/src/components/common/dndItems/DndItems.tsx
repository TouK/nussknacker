import update from "immutability-helper";
import { cloneDeep } from "lodash";
import React, { useCallback, useRef } from "react";
import { Draggable, DraggableChildrenFn } from "@hello-pangea/dnd";
import { DragHandle, DragHandlerContext } from "./DragHandle";
import { DropTarget } from "./DropTarget";
import { FakeFormWindow } from "./FakeFormWindow";
import { ItemsProps } from "./Items";
import { alpha, Box } from "@mui/material";

interface DndListProps<I> extends ItemsProps<I> {
    disabled?: boolean;
    onChange: (value: I[]) => void;
    onDestinationChange?: (index: number | null) => void;
}

export function DndItems<I>(props: DndListProps<I>): JSX.Element {
    const { items, onChange, onDestinationChange, disabled } = props;

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
        >
            {items.map((_, index) => (
                <Draggable key={index} draggableId={`${index}`} index={index} isDragDisabled={disabled}>
                    {renderDraggable}
                </Draggable>
            ))}
        </DropTarget>
    );
}
