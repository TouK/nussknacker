import { ExternalLink, Highlight, nodeHref, fragmentNodeHref } from "../../common";
import React, { memo, useCallback, useMemo } from "react";
import { OpenInBrowser as LinkIcon } from "@mui/icons-material";
import { Chip } from "@mui/material";
import { TruncateWrapper } from "../utils";
import { GridRenderCellParams } from "@mui/x-data-grid";
import {NodeUsageData} from "nussknackerUi/HttpService";

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

    const sorted = useMemo(() => value.map((node: NodeUsageData) => [countMatches(node.nodeId), node.nodeId, node.fragmentNodeId, node.type]).sort(([a], [b]) => b - a), [countMatches, value]);

    const elements = sorted.map(([match, nodeId, fragmentNodeId, nodeType]) => (
        <NodeChip key={nodeId} icon={icon} nodeId={nodeId} fragmentNodeId={fragmentNodeId} nodeType={nodeType} filterText={filterText} rowId={id} matched={filterText ? match : -1} />
    ));
    return <TruncateWrapper {...props}>{elements}</TruncateWrapper>;
};

const NodeChip = memo(function NodeChip({
    rowId,
    nodeId,
    fragmentNodeId,
    nodeType,
    filterText,
    matched,
    icon,
}: {
    rowId: string;
    nodeId: string;
    fragmentNodeId?: string;
    nodeType: string;
    filterText: string;
    matched: number;
    icon: React.ReactElement;
}) {
    return (
        nodeType === "FragmentUsageData" ?
            (<Chip
                size="small"
                component={ExternalLink}
                href={fragmentNodeHref(rowId, fragmentNodeId, nodeId)}
                tabIndex={0}
                label={matched > 0 ? <Highlight value={nodeId} filterText={filterText} /> : fragmentNodeId + " / " + nodeId}
                color={matched !== 0 ? "secondary" : "default"}
                variant={matched > 0 ? "outlined" : "filled"}
                icon={icon}
        />) :
            (<Chip
                size="small"
                component={ExternalLink}
                href={nodeHref(rowId, nodeId)}
                tabIndex={0}
                label={matched > 0 ? <Highlight value={nodeId} filterText={filterText} /> : nodeId}
                color={matched !== 0 ? "primary" : "default"}
                variant={matched > 0 ? "outlined" : "filled"}
                icon={icon}
            />)
    );
});
