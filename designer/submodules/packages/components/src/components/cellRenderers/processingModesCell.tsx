import React from "react";

import { ProcessingModeItem } from "../../scenarios/list/processingMode";
import { useComponentsFilterContext } from "../filters/useComponentsFilterContext";
import type { CellRendererParams } from "../tableWrapper";

export const ProcessingModesCell = (props: CellRendererParams) => {
    const filtersContext = useComponentsFilterContext();

    return (
        <>
            {props.row.allowedProcessingModes.map((processingMode, index) => (
                <ProcessingModeItem key={index} processingMode={processingMode} filtersContext={filtersContext} />
            ))}
        </>
    );
};
