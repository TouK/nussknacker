import { cx } from "@emotion/css";
import React, { ButtonHTMLAttributes, DetailedHTMLProps, InputHTMLAttributes, TextareaHTMLAttributes } from "react";
import { styled } from "@mui/material";
import { nodeInputCss } from "./NodeInput";
import { nodeInput } from "./graph/node-modal/NodeDetailsContent/NodeTableStyled";

export type InputWithFocusProps = DetailedHTMLProps<InputHTMLAttributes<HTMLInputElement>, HTMLInputElement>;

export const NodeInput = styled("input")``;

export type TextAreaWithFocusProps = DetailedHTMLProps<TextareaHTMLAttributes<HTMLTextAreaElement>, HTMLTextAreaElement>;

export function TextArea({ className, ...props }: TextAreaWithFocusProps): JSX.Element {
    return <textarea {...props} className={cx(className, nodeInput)} />;
}

export const TextAreaNode = styled(TextArea)(nodeInputCss);

export type ButtonProps = DetailedHTMLProps<ButtonHTMLAttributes<HTMLButtonElement>, HTMLButtonElement>;

export function Button({ className, onClick, ...props }: ButtonProps): JSX.Element {
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

export const SelectNode = styled("select")(nodeInputCss);
