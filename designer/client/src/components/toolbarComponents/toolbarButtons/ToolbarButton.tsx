import React, { useContext } from "react";
import Dropzone from "react-dropzone";
import { NodeInput } from "../../withFocus";
import { ButtonsVariant, ToolbarButtonProps, ToolbarButtonsContext } from "./index";
import { css, cx } from "@emotion/css";
import { variables } from "../../../stylesheets/variables";
import { Icon } from "./ToolbarButtonStyled";
import { Typography, useTheme } from "@mui/material";

const { buttonSize, rightPanelButtonFontSize, buttonSmallSize } = variables;

export const ToolbarButton = React.forwardRef<HTMLDivElement & HTMLButtonElement, ToolbarButtonProps>(function ToolbarButton(
    { onDrop, title, className, disabled, name, icon, hasError, isActive, ...props },
    ref,
) {
    const { variant } = useContext(ToolbarButtonsContext);
    const {
        palette,
        custom: { colors },
    } = useTheme();

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

        borderColor: hasError ? colors.error : "transparent",

        color: hasError ? colors.error : isActive ? colors.ok : colors.secondaryColor,

        backgroundColor: palette.background.paper,
        ":hover": {
            backgroundColor: disabled ? "inherit" : palette.action.hover,
        },
    });

    const buttonProps = {
        ...props,
        title: title || name,
        className: cx(styles, className),
        children: (
            <>
                <Icon title={title}>{icon}</Icon>
                <Typography variant={"caption"} display={variant === ButtonsVariant.small ? "none" : "unset"}>
                    {name}
                </Typography>
            </>
        ),
    };

    if (!disabled && onDrop) {
        return (
            <Dropzone onDrop={onDrop}>
                {({ getRootProps, getInputProps }) => (
                    <>
                        <div {...getRootProps(buttonProps)} />
                        <NodeInput {...getInputProps()} />
                    </>
                )}
            </Dropzone>
        );
    }

    return <button ref={ref} type="button" {...buttonProps} disabled={disabled} />;
});
