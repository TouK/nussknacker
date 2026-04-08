import { Box, Button, Typography } from "@mui/material";
import React, { useMemo } from "react";
import { useTranslation } from "react-i18next";

import type { NodeAssertionResults } from "../../../http/resultsWithCountsDto";
import type { TestCase } from "../../../reducers/graph/testCase";
import { getNodeAssertionResults } from "../../../reducers/graph/testing";
import type { TestCaseResult } from "../../../reducers/graph/testing";
import type { NodeType } from "../../../types/node";
import { Expandable } from "../../common/Expandable";
import { InfoTooltip } from "../../graph/node-modal/editors/InfoTooltip/InfoTooltip";
import { useTestingScenarioEnabled } from "../../modals/TestingDataRecords/useTestingScenarioEnabled";
import { useRunTestScenario } from "../test/useRunTestScenario";
import { OpenNodeTestingDetails } from "./assertionResultsForNode/assertionResult/openNodeTestingDetails";
import { AssertionResultsForNode } from "./assertionResultsForNode/assertionResultsForNode";
import { AssertionResultsForNodeTitle } from "./assertionResultsForNode/assertionResultsForNodeTitle";
import { useScenarioNodeOrder } from "./useScenarioNodeOrder";

interface Props {
    testCase: TestCase;
    testCaseResult: TestCaseResult | undefined;
}

export const Results = ({ testCaseResult, testCase }: Props) => {
    const testingScenarioEnabled = useTestingScenarioEnabled({ disabled: false });

    const { runTest } = useRunTestScenario();

    const { nodes, sortByScenarioOrder } = useScenarioNodeOrder();

    const isLoading = testCaseResult?.status === "loading";
    const nodeAssertionResults = getNodeAssertionResults(testCaseResult) ?? null;

    const sortedNodeIds = useMemo(
        () => sortByScenarioOrder(Object.keys(nodeAssertionResults ?? {})),
        [nodeAssertionResults, sortByScenarioOrder],
    );

    if (!nodeAssertionResults || sortedNodeIds.length === 0) {
        return (
            <NoResults
                onRun={() => runTest(testCase)}
                testCaseName={testCase.name}
                isRunTestButtonDisabled={isLoading}
                isRunTestButtonVisible={testingScenarioEnabled}
            />
        );
    }

    return <ResultsContent sortedNodeIds={sortedNodeIds} nodeAssertionResults={nodeAssertionResults} scenarioGraphNodes={nodes} />;
};

const NoResults = ({
    onRun,
    testCaseName,
    isRunTestButtonDisabled,
    isRunTestButtonVisible,
}: {
    onRun: () => void;
    testCaseName: string;
    isRunTestButtonDisabled: boolean;
    isRunTestButtonVisible: boolean;
}) => {
    const { t } = useTranslation();

    return (
        <Box px={1.5} py={1}>
            <Typography variant="body2" color="text.secondary">
                {t("testCases.results.noResults", "No results yet.")}{" "}
                {isRunTestButtonVisible && (
                    <Button
                        variant="text"
                        size="small"
                        onClick={onRun}
                        disabled={isRunTestButtonDisabled}
                        title={t("testCases.results.run", "Run {{name}}", { name: testCaseName })}
                        sx={{
                            fontSize: "inherit",
                            fontWeight: "inherit",
                            p: 0,
                            minWidth: 0,
                            maxWidth: "15em",
                            verticalAlign: "baseline",
                            textTransform: "lowercase",
                            display: "inline-block",
                            overflow: "hidden",
                            textOverflow: "ellipsis",
                            whiteSpace: "nowrap",
                        }}
                    >
                        {t("testCases.results.run", "Run {{name}}", { name: testCaseName })}
                    </Button>
                )}
            </Typography>
        </Box>
    );
};

interface ResultsContentProps {
    sortedNodeIds: string[];
    nodeAssertionResults: NodeAssertionResults;
    scenarioGraphNodes: NodeType[];
}

const ResultsContent = ({ sortedNodeIds, nodeAssertionResults, scenarioGraphNodes }: ResultsContentProps) => {
    const { t } = useTranslation();

    return (
        <>
            {sortedNodeIds.map((nodeId) => {
                const node = scenarioGraphNodes.find((node) => node.id === nodeId);

                return (
                    <Expandable
                        key={nodeId}
                        expandableTitle={
                            <AssertionResultsForNodeTitle
                                title={node?.name}
                                assertionResults={nodeAssertionResults[nodeId]}
                                action={
                                    node ? (
                                        <InfoTooltip
                                            variant={"hover"}
                                            title={t("testCases.run", "Open node testing details")}
                                            enterDelay={500}
                                        >
                                            <OpenNodeTestingDetails node={node} />
                                        </InfoTooltip>
                                    ) : undefined
                                }
                                node={node}
                            />
                        }
                        componentId={nodeId}
                        detailsSx={{ pl: 2, pr: 1, py: 0 }}
                        summarySx={{ px: 1.25, minHeight: "20px", "& .MuiAccordionSummary-content": { margin: "4px", overflow: "hidden" } }}
                    >
                        <AssertionResultsForNode nodeId={nodeId} />
                    </Expandable>
                );
            })}
        </>
    );
};
