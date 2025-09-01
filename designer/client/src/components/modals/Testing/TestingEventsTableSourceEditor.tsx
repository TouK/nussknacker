import type { CustomCell } from "@glideapps/glide-data-grid";
import React, { useCallback, useRef } from "react";

import TestingEventsTableDropdown from "./TestingEventsTableDropdown";

// Recreate the SourceSelectCell type locally (kept in sync with TestingEventsTable.tsx)
export interface SourceSelectCellData {
    kind: "source-select-cell";
    value: string;
    options: string[];
}
export type SourceSelectCell = CustomCell<SourceSelectCellData>;

interface EditorProps {
    value: SourceSelectCell;
    onChange: (cell: SourceSelectCell) => void;
    onFinishedEditing: () => void;
    target: { width: number; height: number };
}

export const TestingEventsTableSourceEditor: React.FC<EditorProps> = ({ value, onChange, onFinishedEditing, target }) => {
    const originalValueRef = useRef(value.data.value ?? "");
    const currentValueRef = useRef(value.data.value ?? "");

    const setVal = useCallback(
        (val: string) => {
            currentValueRef.current = val;
            onChange({
                ...value,
                copyData: val,
                data: { ...value.data, value: val },
            });
        },
        [onChange, value],
    );

    const commit = useCallback(() => {
        onFinishedEditing();
    }, [onFinishedEditing]);

    const cancel = useCallback(() => {
        if (currentValueRef.current !== originalValueRef.current) {
            onChange({
                ...value,
                copyData: originalValueRef.current,
                data: { ...value.data, value: originalValueRef.current },
            });
        }
        onFinishedEditing();
    }, [onChange, onFinishedEditing, value]);

    return (
        <div
            style={{
                width: target.width,
                height: target.height,
                display: "flex",
                alignItems: "center",
                padding: 0,
            }}
        >
            {/*TODO There is a problem with using Mui elements or react-select. Change value doesn't work probably due to react portal usage both in the glide-data-grid and dropdown libraries
             */}
            <TestingEventsTableDropdown
                value={currentValueRef.current}
                options={value.data.options}
                onValueChange={setVal}
                onCommit={commit}
                onCancel={cancel}
                style={{ width: "100%" }}
            />
        </div>
    );
};

export default TestingEventsTableSourceEditor;
