import { NodeType } from "../../../types";
import { Stack, styled, Typography } from "@mui/material";
import React from "react";
import { SearchHighlighter } from "../creator/SearchHighlighter";
import { ComponentIcon } from "../creator/ComponentIcon";

type Props = {
    node: NodeType;
    highlights: string[];
    fields: string[];
};

const NodeIcon = styled(ComponentIcon)({
    minWidth: "1.5em",
    maxWidth: "1.5em",
    minHeight: "1.5em",
    maxHeight: "1.5em",
});

export function FoundNode({ node, highlights, fields }: Props) {
    return (
        <Stack spacing={0.5} padding={0.5}>
            <Typography variant="body1" component={Stack} direction="row" spacing={0.5} whiteSpace="normal">
                <NodeIcon node={node} sx={{}} />
                <SearchHighlighter highlights={highlights}>{node.id}</SearchHighlighter>
            </Typography>
            <Typography variant="caption" paddingX={0.5} color={(t) => t.palette.text.disabled} whiteSpace="normal">
                {fields.join(", ")}
            </Typography>
        </Stack>
    );
}
