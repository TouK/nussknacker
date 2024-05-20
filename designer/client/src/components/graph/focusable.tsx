import { debounce } from "lodash";
import React, { forwardRef, MouseEventHandler, SyntheticEvent, useCallback, useMemo } from "react";
import { useSizeWithRef } from "../../containers/hooks/useSize";
import { GraphStyled } from "./GraphStyled";

interface ContainerProps extends React.DetailedHTMLProps<React.HTMLAttributes<HTMLDivElement>, HTMLDivElement> {
    onResize?: (current: SyntheticEvent<HTMLDivElement>) => void;
}

export const GraphPaperContainer = forwardRef<HTMLDivElement, ContainerProps>(function GraphPaperContainer(
    { onClick, className, onResize, ...props },
    forwardedRef,
) {
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

    return <GraphStyled className={className} ref={onResize ? observe : forwardedRef} tabIndex={-1} onClick={clickHandler} {...props} />;
});
