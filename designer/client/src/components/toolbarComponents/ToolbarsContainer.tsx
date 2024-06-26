import { ToolbarsSide } from "../../reducers/toolbars";
import {
    Draggable,
    DraggableChildrenFn,
    DraggableLocation,
    DraggableProvided,
    DraggableRubric,
    DraggableStateSnapshot,
    Droppable,
    DroppableProps,
    DroppableProvided,
    DroppableStateSnapshot,
} from "@hello-pangea/dnd";
import React, { CSSProperties, Suspense, useCallback, useMemo } from "react";
import { useSelector } from "react-redux";
import { DragHandlerContext, SimpleDragHandle } from "../common/dndItems/DragHandle";
import { getOrderForPosition } from "../../reducers/selectors/toolbars";
import { Toolbar } from "./toolbar";
import { cx } from "@emotion/css";
import { alpha, styled } from "@mui/material";
import { TOOLBAR_DRAGGABLE_TYPE } from "./DragAndDropContainer";

export const StyledDraggableItem = styled("div")(({ theme }) => ({
    [`&.${DRAGGING_CLASSNAME}`]: {
        backdropFilter: "blur(2px)",

        "& > *": {
            transition: theme.transitions.create(["all"], { duration: theme.transitions.duration.standard }),
            opacity: 0.5,
        },

        "::after": {
            mixBlendMode: "color",
            transition: theme.transitions.create(["all"], { duration: theme.transitions.duration.standard }),
            content: "''",
            lineHeight: 0,
            position: "absolute",
            inset: 0,
            backdropFilter: "blur(0.5px)",
            background: alpha(theme.palette.error.main, 0.25),
            outline: `2px dashed ${theme.palette.error.main}`,
            outlineOffset: -3,
        },

        ":focus, *:focus": {
            outline: "none",
        },
    },

    [`&.${DRAGGING_OVER_CLASSNAME}`]: {
        "& > *": {
            opacity: 1,
        },

        "::after": {
            backdropFilter: "none",
            background: alpha(theme.palette.primary.main, 0.25),
            outline: `4px solid ${theme.palette.primary.main}`,
            outlineOffset: -2,
        },
    },

    [`&.${ANIMATING_CLASSNAME}`]: {
        ".content": {
            opacity: 1,
        },
    },
}));

interface Rubric extends DraggableRubric {
    source: DraggableLocation;
}

const fixAnimation = (style: CSSProperties) => ({ ...style, transitionDuration: `${0.0001}s` });
const getStyle = (style: CSSProperties, s: DraggableStateSnapshot) => (s.isDropAnimating && s.draggingOver ? fixAnimation(style) : style);

function sortByIdsFrom(orderElement: string[]) {
    return ({ id: a }, { id: b }) => orderElement.findIndex((v) => v === a) - orderElement.findIndex((v) => v === b);
}

type Props = {
    side: ToolbarsSide;
    availableToolbars: Toolbar[];
    className?: string;
};

export const DRAGGABLE_LIST_CLASSNAME = "draggable-list";
export const DROPPABLE_CLASSNAME = "droppable";
export const DRAGGABLE_CLASSNAME = "draggable";
export const DRAGGING_OVER_CLASSNAME = "is-dragging-over";
export const DRAGGING_FROM_CLASSNAME = "is-dragging-from";
export const DRAGGING_CLASSNAME = "is-dragging";
export const ANIMATING_CLASSNAME = "is-animating";

const DroppableContainer = styled("div")({
    display: "flex",
    flexDirection: "column",
    flexGrow: 1,
    justifyContent: "space-around",
    "&:first-of-type": {
        justifyContent: "flex-start",
    },
    "&:last-child": {
        justifyContent: "flex-end",
    },
});

export function ToolbarsContainer(props: Props): JSX.Element {
    const { side, availableToolbars, className } = props;
    const selector = useMemo(() => getOrderForPosition(side), [side]);
    const order = useSelector(selector);

    const ordered = availableToolbars.filter(({ id }) => order.includes(id)).sort(sortByIdsFrom(order));

    const renderDraggable: DraggableChildrenFn = useCallback(
        (p: DraggableProvided, s: DraggableStateSnapshot, r: Rubric) => (
            <div ref={p.innerRef} {...p.draggableProps} style={getStyle(p.draggableProps.style, s)} className={DRAGGABLE_CLASSNAME}>
                <DragHandlerContext.Provider value={p.dragHandleProps}>
                    <StyledDraggableItem
                        className={cx(
                            s.isDragging && DRAGGING_CLASSNAME,
                            s.draggingOver && DRAGGING_OVER_CLASSNAME,
                            s.isDropAnimating && ANIMATING_CLASSNAME,
                        )}
                    >
                        <Suspense fallback={<SimpleDragHandle />}>{ordered[r.source.index].component}</Suspense>
                    </StyledDraggableItem>
                </DragHandlerContext.Provider>
            </div>
        ),
        [ordered],
    );

    const renderDroppable: DroppableProps["children"] = useCallback(
        (p: DroppableProvided, s: DroppableStateSnapshot) => (
            <DroppableContainer
                ref={p.innerRef}
                className={cx(
                    DROPPABLE_CLASSNAME,
                    s.isDraggingOver && DRAGGING_OVER_CLASSNAME,
                    s.draggingFromThisWith && DRAGGING_FROM_CLASSNAME,
                    className,
                )}
            >
                <DraggableList {...p.droppableProps} className={DRAGGABLE_LIST_CLASSNAME}>
                    {ordered.map(({ id, isHidden }, index) => {
                        if (isHidden) {
                            return null;
                        }
                        return (
                            <Draggable key={id} draggableId={id} index={index}>
                                {renderDraggable}
                            </Draggable>
                        );
                    })}
                    {p.placeholder}
                </DraggableList>
            </DroppableContainer>
        ),
        [className, ordered, renderDraggable],
    );

    return (
        <Droppable droppableId={side} type={TOOLBAR_DRAGGABLE_TYPE} renderClone={renderDraggable}>
            {renderDroppable}
        </Droppable>
    );
}

const DraggableList = styled("div")({
    display: "flex",
    flexDirection: "column",
    position: "relative",
    transition: "all 0.3s",
});
