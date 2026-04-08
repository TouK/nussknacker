import React, { useCallback, useContext, useEffect } from "react";
import type ReactAce from "react-ace/lib/ace";
import type { XYCoord } from "react-dnd";
import { useDragLayer, useDrop } from "react-dnd";
import type { KeyPath } from "react-json-tree";

import { memoizeByArgsWithTTL } from "../../../../../helpers/memoizeByArgsWithTTL";
import { DndTypes } from "../../../../DndTypes";
import { AcceptedLangCtx } from "../../../../ValueDragPreview";
import type { SpelDndContext } from "../../io/ContextTree";
import { ExpressionLang } from "./types";

export const UseRecordsPrefixContext = React.createContext(false);
export const UseValueInsertContext = React.createContext(false);

export function jsonToSpelString(value: any): string {
    if (value === null) return "null";
    if (typeof value === "string") return `"${value.replace(/"/g, '\\"')}"`;
    if (typeof value === "number" || typeof value === "boolean") return String(value);

    if (Array.isArray(value)) {
        return `{${value.map(jsonToSpelString).join(",")}}`;
    }

    if (typeof value === "object") {
        const entries = Object.entries(value).map(([key, val]) => [key.match(/\s/) ? `"${key}"` : key, jsonToSpelString(val)].join(": "));
        return `{ ${entries.join(", ")} }`;
    }

    throw new Error(`Unsupported type: ${typeof value}`);
}

export const getPathString = memoizeByArgsWithTTL((item: KeyPath = []): string => {
    return item
        .map((key, index) => {
            if (index === 0) return `${key}`;
            if (typeof key === "number") return `[${key}]`;
            return `["${key}"]`;
        })
        .join("");
});

const getTextToInsert = memoizeByArgsWithTTL((item: SpelDndContext, language: ExpressionLang): string => {
    switch (item?.type) {
        case "key": {
            const path = getPathString(item.path);
            switch (language) {
                case ExpressionLang.JsonTemplate:
                case ExpressionLang.SpELTemplate:
                    return `#{ #${path} }`;
                case ExpressionLang.SpEL:
                    return `#${path}`;
                default:
                    return path;
            }
        }
        case "value": {
            switch (language) {
                case ExpressionLang.SpELTemplate:
                case ExpressionLang.SpEL:
                    return jsonToSpelString(item.value);
            }
        }
    }
    switch (typeof item?.value) {
        case "string":
        case "number":
        case "boolean":
            return `${item.value}`;
    }
    return JSON.stringify(item?.value);
});

export function getInsertText(
    item: SpelDndContext,
    language: ExpressionLang,
    { useRecordsPrefix, useValueInsert }: { useRecordsPrefix: boolean; useValueInsert: boolean },
): string {
    if (useRecordsPrefix && item.recordIndex !== undefined) {
        return `#records[${item.recordIndex}].${getPathString(item.path)}`;
    }
    if (useValueInsert) {
        return jsonToSpelString(item.value);
    }
    return getTextToInsert(item, language);
}

export function useAceDndTarget(editorRef: React.MutableRefObject<ReactAce>, language: ExpressionLang) {
    const getEditor = useCallback(() => editorRef?.current?.editor, [editorRef]);

    const removePlaceholder = useCallback(() => {
        getEditor()?.removeGhostText();
    }, [getEditor]);

    const useRecordsPrefix = useContext(UseRecordsPrefixContext);
    const useValueInsert = useContext(UseValueInsertContext);

    const getText = useCallback(
        (item: SpelDndContext) => getInsertText(item, language, { useRecordsPrefix, useValueInsert }),
        [language, useRecordsPrefix, useValueInsert],
    );

    const addPlaceholder = useCallback(
        (clientOffset: XYCoord, item: SpelDndContext) => {
            const editor = getEditor();
            if (!editor) return;

            editor.session.selection.clearSelection();
            const screenCoordinates = editor.renderer.pixelToScreenCoordinates(clientOffset.x, clientOffset.y);
            const documentPosition = editor.session.screenToDocumentPosition(screenCoordinates.row, screenCoordinates.column);
            editor.setGhostText(getText(item), documentPosition);
        },
        [getEditor, getText],
    );

    const insertValue = useCallback(
        (clientOffset: XYCoord, item: SpelDndContext) => {
            const editor = getEditor();
            if (!editor) return;

            const coords = editor.renderer.pixelToScreenCoordinates(clientOffset.x, clientOffset.y);
            editor.session.insert(coords, getText(item));
        },
        [getEditor, getText],
    );

    const setAcceptedLang = useContext(AcceptedLangCtx);
    const [{ isOver }, connectDropTarget] = useDrop<SpelDndContext, void, { isOver: boolean }>(() => ({
        accept: [DndTypes.VALUE],
        hover: (item, monitor) => {
            if (!monitor.canDrop()) return;
            addPlaceholder(monitor.getClientOffset(), item);
        },
        drop: (item, monitor) => {
            if (!monitor.canDrop()) return;
            insertValue(monitor.getClientOffset(), item);
        },
        collect: (monitor) => ({
            isOver: monitor.isOver(),
        }),
    }));

    useEffect(() => {
        if (!isOver) {
            return removePlaceholder();
        }
        setAcceptedLang?.(language);
    }, [removePlaceholder, isOver, setAcceptedLang, language]);

    const { shouldBlockEditor } = useDragLayer((monitor) => ({
        shouldBlockEditor: monitor.isDragging() && monitor.getItemType() === DndTypes.VALUE,
    }));

    useEffect(() => {
        // cleanest way to block default drop behavior
        getEditor()?.setOptions({ readOnly: shouldBlockEditor });
    }, [getEditor, shouldBlockEditor]);

    useEffect(() => {
        const container = getEditor()?.container;
        if (!container) return;
        connectDropTarget(container);
    }, [connectDropTarget, getEditor]);

    return { isOver };
}
