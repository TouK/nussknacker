import { Box, CircularProgress, Divider, Typography } from "@mui/material";
import SvgIcon from "@mui/material/SvgIcon/SvgIcon";
import React, { useCallback, useEffect, useState } from "react";
import { useTranslation } from "react-i18next";

import { changeActiveTestCase } from "../../../actions/nk/testingActions";
import TestingIcon from "../../../assets/img/toolbarButtons/test.svg";
import type { TestCaseAssertionResult } from "../../../http/resultsWithCountsDto";
import type { TestCase } from "../../../reducers/graph/testCase";
import { getActiveTestCaseId } from "../../../reducers/selectors/testing";
import { useAppDispatch, useAppSelector } from "../../../store/storeHelpers";
import { Expandable } from "../../common/Expandable";
import { InfoTooltip } from "../../graph/node-modal/editors/InfoTooltip/InfoTooltip";
import { useRunTestScenario } from "../test/useRunTestScenario";
import { AssertionResultsBadge } from "./assertionResultsForNode/AssertionResultsBadge";
import { Definitions } from "./definitions";
import { Footer } from "./footer";
import { Results } from "./results";
import type { TestCaseMode } from "./TestCaseSwitchMode";
import { TestCaseSwitchMode } from "./TestCaseSwitchMode";

interface TestCaseExpandableProps {
    testCase: TestCase;
    testCaseAssertionResult: TestCaseAssertionResult | undefined;
}

export const TestCaseExpandable = ({ testCase, testCaseAssertionResult }: TestCaseExpandableProps) => {
    const [mode, setMode] = useState<TestCaseMode>("results");

    const activeTestCaseId = useAppSelector(getActiveTestCaseId);
    const dispatch = useAppDispatch();

    const isActive = activeTestCaseId === testCase.id;
    const [isCollapsed, setIsCollapsed] = useState(false);

    /**
     * Only one test case can be expanded at a time, but user still can click on the active test case to collapse it.
     */
    useEffect(() => {
        if (isActive) {
            setIsCollapsed(false);
        }
    }, [isActive]);

    const isExpanded = isActive && !isCollapsed;

    const onExpandedChange = useCallback(() => {
        if (isActive) {
            setIsCollapsed((prev) => !prev);
        } else {
            dispatch(changeActiveTestCase(testCase.id));
        }
    }, [dispatch, isActive, testCase.id]);

    return (
        <Expandable
            componentId={`test-case-${testCase.id}`}
            expandableTitle={<TestCaseTitle testCase={testCase} testCaseAssertionResult={testCaseAssertionResult} />}
            expanded={isExpanded}
            onChange={onExpandedChange}
            summarySx={{ px: 0.5, minHeight: "20px", "& .MuiAccordionSummary-content": { margin: "4px", overflow: "hidden" } }}
            detailsSx={{ p: 0 }}
        >
            <TestCaseSwitchMode value={mode} onChange={setMode} />
            {mode === "results" && <Results testCaseAssertionResult={testCaseAssertionResult} testCase={testCase} />}
            {mode === "definitions" && <Definitions />}
            <Divider sx={{ mt: 1.5 }} />
            <Footer testCaseAssertionResult={testCaseAssertionResult} />
        </Expandable>
    );
};

interface TestCaseTitleProps {
    testCase: TestCase;
    testCaseAssertionResult: TestCaseAssertionResult | undefined;
}

const TestCaseTitle = ({ testCase, testCaseAssertionResult }: TestCaseTitleProps) => {
    const { t } = useTranslation();
    const allResults = testCaseAssertionResult?.status === "loaded" ? Object.values(testCaseAssertionResult.results).flat() : [];
    const isLoading = testCaseAssertionResult?.status === "loading";
    const dispatch = useAppDispatch();

    const { runTest } = useRunTestScenario();

    const handleRun = useCallback(
        (e: React.MouseEvent) => {
            e.stopPropagation();
            dispatch(changeActiveTestCase(testCase.id));
            runTest(testCase);
        },
        [dispatch, runTest, testCase],
    );

    return (
        <Box
            display={"flex"}
            alignItems={"center"}
            gap={0.75}
            minWidth={0}
            width={"100%"}
            sx={{
                "& .action-slot": { opacity: 0, transition: "opacity 0.15s" },
                "&:hover .action-slot": { opacity: 1 },
            }}
        >
            <Typography variant={"body1"} noWrap sx={{ overflow: "hidden", textOverflow: "ellipsis" }}>
                {testCase.name}
            </Typography>
            <AssertionResultsBadge assertionResults={allResults} />
            {isLoading ? (
                <CircularProgress size={14} />
            ) : (
                <InfoTooltip variant={"hover"} title={t("testCases.run", "Run test")} enterDelay={500}>
                    <SvgIcon fontSize={"small"} className={"action-slot"} onClick={handleRun}>
                        <TestingIcon />
                    </SvgIcon>
                </InfoTooltip>
            )}
        </Box>
    );
};
