import React, { PropsWithChildren } from "react";
import { FilterListOff } from "@mui/icons-material";
import { Divider, ListItemIcon, ListItemText, MenuItem } from "@mui/material";
import { FiltersParams } from "./simpleOptionsStack";
import { useTranslation } from "react-i18next";

export function ClearListItem(props: FiltersParams<string, any> & { clearIcon?: React.ReactElement }): JSX.Element {
    const { value, onChange, label, clearIcon } = props;
    const clear = () => onChange?.(null);
    const showClear = value?.length;
    return (
        <MenuItem onClick={clear} disabled={!showClear} dense autoFocus>
            <ListItemIcon aria-label="clear">{clearIcon || <FilterListOff color={showClear ? "warning" : "inherit"} />}</ListItemIcon>
            <ListItemText primary={label} />
        </MenuItem>
    );
}

export function OptionsStack({
    children,
    ...props
}: PropsWithChildren<FiltersParams<string, { name: string; icon?: string }> & { clearIcon?: React.ReactElement }>): JSX.Element {
    const { t } = useTranslation();
    return (
        <>
            <ClearListItem {...props} label={t("filtes.clear", "Default")} />
            <Divider />
            {children}
        </>
    );
}
