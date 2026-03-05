import { Box, Typography } from "@mui/material";
import React, { useCallback, useMemo } from "react";
import { useTranslation } from "react-i18next";

import { getScenarioGraph } from "../../../reducers/selectors/graph";
import { getTestCaseAssertions, getTestCaseMocks, getInputDataRecords } from "../../../reducers/selectors/testCases";
import { useAppSelector } from "../../../store/storeHelpers";
import type { NodeType } from "../../../types/node";
import { SectionHeader } from "../../CommandBar/SectionHeader";
import { useGraph } from "../../graph/GraphContext";
import { nodeFound, nodeFoundHover } from "../../graph/graphStyledWrapper";
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
            <SectionHeader>{t("testCases.definitions.sources", "Sources")}</SectionHeader>
            {sourceIds.length === 0 ? (
                <Box px={1.5} pb={1}>
                    <Typography variant="body2" color="text.secondary">
                        {t("testCases.definitions.noSources", "No sources defined.")}
                    </Typography>
                </Box>
            ) : (
                <Box px={1.5} pb={1}>
                    {sourceIds.map((sourceId) => (
                        <NodeRow key={sourceId} node={findNode(sourceId)} label={sourceId} />
                    ))}
                </Box>
            )}

            <SectionHeader>{t("testCases.definitions.mocks", "Mocks")}</SectionHeader>
            {mockNodeIds.length === 0 ? (
                <Box px={1.5} pb={1}>
                    <Typography variant="body2" color="text.secondary">
                        {t("testCases.definitions.noMocks", "No mocks defined.")}
                    </Typography>
                </Box>
            ) : (
                <Box px={1.5} pb={1}>
                    {mockNodeIds.map((nodeId) => (
                        <NodeRow key={nodeId} node={findNode(nodeId)} label={nodeId} />
                    ))}
                </Box>
            )}

            <SectionHeader>{t("testCases.definitions.assertions", "Assertions")}</SectionHeader>
            {assertionNodeIds.length === 0 ? (
                <Box px={1.5} pb={1}>
                    <Typography variant="body2" color="text.secondary">
                        {t("testCases.definitions.noAssertions", "No assertions defined.")}
                    </Typography>
                </Box>
            ) : (
                <Box px={1.5} pb={1}>
                    {assertionNodeIds.map((nodeId) => (
                        <NodeRow key={nodeId} node={findNode(nodeId)} label={nodeId} />
                    ))}
                </Box>
            )}
        </Box>
    );
};

const NodeRow = ({ node }: { node: NodeType | undefined; label: string }) => {
    const { onMouseEnter: handleMouseEnter, onMouseLeave: handleMouseLeave } = useNodeHover(node?.id);

    return (
        <Box display="flex" alignItems="center" gap={0.75} py={0.25} onMouseEnter={handleMouseEnter} onMouseLeave={handleMouseLeave}>
            {node && <NodeIcon node={node} />}
            <Typography variant="body2">{node?.id ?? ""}</Typography>
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
