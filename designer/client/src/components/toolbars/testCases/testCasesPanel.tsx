import { Box } from "@mui/material";
import React, { useState } from "react";

import { getTestAssertionResults } from "../../../reducers/selectors/testing";
import { useAppSelector } from "../../../store/storeHelpers";
import { ToolbarWrapper } from "../../toolbarComponents/toolbarWrapper/ToolbarWrapper";
import { Definitions } from "./definitions";
import { Results } from "./results";
import { TestCaseHeader } from "./testCaseHeader";
import type { TestCaseMode } from "./TestCaseSwitchMode";
import { TestCaseSwitchMode } from "./TestCaseSwitchMode";

const TestCasesPanel = () => {
    const testAssertionResults = useAppSelector(getTestAssertionResults);
    const [mode, setMode] = useState<TestCaseMode>("results");

    return (
        <ToolbarWrapper id={"test-cases-panel"} title={"Test cases"}>
            <Box py={1}>
                <TestCaseHeader />
                <TestCaseSwitchMode value={mode} onChange={setMode} />
                {mode === "results" && <Results testAssertionResults={testAssertionResults} />}
                {mode === "definitions" && <Definitions />}
            </Box>
        </ToolbarWrapper>
    );
};

export default TestCasesPanel;
