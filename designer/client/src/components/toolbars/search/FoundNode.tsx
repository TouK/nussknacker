import { Edge, NodeType } from "../../../types";
import { ListItemText, Stack, Typography } from "@mui/material";
import React from "react";
import { SearchHighlighter } from "../creator/SearchHighlighter";

type Props = {
    node: NodeType;
    edges: Edge[];
    highlights: string[];
    names: string[];
};

export function FoundNode({ node, edges, highlights, names }: Props) {
    return (
        <ListItemText
            primary={<SearchHighlighter highlights={highlights}>{node.id}</SearchHighlighter>}
            primaryTypographyProps={{ component: "span" }}
            secondary={
                <Stack spacing={1}>
                    <SearchHighlighter highlights={highlights}>{node.additionalFields?.description}</SearchHighlighter>
                    <Stack direction="row" spacing={0.5} useFlexGap flexWrap="wrap">
                        {names.map((n) => (
                            <Typography component="span" key={n} variant="caption">
                                {n.toUpperCase()}
                            </Typography>
                        ))}
                    </Stack>
                </Stack>
            }
            secondaryTypographyProps={{ component: "span" }}
        />
    );
}
