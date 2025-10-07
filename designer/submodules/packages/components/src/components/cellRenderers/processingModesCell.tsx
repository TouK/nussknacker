import { Stack } from "@mui/material";
import React from "react";

import { ProcessingModeItem } from "../../scenarios/list/processingMode";
import { useComponentsFilterContext } from "../filters/useComponentsFilterContext";
import type { CellRendererParams } from "../tableWrapper";

export const ProcessingModesCell = (props: CellRendererParams) => {
    const filtersContext = useComponentsFilterContext();

    return (
        <Stack direction="row" spacing={0.25}>
            {props.row.allowedProcessingModes.map((processingMode, index) => (
                <ProcessingModeItem key={index} processingMode={processingMode} filtersContext={filtersContext} />
            ))}
        </Stack>
    );
};
