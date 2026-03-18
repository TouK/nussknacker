import { Box, CircularProgress, Divider, Typography } from "@mui/material";
import SvgIcon from "@mui/material/SvgIcon/SvgIcon";
import React, { useCallback, useState } from "react";
import { useTranslation } from "react-i18next";

import TestingIcon from "../../../assets/img/toolbarButtons/test.svg";
import type { TestCase } from "../../../reducers/graph/testCase";
import { getTestAssertionResults, getTestResultsLoading } from "../../../reducers/selectors/testing";
import { useAppSelector } from "../../../store/storeHelpers";
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
}

export const TestCaseExpandable = ({ testCase }: TestCaseExpandableProps) => {
    const testAssertionResults = useAppSelector(getTestAssertionResults);
    const [mode, setMode] = useState<TestCaseMode>("results");
    const [expanded, setExpanded] = useState(true);

    return (
        <Expandable
            componentId={`test-case-${testCase.id}`}
            expandableTitle={<TestCaseTitle testCase={testCase} />}
            expanded={expanded}
            onChange={setExpanded}
            summarySx={{ px: 0.5, minHeight: "20px", "& .MuiAccordionSummary-content": { margin: "4px", overflow: "hidden" } }}
            detailsSx={{ p: 0 }}
        >
            <TestCaseSwitchMode value={mode} onChange={setMode} />
            {mode === "results" && <Results testAssertionResults={testAssertionResults} />}
            {mode === "definitions" && <Definitions />}
            <Divider sx={{ mt: 1.5 }} />
            <Footer />
        </Expandable>
    );
};

interface TestCaseTitleProps {
    testCase: TestCase;
}

const TestCaseTitle = ({ testCase }: TestCaseTitleProps) => {
    const { t } = useTranslation();
    const testAssertionResults = useAppSelector(getTestAssertionResults);
    const allResults = Object.values(testAssertionResults).flat();
    const isLoading = useAppSelector(getTestResultsLoading);

    const { runTest } = useRunTestScenario();

    const handleRun = useCallback(
        (e: React.MouseEvent) => {
            e.stopPropagation();
            runTest(testCase);
        },
        [runTest, testCase],
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
