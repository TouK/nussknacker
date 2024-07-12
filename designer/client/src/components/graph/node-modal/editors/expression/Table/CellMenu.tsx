/* eslint-disable i18next/no-literal-string */
import { PopoverPosition } from "@mui/material/Popover/Popover";
import { ClickAwayListener, ListItemIcon, ListItemText, Menu, MenuItem } from "@mui/material";
import { AutoAwesome, DeleteForever } from "@mui/icons-material";
import React, { PropsWithChildren, useMemo } from "react";
import { useTableTheme } from "./tableTheme";
import { useTranslation } from "react-i18next";

interface ColumnMenuParams {
    anchorPosition: PopoverPosition | null;
    onClose: () => void;
}

export function CellMenu({ anchorPosition, onClose, children }: PropsWithChildren<ColumnMenuParams>) {
    const open = useMemo(() => Boolean(anchorPosition) && React.Children.toArray(children).length > 0, [anchorPosition, children]);
    const tableTheme = useTableTheme();
    return (
        <ClickAwayListener onClickAway={onClose}>
            <Menu
                sx={{
                    pointerEvents: "none",
                }}
                anchorReference="anchorPosition"
                anchorPosition={anchorPosition}
                open={open}
                onClose={onClose}
                PaperProps={{
                    sx: {
                        pointerEvents: "all",
                        backgroundColor: tableTheme.bgCell,
                    },
                }}
                MenuListProps={{
                    dense: true,
                }}
            >
                {children}
            </Menu>
        </ClickAwayListener>
    );
}

export function DeleteRowMenuItem({ onClick, indexes = [] }: { indexes: number[]; onClick: (indexes: number[]) => void }) {
    const { t } = useTranslation();
    return (
        <MenuItem
            onClick={() => {
                onClick(indexes);
            }}
        >
            <ListItemIcon>
                <DeleteForever fontSize="small" />
            </ListItemIcon>
            <ListItemText>
                {t("cellMenu.deleteRow", {
                    defaultValue_one: "Remove {{count}} row",
                    defaultValue_other: "Remove {{count}} rows",
                    count: indexes.length,
                })}
            </ListItemText>
        </MenuItem>
    );
}

export function ResetColumnWidthMenuItem({
    indexes,
    onClick,
    disabled,
}: {
    disabled?: boolean;
    indexes: number[];
    onClick: (indexes: number[]) => void;
}) {
    return (
        <MenuItem
            disabled={disabled}
            onClick={() => {
                onClick(indexes);
            }}
        >
            <ListItemIcon>
                <AutoAwesome fontSize="small" />
            </ListItemIcon>
            <ListItemText>Auto width</ListItemText>
        </MenuItem>
    );
}

export function DeleteColumnMenuItem({ indexes, onClick }: { indexes: number[]; onClick: (indexes: number[]) => void }) {
    const { t } = useTranslation();
    return (
        <MenuItem
            onClick={() => {
                onClick(indexes);
            }}
        >
            <ListItemIcon>
                <DeleteForever fontSize="small" />
            </ListItemIcon>
            <ListItemText>
                {t("cellMenu.deleteColumns", {
                    defaultValue_one: "Remove {{count}} column",
                    defaultValue_other: "Remove {{count}} columns",
                    count: indexes.length,
                })}
            </ListItemText>
        </MenuItem>
    );
}
