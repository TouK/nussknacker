import React, { forwardRef, useMemo } from "react";
import { debounce } from "lodash";
import { useSizeWithRef } from "../../containers/hooks/useSize";
import { StyledDiv, StyledDivProps } from "./styledDiv";

export type ResizeObserverProps = StyledDivProps & {
    onResize?: (current: DOMRectReadOnly) => void;
};

export const ResizeObserver = forwardRef<HTMLDivElement, ResizeObserverProps>(function ResizeObserver(
    { onResize, ...props }: ResizeObserverProps,
    forwardedRef,
) {
    const options = useMemo(
        () => ({
            onResize: debounce(({ entry }) => {
                onResize?.(entry.contentRect);
            }, 100),
        }),
        [onResize],
    );

    const { observe } = useSizeWithRef(forwardedRef, options);

    return <StyledDiv {...props} ref={onResize ? observe : forwardedRef} />;
});
