import { css, cx } from "@emotion/css";
import React, { PropsWithChildren, useCallback, useRef } from "react";
import { InputProps, ThemedInput } from "./ThemedInput";
import { ClearIcon } from "../table/SearchFilter";
import { useTheme } from "@mui/material";

type Props = PropsWithChildren<InputProps> & {
    onClear?: () => void;
    onAddonClick?: () => void;
};

export function InputWithIcon({ children, onAddonClick, onClear, ...props }: Props): JSX.Element {
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
    });

    const ref = useRef<HTMLInputElement>();
    const focus = useCallback(() => ref.current.focus(), [ref]);

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
}
