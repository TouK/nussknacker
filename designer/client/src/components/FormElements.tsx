import { cx } from "@emotion/css";
import { styled } from "@mui/material";
import type { ButtonHTMLAttributes, DetailedHTMLProps, InputHTMLAttributes, TextareaHTMLAttributes } from "react";
import React, { forwardRef } from "react";

import { nodeInput } from "./graph/node-modal/NodeDetailsContent/NodeTableStyled";
import { nodeInputCss } from "./NodeInput";

export type InputWithFocusProps = DetailedHTMLProps<InputHTMLAttributes<HTMLInputElement>, HTMLInputElement>;

export const NodeInput = styled("input")``;

export type TextAreaWithFocusProps = DetailedHTMLProps<TextareaHTMLAttributes<HTMLTextAreaElement>, HTMLTextAreaElement>;

export const TextArea = forwardRef<HTMLTextAreaElement, TextAreaWithFocusProps>(({ className, ...props }, ref) => {
    return <textarea ref={ref} {...props} className={cx(className, nodeInput)} />;
});

TextArea.displayName = "TextArea";

export const TextAreaNode = styled(TextArea)(nodeInputCss);

export type ButtonProps = DetailedHTMLProps<ButtonHTMLAttributes<HTMLButtonElement>, HTMLButtonElement>;

export function Button({ className, onClick, ...props }: ButtonProps): React.JSX.Element {
    return (
        <button
            {...props}
            tabIndex={props.disabled ? -1 : props.tabIndex}
            className={className}
            onClick={(event) => {
                const { currentTarget } = event;
                onClick?.(event);
                setTimeout(() => currentTarget.scrollIntoView({ behavior: "smooth", block: "nearest" }));
            }}
        />
    );
}
