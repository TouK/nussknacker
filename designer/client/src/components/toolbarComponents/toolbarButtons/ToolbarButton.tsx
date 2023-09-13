import React, { useContext } from "react";
import Dropzone from "react-dropzone";
import { InputWithFocus } from "../../withFocus";
import { ButtonsVariant, ToolbarButtonProps, ToolbarButtonsContext } from "./index";
import { css, cx } from "@emotion/css";
import { variables } from "../../../stylesheets/variables";
import { Icon, Label } from "./ToolbarButtonStyled";

const {
    buttonSize,
    rightPanelButtonFontSize,
    buttonTextColor,
    buttonBkgColor,
    buttonBkgHover,
    focusColor,
    errorColor,
    okColor,
    buttonSmallSize,
} = variables;

export function ToolbarButton({ onDrop, title, className, disabled, name, icon, hasError, isActive, ...props }: ToolbarButtonProps) {
    const { variant } = useContext(ToolbarButtonsContext);

    const margin = 2;
    const width = parseFloat(variant === ButtonsVariant.small ? buttonSmallSize : buttonSize) - 2 * margin;
    const styles = css({
        margin,
        padding: variant === ButtonsVariant.small ? 0 : "4px 0",
        borderRadius: 6,
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
        justifyContent: "start",
        border: "3px solid",
        userSelect: "none",
        opacity: disabled ? 0.3 : 1,
        cursor: disabled ? "not-allowed" : "pointer",
        fontSize: rightPanelButtonFontSize,
        width,
        height: width,
        outline: "none",

        borderColor: hasError ? errorColor : "transparent",
        ":focus": {
            borderColor: focusColor,
        },

        color: hasError ? errorColor : isActive ? okColor : buttonTextColor,

        backgroundColor: buttonBkgColor,
        ":hover": {
            backgroundColor: disabled ? buttonBkgColor : buttonBkgHover,
        },
    });

    const buttonProps = {
        ...props,
        title: title || name,
        className: cx(styles, className),
        children: (
            <>
                <Icon title={title}>{icon}</Icon>
                <Label variant={variant}>{name}</Label>
            </>
        ),
    };

    if (!disabled && onDrop) {
        return (
            <Dropzone onDrop={onDrop}>
                {({ getRootProps, getInputProps }) => (
                    <>
                        <div {...getRootProps(buttonProps)} />
                        <InputWithFocus {...getInputProps()} />
                    </>
                )}
            </Dropzone>
        );
    }

    return <button type="button" {...buttonProps} disabled={disabled} />;
}
