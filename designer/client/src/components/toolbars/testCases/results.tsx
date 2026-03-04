import { Box, Button, Typography } from "@mui/material";
import React, { useCallback, useMemo } from "react";
import { useTranslation } from "react-i18next";

import { testScenarioWithTestCase } from "../../../actions/nk/testingActions";
import { useUserSettings } from "../../../common/useUserSettings";
import type { TestAssertionResults } from "../../../http/resultsWithCountsDto";
import { getScenarioGraph } from "../../../reducers/selectors/graph";
import { getTestCase } from "../../../reducers/selectors/testCases";
import { getTestResultsLoading } from "../../../reducers/selectors/testing";
import { useAppDispatch, useAppSelector } from "../../../store/storeHelpers";
import type { NodeType } from "../../../types/node";
import { Expandable } from "../../common/Expandable";
import { OpenNodeTestingDetails } from "./assertionResultsForNode/assertionResult/openNodeTestingDetails";
import { AssertionResultsForNode } from "./assertionResultsForNode/assertionResultsForNode";
import { AssertionResultsForNodeTitle } from "./assertionResultsForNode/assertionResultsForNodeTitle";

interface Props {
    testAssertionResults: TestAssertionResults;
}

export const Results = ({ testAssertionResults }: Props) => {
    const scenarioGraph = useAppSelector(getScenarioGraph);
    const testCase = useAppSelector(getTestCase);
    const dispatch = useAppDispatch();
    const [showMockFieldOnEnrichers] = useUserSettings("node.showMockFieldOnEnrichers");
    const isLoading = useAppSelector(getTestResultsLoading);

    const handleRun = useCallback(() => {
        dispatch(testScenarioWithTestCase(testCase, showMockFieldOnEnrichers));
    }, [dispatch, testCase, showMockFieldOnEnrichers]);

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

    if (sortedNodeIds.length === 0) {
        return <NoResults onRun={handleRun} testCaseName={testCase.name} isRunTestButtonDisabled={isLoading} />;
    }

    return (
        <ResultsContent
            sortedNodeIds={sortedNodeIds}
            testAssertionResults={testAssertionResults}
            scenarioGraphNodes={scenarioGraph.nodes}
        />
    );
};

const NoResults = ({
    onRun,
    testCaseName,
    isRunTestButtonDisabled,
}: {
    onRun: () => void;
    testCaseName: string;
    isRunTestButtonDisabled: boolean;
}) => {
    const { t } = useTranslation();

    return (
        <Box px={1.5} py={1}>
            <Typography variant="body2" color="text.secondary">
                {t("testCases.results.noResults", "No results yet.")}{" "}
                <Button
                    variant="text"
                    size="small"
                    onClick={onRun}
                    disabled={isRunTestButtonDisabled}
                    sx={{
                        fontSize: "inherit",
                        fontWeight: "inherit",
                        p: 0,
                        minWidth: 0,
                        verticalAlign: "baseline",
                        textTransform: "lowercase",
                    }}
                >
                    {t("testCases.results.run", "Run {{name}}", { name: testCaseName })}
                </Button>
            </Typography>
        </Box>
    );
};

interface ResultsContentProps {
    sortedNodeIds: string[];
    testAssertionResults: TestAssertionResults;
    scenarioGraphNodes: NodeType[];
}

const ResultsContent = ({ sortedNodeIds, testAssertionResults, scenarioGraphNodes }: ResultsContentProps) => (
    <>
        {sortedNodeIds.map((nodeId) => {
            const node = scenarioGraphNodes.find((node) => node.id === nodeId);

            return (
                <Expandable
                    key={nodeId}
                    expandableTitle={
                        <AssertionResultsForNodeTitle
                            title={nodeId}
                            assertionResults={testAssertionResults[nodeId]}
                            action={node ? <OpenNodeTestingDetails node={node} /> : undefined}
                        />
                    }
                    componentId={nodeId}
                    detailsSx={{ pl: 2, pr: 1, py: 0 }}
                    summarySx={{ minHeight: "20px", "& .MuiAccordionSummary-content": { margin: "4px" } }}
                >
                    <AssertionResultsForNode nodeId={nodeId} />
                </Expandable>
            );
        })}
    </>
);
