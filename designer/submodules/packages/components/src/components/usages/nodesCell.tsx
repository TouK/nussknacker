import { OpenInBrowser as LinkIcon } from "@mui/icons-material";
import { Chip, styled } from "@mui/material";
import type { GridRenderCellParams } from "@mui/x-data-grid";
import type { NodeUsageData } from "nussknackerUi/HttpService";
import React, { memo, useCallback, useMemo } from "react";

import { createFilterRules } from "../../common/filters/filterRules";
import { Highlight } from "../../common/highlight";
import { ExternalLink } from "../../common/parentNavigationProvider";
import { fragmentNodeHref, nodeHref } from "../../common/scenarioHref";
import { TruncateWrapper } from "../../common/utils/truncateWrapper";
import type { UsageWithStatus } from "../useComponentsQuery";
import type { UsagesFiltersModel } from "./usagesFiltersModel";
import { nodeFilter } from "./usagesTable";
import { useUsagesFilterContext } from "./useUsagesFilterContext";

const icon = <LinkIcon />;

export function getNodeName({
    fragmentNodeId,
    fragmentNodeName,
    nodeId,
    nodeName,
}: Pick<NodeUsageData, "fragmentNodeId" | "fragmentNodeName" | "nodeId" | "nodeName">): string {
    const directNode = nodeId || nodeName || "";
    const fragmentNode = fragmentNodeId || fragmentNodeName;
    return fragmentNode ? `${directNode} ❮${fragmentNode}❯` : directNode;
}

export function getNodeSearchText({ fragmentNodeId, fragmentNodeName, nodeId, nodeName, ...rest }: NodeUsageData): string {
    const explicitFields = [nodeId, nodeName, fragmentNodeId, fragmentNodeName]
        .filter((value): value is string => Boolean(value && value.trim()))
        .join(" ");

    const rawPayload = JSON.stringify(rest).toLowerCase();
    return [explicitFields, rawPayload].filter(Boolean).join(" ").toLowerCase();
}

const nodesFilterRules = createFilterRules<NodeUsageData, UsagesFiltersModel>({
    USAGE_TYPE: (row, value) => !value?.length || [].concat(value).some((f) => nodeFilter(f, row)),
});

export const NodesCell = ({
    filterText,
    ...props
}: GridRenderCellParams<NodeUsageData[], UsageWithStatus> & {
    filterText: string;
}): JSX.Element => {
    const {
        value,
        rowNode: { id },
    } = props;
    const filterSegments = useMemo(() => filterText?.toLowerCase().toString().trim().split(/\s/) || [], [filterText]);

    const countMatches = useCallback(
        (node: NodeUsageData) => filterSegments.filter((segment) => getNodeSearchText(node).includes(segment)).length,
        [filterSegments],
    );

    const { getFilter } = useUsagesFilterContext();
    const filters = useMemo(
        () =>
            nodesFilterRules.map(
                ({ key, rule }) =>
                    (row) =>
                        rule(row, getFilter(key)),
            ),
        [getFilter],
    );

    const filtered = useMemo(() => (Array.isArray(value) ? value.filter((row) => filters.every((f) => f(row))) : []), [value, filters]);

    const sorted = useMemo(
        () =>
            filtered
                .map((node: NodeUsageData): [number, NodeUsageData] => [countMatches(node), node])
                .sort((a, b) => {
                    if (a[0] !== b[0]) {
                        return b[0] - a[0];
                    }
                    return a[1].nodeId.localeCompare(b[1].nodeId);
                }),
        [countMatches, filtered],
    );

    const elements = sorted.map(([match, node]) => (
        <NodeChip
            key={getNodeName(node)}
            icon={icon}
            node={node}
            filterText={filterText}
            rowId={id.toString()}
            matched={filterText ? match : -1}
        />
    ));
    return <TruncateWrapper>{elements}</TruncateWrapper>;
};

const HighlightNode = styled(Highlight)({
    span: {
        opacity: 0.8,
    },
    strong: {
        color: "inherit",
        fontSize: "110%",
    },
});

const NodeChip = memo(function NodeChip({
    rowId,
    node,
    filterText,
    matched,
    icon,
}: {
    rowId: string;
    node: NodeUsageData;
    filterText: string;
    matched: number;
    icon: React.ReactElement;
}) {
    const { fragmentNodeId, nodeId }: NodeUsageData = node;
    const nodeLabel: string = getNodeName(node);
    const nodeLink: string = fragmentNodeId ? fragmentNodeHref(rowId, fragmentNodeId, nodeId) : nodeHref(rowId, nodeId);
    return (
        <Chip
            size="small"
            component={ExternalLink}
            href={nodeLink}
            tabIndex={0}
            label={matched > 0 ? <HighlightNode value={nodeLabel} filterText={filterText} /> : nodeLabel}
            color={matched !== 0 ? (fragmentNodeId ? "secondary" : "primary") : "default"}
            icon={icon}
            clickable
        />
    );
});
