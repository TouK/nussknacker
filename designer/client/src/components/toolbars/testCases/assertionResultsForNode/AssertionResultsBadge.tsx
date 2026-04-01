import { Box, Typography, useTheme } from "@mui/material";
import React from "react";

import { calculateAssertionResultsSummary } from "../../../../containers/assertions/assertionResultsUtils";
import type { TestAssertionResult } from "../../../../http/resultsWithCountsDto";

interface AssertionResultsBadgeProps {
    assertionResults: TestAssertionResult[] | undefined;
}

export const AssertionResultsBadge = ({ assertionResults }: AssertionResultsBadgeProps) => {
    const { passedCount, total, failedCount, hasResult } = calculateAssertionResultsSummary(assertionResults);
    const theme = useTheme();
    const fillColor = failedCount > 0 ? theme.palette.error.dark : theme.palette.success.dark;
    const textColor = theme.palette.text.secondary;

    if (assertionResults === undefined) return null;

    return (
        <Box
            sx={{
                display: "flex",
                alignItems: "center",
                justifyContent: "center",
                height: "18px",
                px: 0.75,
                backgroundColor: hasResult ? fillColor : theme.palette.action.disabled,
                borderRadius: 1,
            }}
        >
            <Typography
                variant={"body2"}
                sx={{
                    fontWeight: "bold",
                    color: textColor,
                    fontSize: 11,
                }}
            >
                {hasResult ? `${passedCount}/${total}` : "N/A"}
            </Typography>
        </Box>
    );
};
