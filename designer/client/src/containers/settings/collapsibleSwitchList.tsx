import { ExpandLess, ExpandMore } from "@mui/icons-material";
import { Collapse, IconButton, List, ListItem, ListItemButton, ListItemText, Switch, Typography } from "@mui/material";
import { capitalize, lowerCase } from "lodash";
import React, { Fragment, useState } from "react";

type Primitive = boolean | number | string;
type NestedRecord = {
    [key: string]: Primitive | NestedRecord;
};

type Props = {
    data: NestedRecord;
    onToggle: (path: string) => void;
    basePath?: string;
    level?: number;
    flattenSingleChild?: boolean;
};

const CollapsibleSwitchList = ({ data, onToggle, basePath = "", level = 0, flattenSingleChild = true }: Props) => {
    const [openKeys, setOpenKeys] = useState<Record<string, boolean>>({});

    const toggleOpen = (key: string) => {
        setOpenKeys((prev) => ({ ...prev, [key]: !prev[key] }));
    };

    const flattenPath = (obj: NestedRecord, currentPath: string): [string, Primitive | NestedRecord] => {
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

    const renderValue = (path: string, value: Primitive) => {
        if (typeof value === "boolean") {
            return <Switch edge="end" checked={value} onChange={() => onToggle(path)} />;
        }

        return null;
    };

    return (
        <List disablePadding>
            {Object.entries(data)
                .sort(([a], [b]) => a.localeCompare(b))
                .sort(([, a], [, b]) => Object.keys(b).length - Object.keys(a).length)
                .map(([key, value]) => {
                    const [currentPath, finalValue] =
                        flattenSingleChild && typeof value === "object" && value !== null
                            ? flattenPath(value as NestedRecord, key)
                            : [key, value];
                    const isObject = typeof finalValue === "object" && finalValue !== null;
                    const string = flattenSingleChild ? currentPath : key;
                    const label = string
                        .split(".")
                        .map((string) => capitalize(lowerCase(string)))
                        .join(" — ");
                    const fullPath = basePath ? `${basePath}.${currentPath}` : currentPath;
                    const expanded = openKeys[currentPath];
                    return isObject ? (
                        <Fragment key={currentPath}>
                            <ListItem disablePadding>
                                <ListItemButton onClick={() => toggleOpen(currentPath)} sx={{ pl: 2 + level * 2 }}>
                                    <ListItemText
                                        primary={expanded ? <strong>{label}</strong> : label}
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
                                    basePath={fullPath}
                                    level={level + 1}
                                    flattenSingleChild={flattenSingleChild}
                                />
                            </Collapse>
                        </Fragment>
                    ) : (
                        <Fragment key={currentPath}>
                            <ListItem disablePadding>
                                <ListItemButton
                                    onClick={typeof finalValue === "boolean" ? () => onToggle(fullPath) : null}
                                    sx={{ pl: 2 + level * 2 }}
                                >
                                    <ListItemText primary={label} />
                                    {renderValue(fullPath, finalValue as Primitive)}
                                </ListItemButton>
                            </ListItem>
                        </Fragment>
                    );
                })}
        </List>
    );
};

export default CollapsibleSwitchList;
