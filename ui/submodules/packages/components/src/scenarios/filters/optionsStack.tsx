import React, { PropsWithChildren } from "react";
import { FilterListOff } from "@mui/icons-material";
import { IconButton, List, ListSubheader, Stack } from "@mui/material";
import { FiltersParams } from "./simpleOptionsStack";

export function SelectFilterTitle(props: FiltersParams<any> & { clearIcon?: React.ReactElement }): JSX.Element {
    const { value, onChange, label, clearIcon } = props;
    const clear = () => onChange?.(null);
    const showClear = value?.length;
    return (
        <ListSubheader>
            <Stack direction="row" alignItems="center" justifyContent="space-between">
                {label}
                {showClear ? (
                    <IconButton color="warning" aria-label="clear" onClick={clear} edge="end" size="small">
                        {clearIcon || <FilterListOff />}
                    </IconButton>
                ) : null}
            </Stack>
        </ListSubheader>
    );
}

export function OptionsStack({
    children,
    ...props
}: PropsWithChildren<FiltersParams<{ name: string; icon?: string }> & { clearIcon?: React.ReactElement }>): JSX.Element {
    return (
        //TODO: change to MUI MenuList for better keyboard accessibility
        <List disablePadding>
            <SelectFilterTitle {...props} />
            {children}
        </List>
    );
}
