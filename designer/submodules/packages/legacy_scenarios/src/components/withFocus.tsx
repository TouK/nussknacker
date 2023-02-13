import { cx } from "@emotion/css";
import React, { ButtonHTMLAttributes, DetailedHTMLProps, forwardRef, InputHTMLAttributes } from "react";
import { useNkTheme } from "../containers/theme";

export type InputWithFocusProps = DetailedHTMLProps<InputHTMLAttributes<HTMLInputElement>, HTMLInputElement>;
export const InputWithFocus = forwardRef(function InputWithFocus(
    { className, ...props }: InputWithFocusProps,
    ref: React.Ref<HTMLInputElement>,
): JSX.Element {
    const { withFocus } = useNkTheme();
    return <input ref={ref} {...props} className={cx(withFocus, className)} />;
});

export type ButtonProps = DetailedHTMLProps<ButtonHTMLAttributes<HTMLButtonElement>, HTMLButtonElement>;

export function ButtonWithFocus({ className, onClick, ...props }: ButtonProps): JSX.Element {
    const { withFocus } = useNkTheme();
    return (
        <button
            {...props}
            className={cx(withFocus, className)}
            onClick={(event) => {
                const { currentTarget } = event;
                onClick?.(event);
                setTimeout(() => currentTarget.scrollIntoView({ behavior: "smooth", block: "nearest" }));
            }}
        />
    );
}
