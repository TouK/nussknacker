import { Chip, Stack, styled } from "@mui/material";
import React from "react";

const Path = styled(Chip)(({ theme }) => ({
    borderRadius: theme.spacing(0.8),
    height: "2em",
}));

type NodeCount = {
    id: string;
    count: number;
};

export function CountsForNodes({ nodes, input }: { nodes: NodeCount[]; input?: boolean }) {
    return (
        <Stack spacing={0.5} direction="row">
            {nodes.map(({ id, count = -1 }) => (
                <Path
                    key={id}
                    label={
                        <Stack direction={input ? "row-reverse" : "row"} spacing={0.5}>
                            {count ? <strong>{Math.abs(count)}</strong> : null}
                            <span>→</span>
                            <span>{id}</span>
                        </Stack>
                    }
                    sx={(theme) => {
                        const background =
                            count > 0 ? theme.palette.primary.dark : count < 0 ? theme.palette.error.main : theme.palette.action.disabled;
                        return {
                            background,
                            color: theme.palette.getContrastText(background),
                        };
                    }}
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
