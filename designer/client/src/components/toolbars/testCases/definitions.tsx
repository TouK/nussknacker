import { Box, Chip, Typography } from "@mui/material";
import type { PropsWithChildren } from "react";
import React, { useCallback, useMemo } from "react";
import { useTranslation } from "react-i18next";

import { resetSelection } from "../../../actions/nk/selection";
import { replaceSearchQuery } from "../../../containers/hooks/useSearchQuery";
import { getScenario, getScenarioGraph, getSelectionState } from "../../../reducers/selectors/graph";
import { getInputDataRecords, getTestCaseAssertions, getTestCaseMocks } from "../../../reducers/selectors/testCases";
import { useAppDispatch, useAppSelector } from "../../../store/storeHelpers";
import type { NodeType } from "../../../types/node";
import { useWindows } from "../../../windowManager/useWindows";
import { useGraph } from "../../graph/GraphContext";
import { nodeFound, nodeFoundHover } from "../../graph/graphStyledWrapper";
import { ACTIVE_TAB_QUERY_KEY, NodeDetailsTab } from "../../graph/node-modal/node/NodeContent/TabsWrapper";
import { NodeIcon } from "./NodeIcon";

export const Definitions = () => {
    const { t } = useTranslation();
    const inputDataRecords = useAppSelector(getInputDataRecords);
    const mocks = useAppSelector(getTestCaseMocks);
    const assertions = useAppSelector(getTestCaseAssertions);
    const scenarioGraph = useAppSelector(getScenarioGraph);

    const nodes = useMemo(() => scenarioGraph.nodes ?? [], [scenarioGraph.nodes]);
    const findNode = useCallback((id: string) => nodes.find((n) => n.id === id), [nodes]);

    const sourceIds = useMemo(() => [...new Set(inputDataRecords.map((r) => r.sourceId))], [inputDataRecords]);
    const mockNodeIds = useMemo(() => Object.keys(mocks).filter((nodeId) => mocks[nodeId]?.expression?.expression?.trim() !== ""), [mocks]);
    const assertionNodeIds = useMemo(() => Object.keys(assertions), [assertions]);

    return (
        <Box>
            <SectionHeader count={sourceIds.length}>{t("testCases.definitions.sources", "Sources")}</SectionHeader>
            {sourceIds.length === 0 ? (
                <EmptySection>{t("testCases.definitions.noSources", "No sources defined.")}</EmptySection>
            ) : (
                <Box px={1.5} pb={1}>
                    {sourceIds.map((sourceId) => (
                        <NodeRow key={sourceId} node={findNode(sourceId)} label={sourceId} />
                    ))}
                </Box>
            )}

            <SectionHeader count={mockNodeIds.length}>{t("testCases.definitions.mocks", "Mocks")}</SectionHeader>
            {mockNodeIds.length === 0 ? (
                <EmptySection>{t("testCases.definitions.noMocks", "No mocks defined.")}</EmptySection>
            ) : (
                <Box px={1.5} pb={1}>
                    {mockNodeIds.map((nodeId) => (
                        <NodeRow key={nodeId} node={findNode(nodeId)} label={nodeId} />
                    ))}
                </Box>
            )}

            <SectionHeader count={assertionNodeIds.length}>{t("testCases.definitions.assertions", "Assertions")}</SectionHeader>
            {assertionNodeIds.length === 0 ? (
                <EmptySection>{t("testCases.definitions.noAssertions", "No assertions defined.")}</EmptySection>
            ) : (
                <Box px={1.5} pb={1}>
                    {assertionNodeIds.map((nodeId) => (
                        <NodeRow
                            key={nodeId}
                            node={findNode(nodeId)}
                            label={nodeId}
                            badge={t("testCases.definitions.testAssertions", {
                                count: assertions[nodeId]?.length,
                                defaultValue_one: "{{count}} assertion",
                                defaultValue_other: "{{count}} assertions",
                            })}
                        />
                    ))}
                </Box>
            )}
        </Box>
    );
};

const SectionHeader = ({ children, count }: PropsWithChildren<{ count: number }>) => {
    return (
        <Box display="flex" alignItems="center" gap={0.75} pl={2} pb={0.5}>
            <Typography variant={"subtitle2"} color={"text.secondary"}>
                {children}
            </Typography>
            <Chip label={count} size="small" sx={{ height: 16, fontSize: "0.65rem", "& .MuiChip-label": { px: 0.75 } }} />
        </Box>
    );
};

const EmptySection = ({ children }: PropsWithChildren) => (
    <Box px={2} pb={1}>
        <Typography variant="body2" color="text.secondary">
            {children}
        </Typography>
    </Box>
);
const NodeRow = ({ node, label, badge }: { node: NodeType | undefined; label: string; badge?: string }) => {
    const { onMouseEnter: handleMouseEnter, onMouseLeave: handleMouseLeave } = useNodeHover(node?.id);
    const handleClick = useNodeSelectOrOpen(node);

    const labelToShow = label ?? node?.id ?? "";

    return (
        <Box
            display="flex"
            alignItems="center"
            gap={0.75}
            py={0.25}
            onClick={handleClick}
            onMouseEnter={handleMouseEnter}
            onMouseLeave={handleMouseLeave}
            sx={{ cursor: node ? "pointer" : "default" }}
        >
            {node && <NodeIcon node={node} />}

            <Box display="flex" alignItems="center" gap={0.5}>
                <Typography variant="body2" component="span">
                    {labelToShow}
                </Typography>

                {badge && (
                    <Typography variant="overline" color="text.disabled" sx={{ ml: 0.5 }}>
                        {badge}
                    </Typography>
                )}
            </Box>
        </Box>
    );
};

function useNodeHover(nodeId: string | undefined) {
    const graphGetter = useGraph();

    const onMouseEnter = useCallback(() => {
        if (!nodeId) return;
        const graph = graphGetter();
        if (!graph) return;
        graph.highlightNode(nodeId, nodeFound);
        graph.highlightNode(nodeId, nodeFoundHover);
    }, [nodeId, graphGetter]);

    const onMouseLeave = useCallback(() => {
        if (!nodeId) return;
        const graph = graphGetter();
        if (!graph) return;
        graph.unhighlightNode(nodeId, nodeFound);
        graph.unhighlightNode(nodeId, nodeFoundHover);
    }, [nodeId, graphGetter]);

    return { onMouseEnter, onMouseLeave };
}

function useNodeSelectOrOpen(node: NodeType | undefined) {
    const graphGetter = useGraph();
    const { openNodeWindow } = useWindows();
    const scenario = useAppSelector(getScenario);
    const dispatch = useAppDispatch();
    const selectionState = useAppSelector(getSelectionState);

    const isNodeSelected = useCallback((node: NodeType) => selectionState.includes(node.id), [selectionState]);

    return useCallback(() => {
        if (!node) return;
        if (isNodeSelected(node)) {
            replaceSearchQuery((current) => ({ ...current, [ACTIVE_TAB_QUERY_KEY]: [NodeDetailsTab.testing] }));
            openNodeWindow(node, scenario);
        } else {
            const graph = graphGetter();
            graph.fitToNode(node.id);
            dispatch(resetSelection(node.id));
        }
    }, [dispatch, graphGetter, isNodeSelected, node, openNodeWindow, scenario]);
}
