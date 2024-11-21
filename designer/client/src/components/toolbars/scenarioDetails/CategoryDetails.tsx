import React from "react";
import { Skeleton, Typography } from "@mui/material";
import { Scenario } from "../../Process/types";
import { useGetAllCombinations } from "../../useGetAllCombinations";
import { useTranslation } from "react-i18next";

export const CategoryDetails = ({ scenario }: { scenario: Scenario }) => {
    const { t } = useTranslation();
    const { isAllCombinationsLoading, isCategoryFieldVisible } = useGetAllCombinations({
        processCategory: scenario.processCategory,
        processingMode: scenario.processingMode,
        processEngine: scenario.engineSetupName,
    });

    return (
        <>
            {isAllCombinationsLoading ? (
                <Skeleton variant="text" sx={{ fontSize: "1.25rem" }} width={"50%"} />
            ) : (
                isCategoryFieldVisible && (
                    <Typography title={t("panels.scenarioDetails.tooltip.category", "Category")} variant={"body2"}>
                        {scenario.processCategory} /
                    </Typography>
                )
            )}
        </>
    );
};
