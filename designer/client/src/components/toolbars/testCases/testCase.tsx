import { Box, Typography } from "@mui/material";
import React, { useState } from "react";

import type { TestCase } from "../../../reducers/graph/testCase";
import { getTestAssertionResults } from "../../../reducers/selectors/testing";
import { useAppSelector } from "../../../store/storeHelpers";
import { Expandable } from "../../common/Expandable";
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
            expandableTitle={<TestCaseTitle name={testCase.name} />}
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
    name: string;
}

const TestCaseTitle = ({ name }: TestCaseTitleProps) => {
    const testAssertionResults = useAppSelector(getTestAssertionResults);
    const allResults = Object.values(testAssertionResults).flat();

    return (
        <Box display={"flex"} alignItems={"center"} gap={0.75} minWidth={0} width={"100%"}>
            <Typography variant={"body1"} noWrap sx={{ overflow: "hidden", textOverflow: "ellipsis" }}>
                {name}
            </Typography>
            <AssertionResultsBadge assertionResults={allResults} />
        </Box>
    );
};
