import { Checkbox, CheckboxProps, ListItemIcon, ListItemText, MenuItem } from "@mui/material";
import React from "react";

interface FilterItemProps extends Omit<CheckboxProps, "onChange"> {
    label: string | React.ReactElement;
    onChange: (checked: boolean) => void;
    invert?: boolean;
    touched?: boolean;
}

export function FilterListItem(props: FilterItemProps): JSX.Element {
    const { label, touched = props.checked, checked, onChange, invert, ...passProps } = props;
    return (
        <MenuItem selected={touched} onClick={() => onChange(!checked)} dense>
            <ListItemIcon>
                <Checkbox sx={{ padding: 0 }} checked={invert ? !checked : checked} disableRipple {...passProps} />
            </ListItemIcon>
            <ListItemText primary={label} />
        </MenuItem>
    );
}
