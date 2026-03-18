import { Box } from "@mui/material";
import React from "react";

import { getActiveTestCase } from "../../../reducers/selectors/testCases";
import { useAppSelector } from "../../../store/storeHelpers";
import { ToolbarWrapper } from "../../toolbarComponents/toolbarWrapper/ToolbarWrapper";
import { TestCaseExpandable } from "./testCase";

const TestCasesPanel = () => {
    const testCase = useAppSelector(getActiveTestCase);

    return (
        <ToolbarWrapper id={"test-cases-panel"} title={"Test cases"}>
            <Box mt={1} mb={0.5}>
                <TestCaseExpandable testCase={testCase} />
            </Box>
        </ToolbarWrapper>
    );
};

export default TestCasesPanel;
