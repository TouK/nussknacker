import React from "react";
import { useFilterContext } from "../../common";
import { ProcessingModeItem } from "../../scenarios/list/processingMode";
import { CellRendererParams } from "../tableWrapper";

export const ProcessingModesCell = (props: CellRendererParams) => {
    const filtersContext = useFilterContext();

    return (
        <>
            {props.row.allowedProcessingModes.map((processingMode, index) => (
                <ProcessingModeItem key={index} processingMode={processingMode} filtersContext={filtersContext} />
            ))}
        </>
    );
};
