import { Box, Menu, MenuItem, styled } from "@mui/material";
import React from "react";
import Dropzone from "react-dropzone";

import { ButtonMenu } from "./ButtonMenu";
import { ButtonRoot } from "./ButtonRoot";
import type { ToolbarButtonProps } from "./index";

function ToolbarButtonComponent(props: ToolbarButtonProps) {
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

const StyledToolbarButton = styled(ToolbarButtonComponent)({});
export const ToolbarButton = StyledToolbarButton;
