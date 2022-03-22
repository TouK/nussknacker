import { Checkbox, CheckboxProps, ListItem, ListItemButton, ListItemIcon, ListItemText } from "@mui/material";
import React from "react";

interface FilterItemProps extends Omit<CheckboxProps, "onChange"> {
    label: string | React.ReactElement;
    onChange: (checked: boolean) => void;
    invert?: boolean;
}

export function FilterListItem({ label, checked, onChange, invert, ...props }: FilterItemProps): JSX.Element {
    return (
        <ListItem disablePadding>
            <ListItemButton role={undefined} onClick={(e) => onChange(!checked)} dense>
                <ListItemIcon>
                    <Checkbox edge="start" checked={invert ? !checked : checked} tabIndex={-1} disableRipple {...props} />
                </ListItemIcon>
                <ListItemText primary={label} />
            </ListItemButton>
        </ListItem>
    );
}
