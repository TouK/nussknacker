import { Stack, Typography } from "@mui/material";
import React from "react";

import { CountsForNodes } from "./CountsForNodes";
import type { ResultsWithId } from "./InputOutputContext";
import type { ValuesContextTreeProps } from "./VariableContextTree";

export function VariableContextTreeHeader({
    direction,
    transitionNodesIds,
}: {
    direction: ValuesContextTreeProps["direction"];
    transitionNodesIds: ResultsWithId[];
}) {
    return (
        <Stack
            spacing={1}
            sx={{
                position: "sticky",
                top: 0,
                zIndex: 0,
                padding: 1,
            }}
        >
            <Typography variant="subtitle1">{direction === "input" ? "Input variables" : "Output variables"}</Typography>
            <CountsForNodes
                nodes={transitionNodesIds.map(({ id, totalCount, results }) => ({
                    id,
                    count: totalCount ?? results?.length,
                }))}
                input={direction === "input"}
            />
        </Stack>
    );
}
