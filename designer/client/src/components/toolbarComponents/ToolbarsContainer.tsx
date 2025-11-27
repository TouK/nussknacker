import { cx } from "@emotion/css";
import type {
    DraggableChildrenFn,
    DraggableLocation,
    DraggableProvided,
    DraggableRubric,
    DraggableStateSnapshot,
    DroppableProps,
    DroppableProvided,
    DroppableStateSnapshot,
} from "@hello-pangea/dnd";
import { Draggable, Droppable } from "@hello-pangea/dnd";
import { alpha, styled } from "@mui/material";
import type { CSSProperties, PropsWithChildren } from "react";
import React, { createContext, Suspense, useCallback, useContext, useMemo, useRef } from "react";

import { getOrderForPosition } from "../../reducers/selectors/toolbars";
import { ToolbarsSide } from "../../reducers/toolbars";
import { useAppSelector } from "../../store/storeHelpers";
import { DragHandlerContext, SimpleDragHandle } from "../common/dndItems/DragHandle";
import { DraggableIdContext, TOOLBAR_DRAGGABLE_TYPE } from "./DragAndDropContainer";
import { isInInertTree } from "./inertHelpers";
import type { Toolbar } from "./toolbar";

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
    disableDnd?: boolean;
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
    "&:first-of-type:not(&:last-child)": {
        justifyContent: "flex-start",
    },
    "&:last-child:not(&:first-of-type)": {
        justifyContent: "flex-end",
    },
});

const isHorizontal = (side) =>
    ![
        ToolbarsSide.LeftBottom,
        ToolbarsSide.LeftTop,
        ToolbarsSide.RightBottom,
        ToolbarsSide.RightTop,
        ToolbarsSide.LeftDynamic,
        ToolbarsSide.RightDynamic,
    ].includes(side);

const CloneWrapper = (
    props: PropsWithChildren<{
        snapshot: DraggableStateSnapshot;
        draggable: DraggableProvided;
        toolbar: Toolbar;
    }>,
) => {
    return <div>{props.children}</div>;
};

export const ToolbarSideContext = createContext<ToolbarsSide>(null);

export function ToolbarsContainer(props: Props): React.JSX.Element {
    const { side, availableToolbars, className, disableDnd } = props;

    const selector = useMemo(() => getOrderForPosition(side), [side]);
    const order = useAppSelector(selector);

    const ordered = useMemo(
        () => availableToolbars.filter(({ id }) => order.includes(id)).sort(sortByIdsFrom(order)),
        [availableToolbars, order],
    );

    const renderDraggable: DraggableChildrenFn = useCallback(
        (p: DraggableProvided, s: DraggableStateSnapshot, r: Rubric) => {
            const toolbar = ordered[r.source.index];
            const { component, horizontalComponent } = toolbar;
            const finalElement = isHorizontal(side) ? horizontalComponent : component;
            return (
                <div ref={p.innerRef} {...p.draggableProps} style={getStyle(p.draggableProps.style, s)} className={DRAGGABLE_CLASSNAME}>
                    <DragHandlerContext.Provider value={p.dragHandleProps || false}>
                        <StyledDraggableItem
                            className={cx(
                                s.isDragging && DRAGGING_CLASSNAME,
                                s.draggingOver && DRAGGING_OVER_CLASSNAME,
                                s.isDropAnimating && ANIMATING_CLASSNAME,
                            )}
                            sx={
                                s.isClone
                                    ? {
                                          "&, & *": {
                                              pointerEvents: "none",
                                          },
                                      }
                                    : null
                            }
                        >
                            <Suspense fallback={<SimpleDragHandle />}>
                                {s.isClone ? (
                                    <CloneWrapper draggable={p} snapshot={s} toolbar={toolbar}>
                                        {finalElement}
                                    </CloneWrapper>
                                ) : (
                                    finalElement
                                )}
                            </Suspense>
                        </StyledDraggableItem>
                    </DragHandlerContext.Provider>
                </div>
            );
        },
        [ordered, side],
    );

    const draggedId = useContext(DraggableIdContext);
    const draggedToolbar = useMemo(
        () => (draggedId ? availableToolbars.find(({ id }) => draggedId === id) : null),
        [availableToolbars, draggedId],
    );

    const ref = useRef(null);

    const dropDisabled = useMemo(() => {
        if (disableDnd) return true;
        if (isInInertTree(ref.current)) return true;
        return !(isHorizontal(side) ? draggedToolbar?.horizontalComponent : draggedToolbar?.component);
    }, [disableDnd, draggedToolbar?.component, draggedToolbar?.horizontalComponent, side]);

    const renderDroppable: DroppableProps["children"] = useCallback(
        (p: DroppableProvided, s: DroppableStateSnapshot) => (
            <DroppableContainer
                ref={p.innerRef}
                className={cx(
                    !dropDisabled && DROPPABLE_CLASSNAME,
                    s.isDraggingOver && !dropDisabled && DRAGGING_OVER_CLASSNAME,
                    s.draggingFromThisWith && DRAGGING_FROM_CLASSNAME,
                    className,
                )}
            >
                <DraggableList ref={ref} {...p.droppableProps} className={DRAGGABLE_LIST_CLASSNAME}>
                    {ordered.map(({ id, isHidden }, index) => {
                        if (isHidden) {
                            return null;
                        }
                        return (
                            <Draggable key={id} draggableId={id} index={index} isDragDisabled={disableDnd}>
                                {renderDraggable}
                            </Draggable>
                        );
                    })}
                    {p.placeholder}
                </DraggableList>
            </DroppableContainer>
        ),
        [className, disableDnd, dropDisabled, ordered, renderDraggable],
    );

    return (
        <ToolbarSideContext.Provider value={side}>
            <Droppable
                direction={isHorizontal(side) ? "horizontal" : "vertical"}
                droppableId={side}
                type={TOOLBAR_DRAGGABLE_TYPE}
                renderClone={renderDraggable}
                isDropDisabled={dropDisabled}
            >
                {renderDroppable}
            </Droppable>
        </ToolbarSideContext.Provider>
    );
}

const DraggableList = styled("div")({
    display: "flex",
    flexDirection: "column",
    position: "relative",
    transition: "all 0.3s",
});
