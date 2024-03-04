import React, { useCallback, useMemo } from "react";
import { Button, Typography } from "@mui/material";
import { FiltersContextType } from "../../common/filters/filtersContext";
import { ScenariosFiltersModel } from "../filters/scenariosFiltersModel";
import { NuIcon } from "../../common";
import i18next from "i18next";

export enum ProcessingMode {
    "streaming" = "Unbounded-Stream",
    "requestResponse" = "Request-Response",
    "batch" = "Bounded-Stream",
}

const processingModeItems = [
    {
        name: ProcessingMode.streaming,
        displayableName: i18next.t(`scenarioDetails.processingModeVariants.streaming`, "Streaming"),
        icon: "/assets/processing-mode/streaming.svg",
    },
    {
        name: ProcessingMode.requestResponse,
        displayableName: i18next.t(`scenarioDetails.processingModeVariants.requestResponse`, "Request-Response"),
        icon: "/assets/processing-mode/request-response.svg",
    },
    {
        name: ProcessingMode.batch,
        displayableName: i18next.t(`scenarioDetails.processingModeVariants.requestResponse`, "Batch"),
        icon: "/assets/processing-mode/batch.svg",
    },
];

interface Props {
    processingMode: ProcessingMode;
    filtersContext: FiltersContextType<ScenariosFiltersModel>;
}
export const ProcessingModeItem = ({ processingMode, filtersContext }: Props) => {
    const { setFilter, getFilter } = filtersContext;
    const filterValue = useMemo(() => getFilter("PROCESSING_MODE", true), [getFilter]);

    const isSelected = useMemo(() => filterValue.includes(processingMode), [filterValue, processingMode]);

    const onClick = useCallback(
        (e) => {
            setFilter("PROCESSING_MODE")(isSelected ? filterValue.filter((v) => v !== processingMode) : [...filterValue, processingMode]);
            e.preventDefault();
            e.stopPropagation();
        },
        [setFilter, isSelected, filterValue, processingMode],
    );

    const item = processingModeItems.find((processingModeItem) => processingModeItem.name === processingMode);

    if (!item) {
        return null;
    }

    console.log(item);
    return (
        <Button
            color={isSelected ? "primary" : "inherit"}
            sx={{ textTransform: "capitalize", display: "flex", gap: 1, alignItems: "center", fontSize: "1rem", py: 0.25 }}
            onClick={onClick}
        >
            <NuIcon src={item.icon} sx={{ fontSize: "1.2em", color: "inherit" }} />
            <Typography variant={"caption"}>{item.displayableName}</Typography>
        </Button>
    );
};
