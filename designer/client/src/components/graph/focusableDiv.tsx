import type { MouseEventHandler } from "react";
import React, { forwardRef, useCallback } from "react";

import type { StyledDivProps } from "./styledDiv";
import { StyledDiv } from "./styledDiv";

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
