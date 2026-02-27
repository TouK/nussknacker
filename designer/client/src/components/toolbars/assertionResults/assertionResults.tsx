import { Box } from "@mui/material";
import React, { useMemo } from "react";

import { getScenarioGraph } from "../../../reducers/selectors/graph";
import { getTestAssertionResults } from "../../../reducers/selectors/testing";
import { useAppSelector } from "../../../store/storeHelpers";
import { Expandable } from "../../common/Expandable";
import { ToolbarWrapper } from "../../toolbarComponents/toolbarWrapper/ToolbarWrapper";
import { AssertionResult } from "./assertionResult/assertionResult";
import { AssertionResultTitle } from "./assertionResult/assertionResultTitle";
import { OpenNodeTestingDetails } from "./assertionResult/openNodeTestingDetails";
import { AssertionResultsHeader } from "./assertionResultsHeader";

const AssertionResults = () => {
    const testAssertionResults = useAppSelector(getTestAssertionResults);
    const scenarioGraph = useAppSelector(getScenarioGraph);

    const nodeOrderMap = useMemo(
        () =>
            scenarioGraph.nodes.reduce((acc, node, index) => {
                acc[node.id] = index;
                return acc;
            }, {} as Record<string, number>),
        [scenarioGraph.nodes],
    );

    const sortedNodeIds = useMemo(
        () =>
            Object.keys(testAssertionResults).sort((nodeIdA, nodeIdB) => {
                const orderA = nodeOrderMap[nodeIdA] ?? Infinity;
                const orderB = nodeOrderMap[nodeIdB] ?? Infinity;
                return orderA - orderB;
            }),
        [testAssertionResults, nodeOrderMap],
    );
    return (
        <ToolbarWrapper id={"assertion-results-panel"} title={"Assertion results"}>
            <Box py={1}>
                <AssertionResultsHeader />
                {sortedNodeIds.map((nodeId) => {
                    const node = scenarioGraph.nodes.find((node) => node.id === nodeId);

                    return (
                        <Expandable
                            key={nodeId}
                            expandableTitle={
                                <AssertionResultTitle
                                    title={nodeId}
                                    assertionResults={testAssertionResults[nodeId]}
                                    action={node ? <OpenNodeTestingDetails node={node} /> : undefined}
                                />
                            }
                            componentId={nodeId}
                            detailsSx={{ pl: 2, pr: 1, py: 0 }}
                            summarySx={{ minHeight: "20px", "& .MuiAccordionSummary-content": { margin: "4px" } }}
                        >
                            <AssertionResult nodeId={nodeId} />
                        </Expandable>
                    );
                })}
            </Box>
        </ToolbarWrapper>
    );
};

export default AssertionResults;
