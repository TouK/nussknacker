import { ToolbarsSide } from "../../reducers/toolbars";
import {
    Draggable,
    DraggableChildrenFn,
    DraggableLocation,
    DraggableProvided,
    DraggableRubric,
    DraggableStateSnapshot,
    Droppable,
    DroppableProvided,
    DroppableStateSnapshot,
} from "react-beautiful-dnd";
import React, { CSSProperties, useCallback, useMemo } from "react";
import { useSelector } from "react-redux";
import { TOOLBAR_DRAGGABLE_TYPE } from "./ToolbarsLayer";
import { DragHandlerContext } from "../common/dndItems/DragHandle";
import { getOrderForPosition } from "../../reducers/selectors/toolbars";
import { Toolbar } from "./toolbar";
import { cx } from "@emotion/css";
import { css, styled } from "@mui/material";
import { alpha } from "../../containers/theme/helpers";

export const StyledDraggableItem = styled("div")(
    ({ theme }) => css`
        .is-dragging-over {
            opacity: 1;
            ::after {
                transition: all 0.3s;
                content: "";
                line-height: 0;
                position: absolute;
                top: 0;
                left: 0;
                right: 0;
                bottom: 0;
                backdrop-filter: none;
                background: ${alpha(theme.custom.colors.lime, 0.05)};
                outline: 2px solid ${theme.custom.colors.lime};
                outline-offset: -2.5px;
            }
        }

        .is-dragging {
            backdrop-filter: blur(8px);
            transition: all 0.3s;
            opacity: 0.75;
            ::after {
                transition: all 0.3s;
                content: "";
                line-height: 0;
                position: absolute;
                top: 0;
                left: 0;
                right: 0;
                bottom: 0;
                backdrop-filter: blur(0.5px);
                outline: 2px dashed ${theme.custom.colors.orangered};
                background: ${alpha(theme.custom.colors.orangered, 0.2)};
                outline-offset: -3px;
            }
        }

        .is-animating {
            .content {
                opacity: 1;
            }
        }
    `,
);

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

export function ToolbarsContainer(props: Props): JSX.Element {
    const { side, availableToolbars, className } = props;
    const selector = useMemo(() => getOrderForPosition(side), [side]);
    const order = useSelector(selector);

    const ordered = availableToolbars.filter(({ id }) => order.includes(id)).sort(sortByIdsFrom(order));

    const renderDraggable: DraggableChildrenFn = useCallback(
        (p: DraggableProvided, s: DraggableStateSnapshot, r: Rubric) => (
            <div ref={p.innerRef} {...p.draggableProps} style={getStyle(p.draggableProps.style, s)} className={"draggable"}>
                <DragHandlerContext.Provider value={p.dragHandleProps}>
                    <StyledDraggableItem>
                        <div
                            className={cx(
                                s.draggingOver ? "is-dragging-over" : s.isDragging && "is-dragging",
                                s.isDropAnimating && "is-animating",
                                r.source.index === 0 && "first",
                                r.source.index === ordered.length - 1 && "last",
                            )}
                        >
                            {ordered[r.source.index].component}
                        </div>
                    </StyledDraggableItem>
                </DragHandlerContext.Provider>
            </div>
        ),
        [ordered],
    );

    const renderDroppable = useCallback(
        (p: DroppableProvided, s: DroppableStateSnapshot) => (
            <div
                ref={p.innerRef}
                className={cx("droppable", s.isDraggingOver && "is-dragging-over", s.draggingFromThisWith && "is-dragging-from", className)}
            >
                <div {...p.droppableProps} className={"draggable-list"}>
                    <div className={"background"}>
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
                    </div>
                </div>
            </div>
        ),
        [className, ordered, renderDraggable],
    );

    return (
        <Droppable droppableId={side} type={TOOLBAR_DRAGGABLE_TYPE} renderClone={renderDraggable}>
            {renderDroppable}
        </Droppable>
    );
}
