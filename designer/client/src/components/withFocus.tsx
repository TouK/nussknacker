import { cx } from "@emotion/css";
import React, {
    ButtonHTMLAttributes,
    DetailedHTMLProps,
    forwardRef,
    HTMLAttributes,
    InputHTMLAttributes,
    SelectHTMLAttributes,
    TextareaHTMLAttributes,
} from "react";
import { styled } from "@mui/material";
import { NodeInputCss } from "./NodeInput";
import { useFocus } from "../containers/theme/helpers";

export type InputWithFocusProps = DetailedHTMLProps<InputHTMLAttributes<HTMLInputElement>, HTMLInputElement>;

export const NodeInput = styled("input")(({ theme }) => `${NodeInputCss(theme).styles}`);

export type TextAreaWithFocusProps = DetailedHTMLProps<TextareaHTMLAttributes<HTMLTextAreaElement>, HTMLTextAreaElement>;

export function TextArea({ className, ...props }: TextAreaWithFocusProps): JSX.Element {
    return <textarea {...props} className={cx(className)} />;
}

export const TextAreaNodeWithFocus = styled(TextArea)(({ theme }) => `${NodeInputCss(theme).styles}`);

export type ButtonProps = DetailedHTMLProps<ButtonHTMLAttributes<HTMLButtonElement>, HTMLButtonElement>;

export function ButtonWithFocus({ className, onClick, ...props }: ButtonProps): JSX.Element {
    const withFocus = useFocus();

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

export function SelectWithFocus({
    className,
    ...props
}: DetailedHTMLProps<SelectHTMLAttributes<HTMLSelectElement>, HTMLSelectElement>): JSX.Element {
    const withFocus = useFocus();

    return <select {...props} className={cx(withFocus, className)} />;
}

export const SelectNodeWithFocus = styled(SelectWithFocus)(
    ({ theme }) => `
    ${NodeInputCss(theme).styles}
`,
);

export const FocusOutline = forwardRef(function FocusOutline(
    { className, ...props }: DetailedHTMLProps<HTMLAttributes<HTMLDivElement>, HTMLDivElement>,
    ref: React.Ref<HTMLDivElement>,
): JSX.Element {
    const withFocus = useFocus();

    return <div ref={ref} {...props} className={cx(withFocus, className)} />;
});
