import { css, cx } from "@emotion/css";
import { styled, useTheme } from "@mui/material";
import React, { forwardRef, PropsWithChildren, ReactElement, useCallback, useImperativeHandle, useRef } from "react";
import { AdvancedOptionsIcon, ClearIcon } from "../table/SearchFilter";
import { InputProps, ThemedInput } from "./ThemedInput";

type Props = PropsWithChildren<InputProps> & {
    onClear?: () => void;
    onAddonClick?: () => void;
};

export type Focusable = {
    focus: (options?: FocusOptions) => void;
};

export const InputWithAdvancedSearchOptions = forwardRef<Focusable, Props>(function InputWithIcon(
    { children, onAddonClick, onClear, ...props },
    forwardedRef,
): ReactElement {
    const theme = useTheme();

    const size = theme.custom.spacing.controlHeight;

    const wrapperWithAddonStyles = css({
        position: "relative",
        display: "flex",
        flexDirection: "row",
    });

    const addonWrapperStyles = css({
        position: "absolute",
        top: 0,
        right: 0,
        height: size,
        display: "flex",
        padding: size / 4,
    });

    const addonStyles = css({
        display: "flex",
        width: size / 2,
        height: size / 2,
        marginLeft: size / 4,
    });

    const ref = useRef<HTMLInputElement>();
    const focus = useCallback(
        (options?: FocusOptions) => {
            const input = ref.current;
            input.focus({ preventScroll: true });
            input.setSelectionRange(0, props.value.length);
            setTimeout(() => {
                if (options?.preventScroll) return;
                input.scrollIntoView({ behavior: "smooth", block: "center" });
            }, theme.transitions.duration.standard);
        },
        [props.value.length, theme.transitions.duration.standard],
    );

    useImperativeHandle(forwardedRef, () => ({ focus }), [focus]);

    return (
        <div className={cx(children && wrapperWithAddonStyles)}>
            <ThemedInput ref={ref} {...props} />
            <div className={addonWrapperStyles}>
                {!!props.value && onClear && (
                    <div className={addonStyles} onClick={onClear}>
                        <ClearIcon />
                    </div>
                )}
                {children && (
                    <div className={addonStyles} onClick={onAddonClick ?? (() => focus())}>
                        {children}
                    </div>
                )}
            </div>
        </div>
    );
});
