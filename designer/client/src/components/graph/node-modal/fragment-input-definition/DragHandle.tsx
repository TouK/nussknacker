import { css, cx } from "@emotion/css";
import React, { useCallback, useEffect, useState } from "react";
import { DraggableProvidedDragHandleProps } from "react-beautiful-dnd";
import Handlebars from "../../../../assets/img/handlebars.svg";
import { useTheme } from "@mui/material";

const grabbing = css({ "*": { cursor: "grabbing !important" } });

export const DragHandle: React.FC<{ active?: boolean; provided: DraggableProvidedDragHandleProps }> = ({
    active,
    provided,
}): JSX.Element => {
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
        <div
            {...provided}
            className={css({
                outline: "none",
                ":focus": {
                    filter: `drop-shadow(0px 0px 3px ${theme.custom.colors.accent})`,
                },
            })}
        >
            <Handlebars
                onMouseDown={onMouseDown}
                onMouseUp={onMouseUp}
                className={cx(
                    "handle-bars",
                    css({
                        g: { fill: isActive ? theme.custom.colors.accent : theme.custom.colors.primary },
                    }),
                )}
            />
        </div>
    );
};
