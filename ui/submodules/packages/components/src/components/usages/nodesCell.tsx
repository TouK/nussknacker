import { ExternalLink, nodeHref, useFilterContext } from "../../common";
import React from "react";
import { OpenInBrowser as LinkIcon } from "@mui/icons-material";
import { Chip } from "@mui/material";
import { GridRenderCellParams } from "@mui/x-data-grid";
import { TruncateWrapper } from "../utils";
import { UsagesFiltersModel } from "./usagesFiltersModel";

export const NodesCell = (props: GridRenderCellParams): JSX.Element => {
    const { value, row } = props;
    const { getFilter } = useFilterContext<UsagesFiltersModel>();
    const filter = getFilter("TEXT");

    const elements = value.map((node) => {
        return (
            <Chip
                size="small"
                component={ExternalLink}
                href={nodeHref(row.id, node)}
                target="_blank"
                rel="noopener"
                tabIndex={0}
                key={node}
                label={node}
                color={!filter || node.toString().includes(filter) ? "primary" : "default"}
                icon={<LinkIcon />}
            />
        );
    });
    return <TruncateWrapper {...props}>{elements}</TruncateWrapper>;
};
