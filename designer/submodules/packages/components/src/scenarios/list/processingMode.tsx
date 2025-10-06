import { Button, Typography } from "@mui/material";
import i18next from "i18next";
import React, { useCallback, useMemo } from "react";
import { useTranslation } from "react-i18next";

import Batch from "../../assets/icons/batch.svg";
import RequestResponse from "../../assets/icons/request-response.svg";
import Streaming from "../../assets/icons/streaming.svg";
import type { FiltersContextType } from "../../common/filters/filtersContext";

export enum ProcessingMode {
    "streaming" = "Unbounded-Stream",
    "requestResponse" = "Request-Response",
    "batch" = "Bounded-Stream",
}

export const processingModeItems = [
    {
        name: ProcessingMode.streaming,
        get displayableName() {
            return i18next.t(`scenarioDetails.processingModeVariants.streaming`, "Streaming");
        },
        Icon: Streaming,
    },
    {
        name: ProcessingMode.requestResponse,
        get displayableName() {
            return i18next.t(`scenarioDetails.processingModeVariants.requestResponse`, "Request-Response");
        },
        Icon: RequestResponse,
    },
    {
        name: ProcessingMode.batch,
        get displayableName() {
            return i18next.t(`scenarioDetails.processingModeVariants.batch`, "Batch");
        },
        Icon: Batch,
    },
];

interface Props {
    processingMode: ProcessingMode;
    filtersContext: FiltersContextType;
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
            title={t("scenariosList.tooltip.processingMode", "Processing mode")}
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
