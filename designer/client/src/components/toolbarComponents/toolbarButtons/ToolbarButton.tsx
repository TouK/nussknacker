import { alpha, decomposeColor, styled } from "@mui/material";
import { blend } from "@mui/system/colorManipulator";
import React from "react";
import Dropzone from "react-dropzone";

import { ButtonProgress } from "../../toolbars/test/buttons/ButtonProgress";
import { ButtonMenu } from "./ButtonMenu";
import { ButtonRoot } from "./ButtonRoot";
import type { ToolbarButtonProps } from "./index";

function ToolbarButtonComponent(props: ToolbarButtonProps) {
    if ("isLoading" in props) {
        const { isLoading, loadingProgress, loadingVariant, children, ...passProps } = props;
        return (
            <ToolbarButtonComponent {...passProps}>
                <ButtonProgress enabled={isLoading} variant={loadingVariant} value={loadingProgress} />
                {children}
            </ToolbarButtonComponent>
        );
    }

    if ("presets" in props) {
        const { presets, selected, onPresetChange, className, ...passProps } = props;
        return <ButtonMenu options={presets} selected={selected} onChange={onPresetChange} className={className} buttonProps={passProps} />;
    }

    if ("onDrop" in props) {
        const { onDrop, ...passProps } = props;
        return (
            <Dropzone disabled={passProps.disabled} onDrop={onDrop}>
                {({ getRootProps, getInputProps, isDragActive }) => (
                    <ButtonRoot {...passProps} {...getRootProps(passProps)} isActive={isDragActive}>
                        <input {...getInputProps()} />
                    </ButtonRoot>
                )}
            </Dropzone>
        );
    }

    return <ButtonRoot {...props} />;
}

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
        ".toolbarButton-Root": {
            backgroundColor: normal,
        },
        "&:hover": {
            color: alpha(theme.palette.getContrastText(highlight), 0.75),
            ".toolbarButton-Root, .toolbarButton-MenuExpand": {
                backgroundColor: highlight,
            },
        },
    };
});
