import { Box } from "@mui/material";
import { get } from "lodash";
import type { PropsWithChildren } from "react";
import React, { useCallback, useEffect, useMemo } from "react";
import type { Styling } from "react-base16-styling/src/types";
import { useDrag } from "react-dnd";
import { getEmptyImage } from "react-dnd-html5-backend";
import type { KeyPath } from "react-json-tree";
import { JSONTree } from "react-json-tree";

import { useJsonTreeTheme } from "../../../../containers/theme/useJsonTreeTheme";
import { DndTypes } from "../../../DndTypes";

class NotChanged {
    constructor(private value) {}
    get [Symbol.toStringTag]() {
        return "same as input";
    }
}

function normalizePath(keyPath: KeyPath | (string | number)[]) {
    return [...keyPath].reverse();
}

export type SpelDndContext = {
    value: any;
    path: KeyPath;
    type: "key" | "value";
};

function DraggableValue({
    disabled,
    path,
    value,
    type = "key",
    children,
}: PropsWithChildren<{
    disabled?: boolean;
    path: SpelDndContext["path"];
    value: unknown;
    type?: SpelDndContext["type"];
}>) {
    const [{ isActive }, drag, preview] = useDrag<SpelDndContext, void, { isActive: boolean }>(() => ({
        type: DndTypes.VALUE,
        item: { path, type, value },
        options: { dropEffect: "copy" },
        canDrag: !disabled,
        collect: (monitor) => ({ isActive: monitor.isDragging() }),
    }));

    useEffect(() => {
        preview(getEmptyImage());
        return () => {
            preview(null);
        };
    }, [preview]);

    return (
        <Box
            component="span"
            ref={(i: HTMLElement) => {
                drag(i);
            }}
            sx={{
                cursor: disabled ? "default" : "grab",
                "&:active": {
                    cursor: "grabbing",
                },
            }}
        >
            {children}
        </Box>
    );
}

export function ContextTree({ data, oldFields = [] }: { data: Record<string, unknown>; oldFields?: string[] }): React.JSX.Element {
    const keys = useMemo(() => Object.keys(data), [data]);
    const { extend, treeStyle } = useJsonTreeTheme();

    const expandedFields = useMemo(
        () =>
            keys.filter((key) => {
                if (key === "inputMeta") return false;
                return !oldFields.includes(key);
            }),
        [keys, oldFields],
    );

    const isOldField = useCallback(
        (keyPath: KeyPath) => keyPath.length === 1 && oldFields.length >= 1 && oldFields.includes(keyPath[0].toString()),
        [oldFields],
    );

    return (
        <JSONTree
            data={data}
            hideRoot
            sortObjectKeys
            theme={{
                extend,
                tree: {
                    ...treeStyle,
                    fontSize: "1rem",
                    margin: 0,
                    padding: ".25em .5em",
                },
                nestedNode: ({ style }: Styling, keyPath: string[], nodeType: string) => ({
                    style: {
                        ...style,
                        opacity: isOldField(keyPath) ? 0.5 : null,
                    },
                }),
                value: ({ style }: Styling, nodeType: string, keyPath: string[]) => ({
                    style: {
                        ...style,
                        opacity: isOldField(keyPath) ? 0.5 : null,
                    },
                }),
            }}
            shouldExpandNodeInitially={([key], data, level) => {
                return level < 2 && expandedFields.includes(key?.toString());
            }}
            getItemString={(nodeType, data, itemType, itemString, keyPath) => {
                const path = normalizePath(keyPath);
                return (
                    <>
                        <DraggableValue path={path} value={data} type="value">
                            {nodeType === "Array" ? "List" : nodeType === "Object" ? "Record" : itemType} ({itemString})
                        </DraggableValue>
                    </>
                );
            }}
            labelRenderer={(keyPath, nodeType, expanded, expandable) => {
                const path = normalizePath(keyPath);
                return (
                    <>
                        <DraggableValue path={path} value={get(data, path)} type="key">
                            {keyPath[0]}
                        </DraggableValue>
                        :
                    </>
                );
            }}
            valueRenderer={(valueAsString: string, value, ...keyPath) => {
                const path = normalizePath(keyPath);
                return (
                    <DraggableValue path={path} value={get(data, path)} type="value">
                        {valueAsString}
                    </DraggableValue>
                );
            }}
            postprocessValue={(value) => {
                if (oldFields.map((f) => data[f]).includes(value)) {
                    return new NotChanged(value);
                }
                return value;
            }}
        />
    );
}
