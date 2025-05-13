import { Chip, Stack, styled } from "@mui/material";
import React from "react";

const Path = styled(Chip)(({ theme }) => ({
    background: theme.palette.background.default,
    borderRadius: theme.spacing(0.8),
    height: "2em",
}));

const Count = styled("span")(({ theme }) => ({
    fontWeight: "bold",
}));

type NodeCount = {
    id: string;
    count: number;
};

export function CountsForNodes({ nodes, input }: { nodes: NodeCount[]; input?: boolean }) {
    return (
        <Stack spacing={0.5} useFlexGap direction="row" sx={{ flexWrap: "wrap" }}>
            {nodes.map(({ id, count }) => (
                <Path
                    key={id}
                    label={
                        <Stack direction={input ? "row-reverse" : "row"} spacing={0.5}>
                            {count ? (
                                <Count
                                    sx={(theme) => ({
                                        color: count >= 0 ? theme.palette.success.main : theme.palette.error.main,
                                    })}
                                >
                                    {Math.abs(count)}
                                </Count>
                            ) : null}
                            <span>→</span>
                            <span>{id}</span>
                        </Stack>
                    }
                    sx={(theme) => {
                        return {
                            color: count > 0 ? "inherit" : theme.palette.action.disabled,
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
