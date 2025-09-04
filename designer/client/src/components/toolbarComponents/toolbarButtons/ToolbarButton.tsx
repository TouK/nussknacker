import type { PropsOf } from "@emotion/react/dist/emotion-react.cjs";
import { ArrowDropDown } from "@mui/icons-material";
import { alpha, decomposeColor, Menu, MenuItem, styled } from "@mui/material";
import type { PopoverPosition } from "@mui/material/Popover/Popover";
import { blend } from "@mui/system/colorManipulator";
import React, { forwardRef, useContext } from "react";
import Dropzone from "react-dropzone";

import type { Option } from "../../graph/node-modal/fragment-input-definition/TypeSelect";
import { Button } from "./Button";
import { ButtonProgress } from "./ButtonProgress";
import { ButtonRoot } from "./ButtonRoot";
import { ButtonsVariant, ToolbarButtonsContext } from "./ToolbarButtons";
import type { ToolbarButtonProps } from "./types";

type ToolbarButtonMenuWrapperProps<T = Option> = {
    options: T[];
    selected: T;
    onChange: (value: T) => void;
    className?: string;
    buttonProps?: PropsOf<typeof ToolbarButton>;
};

const ExpandButton = styled(Button)(({ theme }) => {
    return {
        position: "absolute",
        top: 0,
        right: 0,
        bottom: 0,
        color: "inherit",
        ":hover &": {
            backgroundColor: theme.palette.background.paper,
        },
    };
});

const ButtonMenu = forwardRef<HTMLButtonElement, ToolbarButtonMenuWrapperProps>(function ButtonMenu(
    { options = [], selected, onChange, className, buttonProps },
    ref,
) {
    const [anchorPosition, setAnchorPosition] = React.useState<null | PopoverPosition>(null);
    const { variant } = useContext(ToolbarButtonsContext);

    if (options.length < 1) {
        return <ToolbarButton {...buttonProps} ref={ref} className={className} />;
    }

    return (
        <ToolbarButton
            {...buttonProps}
            ref={ref}
            className={className}
            sx={{
                "&:has(.toolbarButton-MenuExpand:hover)": {
                    ".toolbarButton-Root": {
                        filter: "brightness(.8)",
                    },
                },
                "&:has(.toolbarButton-Root:hover)": {
                    ".toolbarButton-MenuExpand": {
                        filter: "brightness(.8)",
                    },
                },
                ".toolbarButton-Root": {
                    paddingRight: [ButtonsVariant.menu, ButtonsVariant.horizontal].includes(variant)
                        ? 3.5
                        : ButtonsVariant.xs === variant
                        ? 2.5
                        : null,
                },
            }}
        >
            <ExpandButton
                sx={{
                    height: ButtonsVariant.small === variant ? 20 : ButtonsVariant.label !== variant ? "auto" : null,
                    width: [ButtonsVariant.menu, ButtonsVariant.horizontal].includes(variant)
                        ? 26
                        : [ButtonsVariant.small, ButtonsVariant.xs].includes(variant)
                        ? 20
                        : null,
                    justifyContent: "center",
                    zoom: variant === ButtonsVariant.xs ? 0.75 : null,
                    borderTopLeftRadius: 0,
                    borderBottomLeftRadius: [ButtonsVariant.menu, ButtonsVariant.horizontal, ButtonsVariant.xs].includes(variant)
                        ? 0
                        : null,
                    borderBottomRightRadius: [ButtonsVariant.menu, ButtonsVariant.horizontal, ButtonsVariant.xs].includes(variant)
                        ? null
                        : 0,
                }}
                className={"toolbarButton-MenuExpand"}
                disabled={buttonProps.disabled}
                onClick={(e) => {
                    e.stopPropagation();
                    setAnchorPosition({ top: e.clientY, left: e.clientX });
                }}
            >
                <ArrowDropDown />
            </ExpandButton>
            <Menu
                anchorReference="anchorPosition"
                anchorPosition={anchorPosition}
                open={Boolean(anchorPosition)}
                onClose={(e: Event) => {
                    e.stopPropagation();
                    setAnchorPosition(null);
                }}
            >
                {options.map((option) => (
                    <MenuItem
                        key={option.value}
                        selected={option.value === selected?.value}
                        disabled={option.isDisabled}
                        onClick={(e) => {
                            e.stopPropagation();
                            onChange(option);
                            setAnchorPosition(null);
                        }}
                    >
                        {option.label}
                    </MenuItem>
                ))}
            </Menu>
            {buttonProps.children}
        </ToolbarButton>
    );
});

const ToolbarButtonComponent = forwardRef<HTMLButtonElement, ToolbarButtonProps>(function _ToolbarButtonComponent(props, ref) {
    if ("isLoading" in props) {
        const { isLoading, loadingProgress, loadingVariant, children, disabled, ...passProps } = props;
        return (
            <ToolbarButtonComponent {...passProps} ref={ref} disabled={disabled}>
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
