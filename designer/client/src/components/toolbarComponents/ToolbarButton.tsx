import React, { ReactEventHandler, useContext } from "react";
import Dropzone, { DropEvent } from "react-dropzone";
import { InputWithFocus } from "../withFocus";
import styles from "../../stylesheets/_variables.styl";
import { ButtonsVariant, ToolbarButtonsContext } from "./ToolbarButtons";
import { css, cx } from "@emotion/css";
import { styled } from "@mui/material";

// TODO: get rid of stylus
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
} = styles;

export interface ToolbarButtonProps {
    name: string;
    icon: React.JSX.Element | string;
    className?: string;
    disabled?: boolean;
    title?: string;
    onDrop?: <T extends File>(acceptedFiles: T[], rejectedFiles: T[], event: DropEvent) => void;
    onMouseOver?: ReactEventHandler;
    onMouseOut?: ReactEventHandler;
    onClick?: ReactEventHandler;
    hasError?: boolean;
    isActive?: boolean;
}

const Icon = styled("div")({
    flex: 1,
    lineHeight: 0,
    display: "flex",
    flexDirection: "column",
    width: parseFloat(buttonSize) / 2,
});

const Label = styled("div")<{
    variant: ButtonsVariant;
}>(({ variant }) => ({
    display: variant === ButtonsVariant.small ? "none" : "unset",
}));

function ToolbarButton({ onDrop, title, className, disabled, name, icon, hasError, isActive, ...props }: ToolbarButtonProps) {
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

export default ToolbarButton;
