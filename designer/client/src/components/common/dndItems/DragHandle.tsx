import { css, cx } from "@emotion/css";
import React, { createContext, PropsWithChildren, useCallback, useContext, useEffect, useState } from "react";
import { DraggableProvidedDragHandleProps } from "@hello-pangea/dnd";
import Handlebars from "./handlebars.svg";
import { styled, useTheme } from "@mui/material";

const StyledHandleBars = styled(Handlebars)`
    height: 35px;
    width: 12px;
    margin-left: 6px;
    cursor: grab;
`;

const grabbing = css({ "*": { cursor: "grabbing !important" } });

export const DragHandlerContext = createContext<DraggableProvidedDragHandleProps>(null);

export function useDragHandler() {
    const handleProps = useContext(DragHandlerContext);
    if (!handleProps) {
        // eslint-disable-next-line i18next/no-literal-string
        throw new Error("used outside DragHandlerContext.Provider");
    }
    return handleProps;
}

export function SimpleDragHandle({
    children,
    className,
}: PropsWithChildren<{
    className?: string;
}>) {
    const handleProps = useDragHandler();

    return (
        <div {...handleProps} className={className}>
            {children}
        </div>
    );
}

export const DragHandle: React.FC<{ active?: boolean }> = ({ active }): React.JSX.Element => {
    const [isActive, setActive] = useState(false);
    const theme = useTheme();

    useEffect(() => {
        setActive(active);
    }, [active]);

    useEffect(() => {
        document.body.classList.toggle(grabbing, isActive);
    }, [isActive]);

    const onMouseDown = useCallback(() => setActive(true), []);
    const onMouseUp = useCallback(() => setActive(false), []);

    return (
        <SimpleDragHandle
            className={css({
                outline: "none",
                ":focus": {
                    filter: `drop-shadow(0px 0px 3px ${theme.palette.primary.main})`,
                },
            })}
        >
            <StyledHandleBars
                onMouseDown={onMouseDown}
                onMouseUp={onMouseUp}
                className={cx(
                    css({
                        g: { fill: isActive ? theme.palette.primary.main : theme.palette.text.secondary },
                    }),
                )}
            />
        </SimpleDragHandle>
    );
};
