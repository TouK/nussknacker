import { DeleteForever, ExpandLess, ExpandMore } from "@mui/icons-material";
import {
    Collapse,
    IconButton,
    List,
    ListItem,
    ListItemButton,
    ListItemText,
    styled,
    ToggleButton,
    ToggleButtonGroup,
    Typography,
} from "@mui/material";
import { capitalize, lowerCase } from "lodash";
import React, { Fragment, useState } from "react";

import { SearchHighlighter } from "../../components/toolbars/creator/SearchHighlighter";

export type UserSettingValue = { value: boolean; isDefault: boolean; hasDefault?: boolean };
type NestedRecord = {
    [key: string]: UserSettingValue | NestedRecord;
};

type CollapsibleSwitchListProps = {
    data: NestedRecord;
    onToggle: (path: string, value?: boolean | "default") => void;
    onDelete?: (path: string) => void;
    basePath?: string;
    level?: number;
    flattenSingleChild?: boolean;
    openIfOnly?: boolean;
    searchStrings?: string[];
};

export function SwitchElement({
    value: { value, isDefault },
    onChange,
}: {
    value: { value: boolean; isDefault: boolean };
    onChange: (value: boolean | "default") => void;
}) {
    const selected = isDefault ? ["default", value] : [value];
    return (
        <ToggleButtonGroup color="primary" size="small" onClick={(e) => e.stopPropagation()} value={selected}>
            <ToggleButton value={"default"} onClick={() => onChange("default")}>
                DEFAULT
            </ToggleButton>
            <ToggleButton color={isDefault ? undefined : "error"} value={false} onClick={() => onChange(false)}>
                OFF
            </ToggleButton>
            <ToggleButton color={isDefault ? undefined : "success"} value={true} onClick={() => onChange(true)}>
                ON
            </ToggleButton>
        </ToggleButtonGroup>
    );
}

export function isNestedObject(finalValue: UserSettingValue | NestedRecord) {
    return (
        typeof finalValue === "object" &&
        finalValue !== null &&
        !(Object.prototype.hasOwnProperty.call(finalValue, "isDefault") && Object.prototype.hasOwnProperty.call(finalValue, "value"))
    );
}

const CollapsibleSwitchList = ({
    data,
    onToggle,
    onDelete,
    basePath = "",
    level = 0,
    flattenSingleChild,
    openIfOnly,
    searchStrings = [],
}: CollapsibleSwitchListProps) => {
    const [openKeys, setOpenKeys] = useState<Record<string, boolean>>({});

    const toggleOpen = (key: string) => {
        setOpenKeys((prev) => ({ ...prev, [key]: !prev[key] }));
    };

    const flattenPath = (obj: NestedRecord, currentPath: string): [string, UserSettingValue | NestedRecord] => {
        let path = currentPath;
        let value: any = obj;

        while (typeof value === "object" && value !== null && Object.keys(value).length === 1) {
            const [nextKey] = Object.keys(value);
            const nextValue = value[nextKey];
            path += `.${nextKey}`;
            value = nextValue;
        }

        return [path, value];
    };

    return (
        <List disablePadding>
            {Object.entries(data)
                .sort(([a], [b]) => a.localeCompare(b))
                .sort(([, a], [, b]) => {
                    const aLength = isNestedObject(b) ? Object.keys(b).length : 0;
                    const bLength = isNestedObject(a) ? Object.keys(a).length : 0;
                    return aLength - bLength;
                })
                .map(([key, value], index, all) => {
                    const [currentPath, finalValue] =
                        flattenSingleChild && typeof value === "object" && value !== null
                            ? flattenPath(value as NestedRecord, key)
                            : [key, value];
                    const isNested = isNestedObject(finalValue);
                    const string = flattenSingleChild ? currentPath : key;
                    const label = string
                        .split(".")
                        .map((string) => capitalize(lowerCase(string)))
                        .join(" — ");
                    const fullPath = basePath ? `${basePath}.${currentPath}` : currentPath;
                    const expanded = openKeys[currentPath] || (openIfOnly && all.length === 1);
                    return isNested ? (
                        <Fragment key={currentPath}>
                            <ListItem disablePadding>
                                <ListItemButton onClick={() => toggleOpen(currentPath)} sx={{ pl: 2 + level * 2 }}>
                                    <ListItemText
                                        primary={<StyledSearchHighlighter highlights={searchStrings}>{label}</StyledSearchHighlighter>}
                                        primaryTypographyProps={{
                                            fontWeight: expanded ? "bold" : null,
                                        }}
                                        secondary={
                                            expanded ? null : (
                                                <Typography variant="caption" sx={(theme) => ({ color: theme.palette.primary.main })}>{`${
                                                    Object.keys(finalValue).length
                                                } keys`}</Typography>
                                            )
                                        }
                                    />
                                    <IconButton edge="end" size="small">
                                        {expanded ? <ExpandLess /> : <ExpandMore />}
                                    </IconButton>
                                </ListItemButton>
                            </ListItem>
                            <Collapse in={expanded} timeout="auto" unmountOnExit>
                                <CollapsibleSwitchList
                                    data={finalValue as NestedRecord}
                                    onToggle={onToggle}
                                    onDelete={onDelete}
                                    basePath={fullPath}
                                    level={level + 1}
                                    flattenSingleChild={flattenSingleChild}
                                    openIfOnly={openIfOnly}
                                />
                            </Collapse>
                        </Fragment>
                    ) : (
                        <Fragment key={currentPath}>
                            <ListItem disablePadding>
                                <ListItemButton onClick={isNested ? null : () => onToggle(fullPath)} sx={{ pl: 2 + level * 2 }}>
                                    <ListItemText
                                        primary={<StyledSearchHighlighter highlights={searchStrings}>{label}</StyledSearchHighlighter>}
                                    />
                                    {onDelete && finalValue.hasDefault ? null : (
                                        <IconButton
                                            edge="end"
                                            size="small"
                                            color="error"
                                            onClick={(e) => {
                                                e.stopPropagation();
                                                onDelete(fullPath);
                                            }}
                                            sx={{
                                                opacity: 0.5,
                                                "&:focus": {
                                                    outline: "none",
                                                },
                                            }}
                                        >
                                            <DeleteForever />
                                        </IconButton>
                                    )}
                                    <SwitchElement value={finalValue as UserSettingValue} onChange={(v) => onToggle(fullPath, v)} />
                                </ListItemButton>
                            </ListItem>
                        </Fragment>
                    );
                })}
        </List>
    );
};

export default CollapsibleSwitchList;

const StyledSearchHighlighter = styled(SearchHighlighter)({
    ".Highlighter-highlight": {
        fontWeight: "inherit",
    },
});
