import { Chip, Stack, styled } from "@mui/material";
import React from "react";

const Path = styled(Chip)(({ theme }) => ({
    borderRadius: theme.spacing(0.8),
}));

type NodeCount = {
    id: string;
    count: number;
};

export function NextNodesCounts({ nodes, input }: { nodes: NodeCount[]; input?: boolean }) {
    return (
        <Stack spacing={0.5} direction="row">
            {nodes.map(({ id, count }) => (
                <Path
                    key={id}
                    label={
                        <Stack direction={input ? "row-reverse" : "row"} spacing={0.5}>
                            {count ? <strong>{count}</strong> : null}
                            <span>→</span>
                            <span>{id}</span>
                        </Stack>
                    }
                    color={count > 0 ? "primary" : count < 0 ? "error" : "default"}
                />
            ))}
        </Stack>
    );
}

export function NextNodes({ nodeIds }: { nodeIds: string[] }) {
    return (
        <Stack spacing={0.5} direction="row">
            {nodeIds.map((n) => (
                <Path key={n} label={`→ ${n}`} size="small" />
            ))}
        </Stack>
    );
}
