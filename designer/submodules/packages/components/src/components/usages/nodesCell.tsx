import { ExternalLink, Highlight, nodeHref, fragmentNodeHref } from "../../common";
import React, { memo, useCallback, useMemo } from "react";
import { OpenInBrowser as LinkIcon } from "@mui/icons-material";
import { Chip } from "@mui/material";
import { TruncateWrapper } from "../utils";
import { GridRenderCellParams } from "@mui/x-data-grid";
import { NodeUsageData } from "nussknackerUi/HttpService";

const icon = <LinkIcon />;

export const NodesCell = ({ filterText, ...props }: GridRenderCellParams & { filterText: string }): JSX.Element => {
    const {
        value,
        row: { id },
    } = props;
    const filterSegments = useMemo(() => filterText?.toString().trim().split(/\s/) || [], [filterText]);

    const countMatches = useCallback(
        (node: string) => filterSegments.filter((segment) => node.toString().includes(segment)).length,
        [filterSegments],
    );

    const sorted = useMemo(
        () => value.map((node: NodeUsageData) => [countMatches(node.nodeId), node.fragmentNodeId, node.nodeId]).sort(([a], [b]) => b - a),
        [countMatches, value],
    );

    const elements = sorted.map(([match, fragmentNodeId, nodeId]) => (
        <NodeChip
            key={nodeId}
            icon={icon}
            nodeId={nodeId}
            fragmentNodeId={fragmentNodeId}
            filterText={filterText}
            rowId={id}
            matched={filterText ? match : -1}
        />
    ));
    return <TruncateWrapper {...props}>{elements}</TruncateWrapper>;
};

const NodeChip = memo(function NodeChip({
    rowId,
    nodeId,
    fragmentNodeId,
    filterText,
    matched,
    icon,
}: {
    rowId: string;
    nodeId: string;
    fragmentNodeId?: string;
    filterText: string;
    matched: number;
    icon: React.ReactElement;
}) {
    const nodeLabel: string = fragmentNodeId !== undefined ? fragmentNodeId + " / " + nodeId : nodeId;
    const nodeLink: string = fragmentNodeId !== undefined ? fragmentNodeHref(rowId, fragmentNodeId, nodeId) : nodeHref(rowId, nodeId);
    return (
        <Chip
            size="small"
            component={ExternalLink}
            href={nodeLink}
            tabIndex={0}
            label={matched > 0 ? <Highlight value={nodeId} filterText={filterText} /> : nodeLabel}
            color={matched !== 0 ? (fragmentNodeId != null ? "secondary" : "primary") : "default"}
            variant={matched > 0 ? "outlined" : "filled"}
            icon={icon}
        />
    );
});
