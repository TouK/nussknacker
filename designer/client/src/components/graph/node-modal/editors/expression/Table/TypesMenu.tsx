import { PopoverPosition } from "@mui/material/Popover/Popover";
import React, { useMemo } from "react";
import { Menu, MenuItem } from "@mui/material";

interface TypesMenuParams {
    anchorPosition: PopoverPosition | null;
    currentValue: string;
    onChange: (value?: string) => void;
    options: {
        label: string;
        value: string;
    }[];
}

export function TypesMenu({ anchorPosition, onChange, options, currentValue }: TypesMenuParams) {
    const open = useMemo(() => Boolean(anchorPosition), [anchorPosition]);
    return (
        <Menu
            anchorReference="anchorPosition"
            anchorPosition={anchorPosition}
            open={open}
            onClose={() => onChange()}
            transformOrigin={{
                vertical: "top",
                horizontal: "right",
            }}
            autoFocus
        >
            {options?.map(({ value, label }) => (
                <MenuItem
                    value={value}
                    key={value}
                    onClick={() => onChange(value)}
                    selected={currentValue === value}
                    autoFocus={currentValue === value}
                >
                    {label}
                </MenuItem>
            ))}
        </Menu>
    );
}
