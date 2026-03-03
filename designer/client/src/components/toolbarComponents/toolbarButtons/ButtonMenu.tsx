import type { PropsOf } from "@emotion/react";
import { ArrowDropDown } from "@mui/icons-material";
import { ListItemIcon, ListItemText, Menu, MenuItem, styled, Typography } from "@mui/material";
import type { PopoverPosition } from "@mui/material/Popover/Popover";
import React, { forwardRef, useContext } from "react";

import type { Option, OptionHeader } from "../../graph/node-modal/fragment-input-definition/TypeSelect";
import { isOptionHeader } from "../../graph/node-modal/fragment-input-definition/TypeSelect";
import { Button } from "./Button";
import { ToolbarButton } from "./ToolbarButton";
import { ButtonsVariant, ToolbarButtonsContext } from "./ToolbarButtons";

type ToolbarButtonMenuWrapperProps<T = Option> = {
    options: Array<T | OptionHeader>;
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

export const ButtonMenu = forwardRef<HTMLButtonElement, ToolbarButtonMenuWrapperProps>(function ButtonMenu(
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
                    "@media (any-pointer: fine)": {
                        zoom: variant === ButtonsVariant.xs ? 0.75 : null,
                    },
                    borderTopLeftRadius: 0,
                    borderBottomLeftRadius: [ButtonsVariant.menu, ButtonsVariant.horizontal, ButtonsVariant.xs].includes(variant)
                        ? 0
                        : null,
                    borderBottomRightRadius: [ButtonsVariant.menu, ButtonsVariant.horizontal, ButtonsVariant.xs].includes(variant)
                        ? null
                        : 0,
                }}
                className={"toolbarButton-MenuExpand"}
                disabled={buttonProps?.disabled}
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
                    <MenuOption
                        key={isOptionHeader(option) ? option.header : option.value}
                        option={option}
                        selected={selected}
                        onChange={(value) => {
                            onChange(value);
                            setAnchorPosition(null);
                        }}
                    />
                ))}
            </Menu>
            {buttonProps?.children}
        </ToolbarButton>
    );
});

function MenuOption<T extends Option>({
    option,
    selected,
    onChange,
}: {
    option: T | OptionHeader;
    selected: T;
    onChange: (value: T) => void;
}) {
    if (isOptionHeader(option)) {
        return (
            <Typography key={option.header} variant={"subtitle1"} color={"text.secondary"} sx={{ px: 2, py: 1 }}>
                {option.header}
            </Typography>
        );
    }

    return (
        <MenuItem
            selected={option.value === selected?.value}
            disabled={option.isDisabled}
            onClick={(e) => {
                e.stopPropagation();
                onChange(option as T);
            }}
        >
            {option.icon && <ListItemIcon sx={{ "&&": { minWidth: 22 } }}>{option.icon}</ListItemIcon>}
            <ListItemText sx={{ pl: option.icon ? 0 : 0.75 }}>{option.label}</ListItemText>
        </MenuItem>
    );
}
