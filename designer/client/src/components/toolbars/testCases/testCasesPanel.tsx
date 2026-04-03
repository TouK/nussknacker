import { Box } from "@mui/material";
import React from "react";

import { getTestCases, hasMultipleTestCases } from "../../../reducers/selectors/testCases";
import { getFilteredTestAssertionResults } from "../../../reducers/selectors/testing";
import { useAppSelector } from "../../../store/storeHelpers";
import { ToolbarWrapper } from "../../toolbarComponents/toolbarWrapper/ToolbarWrapper";
import { RunAllTestsButton } from "./RunAllTestsButton";
import { TestCaseExpandable } from "./testCase";

const TestCasesPanel = () => {
    const testCases = useAppSelector(getTestCases);
    const testCaseAssertionResults = useAppSelector(getFilteredTestAssertionResults);
    const showRunAll = useAppSelector(hasMultipleTestCases);

    return (
        <ToolbarWrapper id={"test-cases-panel"} title={"Test cases"} actions={showRunAll ? <RunAllTestsButton /> : undefined}>
            <Box mt={1} mb={0.5}>
                {testCases.map((testCase) => (
                    <TestCaseExpandable key={testCase.id} testCase={testCase} testCaseResult={testCaseAssertionResults?.[testCase.id]} />
                ))}
            </Box>
        </ToolbarWrapper>
    );
};

export default TestCasesPanel;
