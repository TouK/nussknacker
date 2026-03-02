import type { CustomCell } from "@glideapps/glide-data-grid";
import { Box } from "@mui/material";
import React, { useCallback } from "react";

import { Dropdown } from "./Dropdown";

export interface SourceSelectCellData {
    kind: "source-select-cell";
    value: string;
    displayValue: string;
    options: string[];
    optionLabels: string[];
}
export type SourceSelectCell = CustomCell<SourceSelectCellData>;

interface EditorProps {
    value: SourceSelectCell;
    onFinishedEditing: (cell?: SourceSelectCell) => void;
}

export const SourceEditor: React.FC<EditorProps> = ({ value, onFinishedEditing }) => {
    const setVal = useCallback(
        (newValue: string) => {
            if (newValue === value.data.value) {
                // just close the editor if value did not change
                onFinishedEditing();
            }

            onFinishedEditing({
                ...value,
                copyData: newValue,
                data: { ...value.data, value: newValue },
            });
        },
        [onFinishedEditing, value],
    );

    return (
        <Box
            id={"source-editor"}
            sx={{
                display: "flex",
                alignItems: "center",
                padding: 0,
            }}
        >
            <Dropdown value={value.data.value} options={value.data.options} optionLabels={value.data.optionLabels} onValueChange={setVal} />
        </Box>
    );
};
