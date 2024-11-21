import React, { useCallback, useMemo } from "react";
import { Button, Typography } from "@mui/material";
import { FiltersContextType } from "../../common/filters/filtersContext";
import { ScenariosFiltersModel } from "../filters/scenariosFiltersModel";
import i18next from "i18next";
import Streaming from "../../assets/icons/streaming.svg";
import Batch from "../../assets/icons/batch.svg";
import RequestResponse from "../../assets/icons/request-response.svg";
import { useTranslation } from "react-i18next";

export enum ProcessingMode {
    "streaming" = "Unbounded-Stream",
    "requestResponse" = "Request-Response",
    "batch" = "Bounded-Stream",
}

export const processingModeItems = [
    {
        name: ProcessingMode.streaming,
        displayableName: i18next.t(`scenarioDetails.processingModeVariants.streaming`, "Streaming"),
        Icon: Streaming,
    },
    {
        name: ProcessingMode.requestResponse,
        displayableName: i18next.t(`scenarioDetails.processingModeVariants.requestResponse`, "Request-Response"),
        Icon: RequestResponse,
    },
    {
        name: ProcessingMode.batch,
        displayableName: i18next.t(`scenarioDetails.processingModeVariants.batch`, "Batch"),
        Icon: Batch,
    },
];

interface Props {
    processingMode: ProcessingMode;
    filtersContext: FiltersContextType<ScenariosFiltersModel>;
}
export const ProcessingModeItem = ({ processingMode, filtersContext }: Props) => {
    const { t } = useTranslation();
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

    return (
        <Button
            title={t("addProcessForm.label.processingMode", "Processing mode")}
            color={isSelected ? "primary" : "inherit"}
            sx={{ textTransform: "capitalize", display: "flex", gap: 1, alignItems: "center", fontSize: "1rem", py: 0.25, mx: 0 }}
            onClick={onClick}
            aria-selected={isSelected}
        >
            <item.Icon color={"inherit"} />
            <Typography variant={"caption"}>{item.displayableName}</Typography>
        </Button>
    );
};
