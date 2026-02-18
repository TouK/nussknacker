import { Box } from "@mui/material";
import React from "react";

import { getTestAssertionResults } from "../../../reducers/selectors/testing";
import { useAppSelector } from "../../../store/storeHelpers";
import { Expandable } from "../../common/Expandable";
import { ToolbarWrapper } from "../../toolbarComponents/toolbarWrapper/ToolbarWrapper";
import { AssertionResult } from "./assertionResult/assertionResult";
import { AssertionResultTitle } from "./assertionResult/assertionResultTitle";
import { AssertionResultsHeader } from "./assertionResultsHeader";

const AssertionResults = () => {
    const testAssertionResults = useAppSelector(getTestAssertionResults);
    return (
        <ToolbarWrapper id={"assertion-results-panel"} title={"Assertions result"}>
            <Box py={1}>
                <AssertionResultsHeader />
                {Object.keys(testAssertionResults).map((nodeId) => (
                    <Expandable
                        key={nodeId}
                        expandableTitle={<AssertionResultTitle title={nodeId} assertionResults={testAssertionResults[nodeId]} />}
                        componentId={"node-name"}
                        detailsSx={{ pl: 2, pr: 1, py: 0 }}
                        summarySx={{ minHeight: "20px", "& .MuiAccordionSummary-content": { margin: "4px" } }}
                    >
                        <AssertionResult nodeId={nodeId} />
                    </Expandable>
                ))}
            </Box>
        </ToolbarWrapper>
    );
};

export default AssertionResults;
