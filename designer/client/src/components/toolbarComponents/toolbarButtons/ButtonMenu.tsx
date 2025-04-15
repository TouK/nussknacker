import type { PropsOf } from "@emotion/react";
import { ArrowDropDown } from "@mui/icons-material";
import { Menu, MenuItem, styled } from "@mui/material";
import React, { useContext } from "react";

import type { Option } from "../../graph/node-modal/fragment-input-definition/TypeSelect";
import { Button } from "./Button";
import { ToolbarButton } from "./ToolbarButton";
import { ButtonsVariant, ToolbarButtonsContext } from "./ToolbarButtons";

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

export const ButtonMenu = function ButtonMenu({ options = [], selected, onChange, className, buttonProps }: ToolbarButtonMenuWrapperProps) {
    const [anchorEl, setAnchorEl] = React.useState<null | HTMLElement>(null);
    const { variant } = useContext(ToolbarButtonsContext);

    if (options.length < 1 || buttonProps.disabled) {
        return <ToolbarButton {...buttonProps} className={className} />;
    }

    return (
        <ToolbarButton
            {...buttonProps}
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
                    paddingRight: ButtonsVariant.horizontal === variant ? 3.5 : ButtonsVariant.xs === variant ? 2.5 : null,
                },
                ".toolbarButton-Label": {
                    display: ButtonsVariant.xs === variant ? "inline" : null,
                },
            }}
        >
            <ExpandButton
                sx={{
                    height: ButtonsVariant.small === variant ? 20 : ButtonsVariant.label !== variant ? "auto" : null,
                    width:
                        ButtonsVariant.horizontal === variant
                            ? 26
                            : [ButtonsVariant.small, ButtonsVariant.xs].includes(variant)
                            ? 20
                            : null,
                    justifyContent: "center",
                    zoom: variant === ButtonsVariant.xs ? 0.75 : null,
                    borderTopLeftRadius: 0,
                    borderBottomLeftRadius: [ButtonsVariant.horizontal, ButtonsVariant.xs].includes(variant) ? 0 : null,
                    borderBottomRightRadius: [ButtonsVariant.horizontal, ButtonsVariant.xs].includes(variant) ? null : 0,
                }}
                className={"toolbarButton-MenuExpand"}
                onClick={(e) => {
                    e.stopPropagation();
                    setAnchorEl(e.currentTarget);
                }}
            >
                <ArrowDropDown />
            </ExpandButton>
            <Menu
                anchorEl={anchorEl}
                open={Boolean(anchorEl)}
                onClose={(e: Event) => {
                    e.stopPropagation();
                    setAnchorEl(null);
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
                            setAnchorEl(null);
                        }}
                    >
                        {option.label}
                    </MenuItem>
                ))}
            </Menu>
            {buttonProps.children}
        </ToolbarButton>
    );
};
