import { alpha, decomposeColor, styled } from "@mui/material";
import { blend } from "@mui/system/colorManipulator";
import React, { forwardRef } from "react";
import Dropzone from "react-dropzone";

import { ButtonProgress } from "../../toolbars/test/buttons/ButtonProgress";
import { ButtonMenu } from "./ButtonMenu";
import { ButtonRoot } from "./ButtonRoot";
import type { ToolbarButtonProps } from "./index";

const ToolbarButtonComponent = forwardRef<HTMLButtonElement, ToolbarButtonProps>(function _ToolbarButtonComponent(props, ref) {
    if ("isLoading" in props) {
        const { isLoading, loadingProgress, loadingVariant, children, disabled, ...passProps } = props;
        return (
            <ToolbarButtonComponent {...passProps} ref={ref} disabled={disabled || isLoading}>
                <ButtonProgress enabled={isLoading} variant={loadingVariant} value={loadingProgress} />
                {children}
            </ToolbarButtonComponent>
        );
    }

    if ("presets" in props) {
        const { presets, selected, onPresetChange, className, ...passProps } = props;
        return (
            <ButtonMenu
                ref={ref}
                options={presets}
                selected={selected}
                onChange={onPresetChange}
                className={className}
                buttonProps={passProps}
            />
        );
    }

    if ("onDrop" in props) {
        const { onDrop, ...passProps } = props;
        return (
            <Dropzone disabled={passProps.disabled} onDrop={onDrop}>
                {({ getRootProps, getInputProps, isDragActive }) => (
                    <ButtonRoot {...passProps} {...getRootProps(passProps)} ref={ref} isActive={isDragActive}>
                        <input {...getInputProps()} />
                    </ButtonRoot>
                )}
            </Dropzone>
        );
    }

    return <ButtonRoot {...props} ref={ref} />;
});
function splitAlpha(base: string) {
    const colorObject = decomposeColor(base);
    return {
        alpha: colorObject.values[3] || 1,
        color: alpha(base, 1),
    };
}

export const ToolbarButton = styled(ToolbarButtonComponent)(({ theme }) => {
    const normal = theme.palette.background.paper;
    const base = splitAlpha(theme.palette.action.hover);
    const highlight = blend(normal, base.color, base.alpha);
    return {
        color: alpha(theme.palette.getContrastText(normal), 0.75),
        "&:hover": {
            color: alpha(theme.palette.getContrastText(highlight), 0.75),
            ".toolbarButton-Root, .toolbarButton-MenuExpand": {
                backgroundColor: highlight,
            },
        },
    };
});
