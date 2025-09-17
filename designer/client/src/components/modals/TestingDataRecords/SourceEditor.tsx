import type { CustomCell } from "@glideapps/glide-data-grid";
import { Box } from "@mui/material";
import React, { useCallback, useRef } from "react";

import Dropdown from "./Dropdown";

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

export const SourceEditor: React.FC<EditorProps> = ({ value, onChange, onFinishedEditing }) => {
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
        <Box
            id={"source-editor"}
            sx={{
                display: "flex",
                alignItems: "center",
                padding: 0,
            }}
        >
            <Dropdown
                value={currentValueRef.current}
                options={value.data.options}
                onValueChange={setVal}
                onCommit={commit}
                onCancel={cancel}
                commitOnClick
            />
        </Box>
    );
};

export default SourceEditor;
