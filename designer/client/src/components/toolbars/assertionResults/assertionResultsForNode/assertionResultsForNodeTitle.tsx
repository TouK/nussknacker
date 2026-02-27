import { Box, Typography, useTheme } from "@mui/material";
import type { ReactNode } from "react";
import React from "react";

import { calculateAssertionResultsSummary } from "../../../../containers/assertions/assertionResultsUtils";
import type { TestAssertionResult } from "../../../../http/resultsWithCountsDto";

interface Props {
    title: string;
    assertionResults: TestAssertionResult[];
    action: ReactNode;
}

export const AssertionResultsForNodeTitle = ({ title, assertionResults, action }: Props) => {
    return (
        <Box display={"flex"} alignItems={"center"} gap={0.75}>
            <Typography variant={"body2"}>{title}</Typography>
            <AssertionResultsBadge assertionResults={assertionResults} />
            {action}
        </Box>
    );
};

interface BadgeProps {
    assertionResults: TestAssertionResult[];
}

const AssertionResultsBadge = ({ assertionResults }: BadgeProps) => {
    const { passedCount, total, failedCount, hasResult } = calculateAssertionResultsSummary(assertionResults);
    const theme = useTheme();
    const fillColor = failedCount > 0 ? theme.palette.error.dark : theme.palette.success.dark;
    const textColor = theme.palette.text.secondary;

    if (!hasResult) return null;

    return (
        <Box
            sx={{
                display: "flex",
                alignItems: "center",
                justifyContent: "center",
                height: "18px",
                px: 0.75,
                backgroundColor: fillColor,
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
                {passedCount}/{total}
            </Typography>
        </Box>
    );
};
