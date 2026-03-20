import { Box } from "@mui/material";
import React from "react";

import { getTestCases } from "../../../reducers/selectors/testCases";
import { getTestAssertionResults } from "../../../reducers/selectors/testing";
import { useAppSelector } from "../../../store/storeHelpers";
import { ToolbarWrapper } from "../../toolbarComponents/toolbarWrapper/ToolbarWrapper";
import { TestCaseExpandable } from "./testCase";

const TestCasesPanel = () => {
    const testCases = useAppSelector(getTestCases);
    const testCaseAssertionResults = useAppSelector(getTestAssertionResults);

    return (
        <ToolbarWrapper id={"test-cases-panel"} title={"Test cases"}>
            <Box mt={1} mb={0.5}>
                {testCases.map((testCase) => (
                    <TestCaseExpandable
                        key={testCase.id}
                        testCase={testCase}
                        testCaseAssertionResult={testCaseAssertionResults?.[testCase.id]}
                    />
                ))}
            </Box>
        </ToolbarWrapper>
    );
};

export default TestCasesPanel;
