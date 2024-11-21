import React, { useEffect, useState } from "react";
import { useProcessFormDataOptions } from "../../useProcessFormDataOptions";
import HttpService, { ScenarioParametersCombination } from "../../../http/HttpService";
import { Skeleton, Typography } from "@mui/material";
import { Scenario } from "../../Process/types";
import { useTranslation } from "react-i18next";

export const CategoryDetails = ({ scenario }: { scenario: Scenario }) => {
    const { t } = useTranslation();
    const [allCombinations, setAllCombinations] = useState<ScenarioParametersCombination[]>([]);
    const [isAllCombinationsLoading, setIsAllCombinationsLoading] = useState<boolean>(false);

    const { isCategoryFieldVisible } = useProcessFormDataOptions({
        allCombinations,
        value: {
            processCategory: scenario.processCategory,
            processingMode: scenario.processingMode,
            processEngine: scenario.engineSetupName,
        },
    });

    useEffect(() => {
        setIsAllCombinationsLoading(true);
        HttpService.fetchScenarioParametersCombinations()
            .then((response) => {
                setAllCombinations(response.data.combinations);
            })
            .finally(() => {
                setIsAllCombinationsLoading(false);
            });
    }, []);

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
