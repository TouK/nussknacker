import {
    Checkbox,
    CheckboxProps,
    FormControlLabel,
    FormGroup,
    ListItemIcon,
    ListItemText,
    MenuItem,
    Switch,
    Typography,
} from "@mui/material";
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
        <MenuItem selected={touched} onClick={() => onChange(!checked)} dense sx={{ minWidth: 175 }}>
            <ListItemIcon>
                <Checkbox sx={{ padding: 0 }} checked={invert ? !checked : checked} disableRipple {...passProps} />
            </ListItemIcon>
            <ListItemText primary={label} />
        </MenuItem>
    );
}

export function FilterListItemSwitch(props: FilterItemProps): JSX.Element {
    const { label, touched = props.checked, checked, onChange } = props;
    return (
        <MenuItem selected={touched} onClick={() => onChange(!checked)} dense sx={{ minWidth: 175 }}>
            <FormGroup>
                <FormControlLabel
                    control={
                        <Switch size="small" color="primary" checked={checked} disableRipple sx={{ marginLeft: 0.5, marginRight: 0.5 }} />
                    }
                    label={
                        <Typography onClick={(event) => event.stopPropagation()} variant="body2" color="textPrimary">
                            {label}
                        </Typography>
                    }
                />
            </FormGroup>
        </MenuItem>
    );
}
