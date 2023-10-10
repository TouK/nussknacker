import { cx } from "@emotion/css";
import { useTheme } from "@mui/material";
import { debounce } from "lodash";
import React, { forwardRef, MouseEventHandler, useCallback, useMemo } from "react";
import { useSizeWithRef } from "../../containers/hooks/useSize";
import { graphTheme } from "./GraphTheme";
import { styledGraphWrapper } from "../../stylesheets/styledGraphWrapper";

interface ContainerProps extends React.DetailedHTMLProps<React.HTMLAttributes<HTMLDivElement>, HTMLDivElement> {
    onResize?: (current: DOMRectReadOnly) => void;
}

export const GraphPaperContainer = forwardRef<HTMLDivElement, ContainerProps>(function GraphPaperContainer(
    { onClick, className, onResize, ...props },
    forwardedRef,
) {
    const theme = useTheme();
    const clickHandler: MouseEventHandler<HTMLDivElement> = useCallback(
        (event) => {
            event.currentTarget?.focus();
            onClick?.(event);
        },
        [onClick],
    );

    const options = useMemo(
        () => ({
            onResize: debounce(({ entry }) => {
                onResize?.(entry.contentRect);
            }, 100),
        }),
        [onResize],
    );

    const { observe } = useSizeWithRef(forwardedRef, options);

    return (
        <div className={cx(styledGraphWrapper(theme))}>
            <div
                className={cx(graphTheme(theme), className)}
                ref={onResize ? observe : forwardedRef}
                tabIndex={-1}
                onClick={clickHandler}
                {...props}
            />
        </div>
    );
});
