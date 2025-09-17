import type { CustomCell } from "@glideapps/glide-data-grid";
import { Box } from "@mui/material";
import React, { useCallback, useRef } from "react";

import { Dropdown } from "./Dropdown";

export interface SourceSelectCellData {
    kind: "source-select-cell";
    value: string;
    options: string[];
}
export type SourceSelectCell = CustomCell<SourceSelectCellData>;

interface EditorProps {
    value: SourceSelectCell;
    onChange: (cell: SourceSelectCell) => void;
    onFinishedEditing: (cell: SourceSelectCell) => void;
}

export const SourceEditor: React.FC<EditorProps> = ({ value, onFinishedEditing }) => {
    const setVal = useCallback(
        (newValue: string) => {
            if (newValue === value.data.value) {
                return;
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
            <Dropdown value={value.data.value} options={value.data.options} onValueChange={setVal} />
        </Box>
    );
};
