import { ExternalLink, Highlight, nodeHref } from "../../common";
import React, { memo, useCallback, useMemo } from "react";
import { OpenInBrowser as LinkIcon } from "@mui/icons-material";
import { Chip } from "@mui/material";
import { TruncateWrapper } from "../utils";
import { GridRenderCellParams } from "@mui/x-data-grid";

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

    const sorted = useMemo(() => value.map((node) => [countMatches(node), node]).sort(([a], [b]) => b - a), [countMatches, value]);

    const elements = sorted.map(([match, node]) => (
        <NodeChip key={node} icon={icon} node={node} filterText={filterText} rowId={id} isMatched={match > 0} />
    ));
    return <TruncateWrapper {...props}>{elements}</TruncateWrapper>;
};

const NodeChip = memo(function NodeChip({
    rowId,
    node,
    filterText,
    isMatched,
    icon,
}: {
    rowId: string;
    node: string;
    filterText: string;
    isMatched: boolean;
    icon: React.ReactElement;
}) {
    return (
        <Chip
            size="small"
            component={ExternalLink}
            href={nodeHref(rowId, node)}
            target="_blank"
            rel="noopener"
            tabIndex={0}
            label={isMatched ? <Highlight value={node} filterText={filterText} /> : node}
            color={isMatched ? "primary" : "default"}
            variant={isMatched ? "outlined" : "filled"}
            icon={icon}
        />
    );
});
