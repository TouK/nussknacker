import { Box, Typography } from "@mui/material";
import moment from "moment";
import React, { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { usePreviousDifferent } from "rooks";

import type { TestCaseAssertionResult } from "../../../http/resultsWithCountsDto";
import { VisibleDataType } from "../../../reducers/graph/types";
import { getVisibleDataType } from "../../../reducers/selectors/getLiveData";
import { useAppSelector } from "../../../store/storeHelpers";

interface Props {
    testCaseAssertionResult: TestCaseAssertionResult | null;
}

export const Footer = ({ testCaseAssertionResult }: Props) => {
    const isLoading = testCaseAssertionResult?.status === "loading";

    return (
        <Box px={1} sx={{ opacity: 0.7 }}>
            <LastRun isLoading={isLoading} />
        </Box>
    );
};

interface LastRunProps {
    isLoading: boolean;
}

const LastRun = ({ isLoading }: LastRunProps) => {
    const { t } = useTranslation();
    const visibleDataType = useAppSelector(getVisibleDataType);

    const [lastRun, setLastRun] = useState<number | null>(null);
    const [lastRunDisplayValue, setLastRunDisplayValue] = useState("-");

    const previousLoading = usePreviousDifferent(isLoading);

    useEffect(
        function setLastRunValue() {
            if (previousLoading && !isLoading && visibleDataType === VisibleDataType.test) {
                setLastRun(Date.now());
            } else {
                setLastRun(null);
            }
        },
        [isLoading, previousLoading, visibleDataType],
    );

    useEffect(
        function updateDisplayValue() {
            if (!lastRun) {
                setLastRunDisplayValue("-");
                return;
            }

            const updateDisplay = () => {
                setLastRunDisplayValue(moment(lastRun).fromNow(false));
            };

            updateDisplay();
            const intervalId = setInterval(updateDisplay, 60000);

            return () => clearInterval(intervalId);
        },
        [lastRun],
    );

    return (
        <>
            <Typography variant={"overline"}>{t("testCasesFooter.lastRun", "Last run:")}</Typography>
            <Typography color={"text.primary"} pl={0.5} variant={"overline"}>
                {lastRunDisplayValue}
            </Typography>
        </>
    );
};
