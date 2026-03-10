import { Box } from "@mui/material";
import React from "react";

import { getTestCase } from "../../../reducers/selectors/testCases";
import { useAppSelector } from "../../../store/storeHelpers";
import { ToolbarWrapper } from "../../toolbarComponents/toolbarWrapper/ToolbarWrapper";
import { TestCaseExpandable } from "./testCase";

const TestCasesPanel = () => {
    const testCase = useAppSelector(getTestCase);

    return (
        <ToolbarWrapper id={"test-cases-panel"} title={"Test cases"}>
            <Box my={1}>
                <TestCaseExpandable testCase={testCase} />
            </Box>
        </ToolbarWrapper>
    );
};

export default TestCasesPanel;
