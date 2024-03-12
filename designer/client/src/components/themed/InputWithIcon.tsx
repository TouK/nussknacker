import { css, cx } from "@emotion/css";
import React, { forwardRef, PropsWithChildren, ReactElement, useCallback, useImperativeHandle, useRef } from "react";
import { InputProps, ThemedInput } from "./ThemedInput";
import { ClearIcon } from "../table/SearchFilter";
import { useTheme } from "@mui/material";

type Props = PropsWithChildren<InputProps> & {
    onClear?: () => void;
    onAddonClick?: () => void;
};

export type Focusable = {
    focus: () => void;
};

export const InputWithIcon = forwardRef<Focusable, Props>(function InputWithIcon(
    { children, onAddonClick, onClear, ...props },
    forwardedRef,
): ReactElement {
    const theme = useTheme();

    const size = theme.custom.spacing.controlHeight;

    const wrapperWithAddonStyles = css({
        position: "relative",
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
        svg: {
            boxShadow: `0 0 ${size / 4}px ${size / 8}px ${theme.custom.colors.secondaryBackground}, 0 0 ${size / 2}px ${size / 2}px ${
                theme.custom.colors.secondaryBackground
            } inset`,
        },
    });

    const ref = useRef<HTMLInputElement>();
    const focus = useCallback(() => {
        const input = ref.current;
        input.focus();
        input.setSelectionRange(0, props.value.length);
    }, [props.value.length]);

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
                    <div className={addonStyles} onClick={onAddonClick ?? focus}>
                        {children}
                    </div>
                )}
            </div>
        </div>
    );
});
