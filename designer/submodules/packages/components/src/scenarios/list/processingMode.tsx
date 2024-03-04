import React, { useCallback, useMemo } from "react";
import BatchIcon from "../../assets/icons/batch.svg";
import RequestResponseIcon from "../../assets/icons/request-response.svg";
import StreamingIcon from "../../assets/icons/streaming.svg";
import { useTranslation } from "react-i18next";
import { Button, Typography } from "@mui/material";
import { FiltersContextType } from "../../common/filters/filtersContext";
import { ScenariosFiltersModel } from "../filters/scenariosFiltersModel";

export enum ProcessingMode {
    "streaming" = "Unbounded-Stream",
    "requestResponse" = "Request-Response",
    "batch" = "Bounded-Stream",
}

interface Props {
    processingMode: ProcessingMode;
    filtersContext: FiltersContextType<ScenariosFiltersModel>;
}
export const ProcessingModeItem = ({ processingMode, filtersContext }: Props) => {
    const { t } = useTranslation();
    const ProcessingModeIcon =
        processingMode === ProcessingMode.streaming
            ? StreamingIcon
            : processingMode === ProcessingMode.batch
            ? BatchIcon
            : RequestResponseIcon;

    const processingModeVariantName = useMemo(() => {
        switch (processingMode) {
            case ProcessingMode.batch: {
                return t(`scenarioDetails.processingModeVariants.batch`, "Batch");
            }

            case ProcessingMode.requestResponse: {
                return t(`scenarioDetails.processingModeVariants.requestResponse`, "Request-Response");
            }
            case ProcessingMode.streaming:
                return t(`scenarioDetails.processingModeVariants.streaming`, "Streaming");
        }
    }, [processingMode, t]);

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

    return (
        <Button
            color={isSelected ? "primary" : "inherit"}
            sx={{ textTransform: "capitalize", display: "flex", gap: 1, alignItems: "center", fontSize: "1rem" }}
            onClick={onClick}
        >
            <ProcessingModeIcon />
            <Typography variant={"caption"}>{processingModeVariantName}</Typography>
        </Button>
    );
};
