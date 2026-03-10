import PlayArrowIcon from "@mui/icons-material/PlayArrow";
import { Box, CircularProgress, IconButton, Tooltip, Typography } from "@mui/material";
import React, { useCallback, useState } from "react";
import { useTranslation } from "react-i18next";

import { testScenarioWithTestCase } from "../../../actions/nk/testingActions";
import { useUserSettings } from "../../../common/useUserSettings";
import type { TestCase } from "../../../reducers/graph/testCase";
import { getTestAssertionResults, getTestResultsLoading } from "../../../reducers/selectors/testing";
import { useAppDispatch, useAppSelector } from "../../../store/storeHelpers";
import { Expandable } from "../../common/Expandable";
import { useTestingScenarioEnabled } from "../../modals/TestingDataRecords/useTestingScenarioEnabled";
import { AssertionResultsBadge } from "./assertionResultsForNode/AssertionResultsBadge";
import { Definitions } from "./definitions";
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
    const testingScenarioEnabled = useTestingScenarioEnabled({ disabled: undefined });
    const [showMockFieldOnEnrichers] = useUserSettings("node.showMockFieldOnEnrichers");
    const dispatch = useAppDispatch();

    const handleRun = useCallback(
        (e: React.MouseEvent) => {
            e.stopPropagation();
            dispatch(testScenarioWithTestCase(testCase, showMockFieldOnEnrichers));
        },
        [dispatch, testCase, showMockFieldOnEnrichers],
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
            <Tooltip title={t("testCases.run", "Run test")} placement={"top"}>
                <span>
                    <IconButton
                        className="action-slot"
                        size="small"
                        disabled={!testingScenarioEnabled || isLoading}
                        onClick={handleRun}
                        sx={{ p: 0.25 }}
                    >
                        {isLoading ? <CircularProgress size={14} /> : <PlayArrowIcon sx={{ fontSize: "16px" }} />}
                    </IconButton>
                </span>
            </Tooltip>
        </Box>
    );
};
