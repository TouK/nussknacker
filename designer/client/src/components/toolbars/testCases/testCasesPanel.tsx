import { Box } from "@mui/material";
import React from "react";

import { getTestCases } from "../../../reducers/selectors/testCases";
import { useAppSelector } from "../../../store/storeHelpers";
import { ToolbarWrapper } from "../../toolbarComponents/toolbarWrapper/ToolbarWrapper";
import { TestCaseExpandable } from "./testCase";

const TestCasesPanel = () => {
    const testCases = useAppSelector(getTestCases);

    return (
        <ToolbarWrapper id={"test-cases-panel"} title={"Test cases"}>
            <Box mt={1} mb={0.5}>
                {testCases.map((testCase) => (
                    <TestCaseExpandable key={testCase.id} testCase={testCase} />
                ))}
            </Box>
        </ToolbarWrapper>
    );
};

export default TestCasesPanel;
