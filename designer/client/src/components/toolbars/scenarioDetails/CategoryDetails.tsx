import React from "react";
import { Skeleton, Typography } from "@mui/material";
import { Scenario } from "../../Process/types";
import { useGetAllCombinations } from "../../useGetAllCombinations";

export const CategoryDetails = ({ scenario }: { scenario: Scenario }) => {
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
                isCategoryFieldVisible && <Typography variant={"body2"}>{scenario.processCategory} /</Typography>
            )}
        </>
    );
};
