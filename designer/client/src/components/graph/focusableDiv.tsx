import React, { forwardRef, MouseEventHandler, useCallback } from "react";
import { StyledDiv, StyledDivProps } from "./styledDiv";

export type FocusableDivProps = StyledDivProps;

export const FocusableDiv = forwardRef<HTMLDivElement, FocusableDivProps>(function FocusableDiv(
    { onClick, ...props }: FocusableDivProps,
    forwardedRef,
) {
    const clickHandler = useCallback<MouseEventHandler<HTMLDivElement>>(
        (event) => {
            event.currentTarget?.focus();
            onClick?.(event);
        },
        [onClick],
    );

    return <StyledDiv tabIndex={-1} {...props} onClick={clickHandler} ref={forwardedRef} />;
});
