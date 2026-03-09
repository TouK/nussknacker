import { Box, Typography } from "@mui/material";
import type { ReactNode } from "react";
import React from "react";

import type { TestAssertionResult } from "../../../../http/resultsWithCountsDto";
import type { NodeType } from "../../../../types/node";
import { NodeIcon } from "../NodeIcon";
import { AssertionResultsBadge } from "./AssertionResultsBadge";

interface Props {
    title: string;
    assertionResults: TestAssertionResult[];
    action: ReactNode;
    node?: NodeType;
}

export const AssertionResultsForNodeTitle = ({ title, assertionResults, action, node }: Props) => {
    return (
        <Box
            display={"flex"}
            alignItems={"center"}
            gap={0.75}
            minWidth={0}
            width={"100%"}
            sx={{
                "&:not(:hover) .action-slot": { visibility: "hidden" },
            }}
        >
            {node && <NodeIcon node={node} />}
            <Typography variant={"body2"} noWrap sx={{ overflow: "hidden", textOverflow: "ellipsis" }}>
                {title}
            </Typography>
            <AssertionResultsBadge assertionResults={assertionResults} />
            <Box className="action-slot" display={"flex"} alignItems={"center"}>
                {action}
            </Box>
        </Box>
    );
};
