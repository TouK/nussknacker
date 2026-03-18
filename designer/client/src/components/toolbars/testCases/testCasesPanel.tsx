import { Box, Divider } from "@mui/material";
import React, { useEffect } from "react";

import { getTestCase } from "../../../reducers/selectors/testCases";
import { useAppSelector } from "../../../store/storeHelpers";
import { ToolbarWrapper } from "../../toolbarComponents/toolbarWrapper/ToolbarWrapper";
import { Footer } from "./footer";
import { TestCaseExpandable } from "./testCase";

const TestCasesPanel = () => {
    const testCase = useAppSelector(getTestCase);

    return (
        <ToolbarWrapper id={"test-cases-panel"} title={"Test cases"}>
            <Box mt={1} mb={0.5}>
                <TestCaseExpandable testCase={testCase} />
            </Box>
        </ToolbarWrapper>
    );
};

export default TestCasesPanel;
