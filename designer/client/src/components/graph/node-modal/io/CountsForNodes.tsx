import { Chip, Stack, styled, useTheme } from "@mui/material";
import { blend } from "@mui/system";
import type { CSSProperties } from "react";
import React from "react";

import { getScenarioGraph } from "../../../../reducers/selectors/graph";
import { useAppSelector } from "../../../../store/storeHelpers";
import NodeUtils from "../../NodeUtils";
import { Count } from "./Count";

const NULL_OUTPUT_NAME = "Void";

const Path = styled(Chip)(({ theme }) => ({
    borderRadius: theme.spacing(0.8),
    height: "2em",
    userSelect: "none",
}));

type NodeCount = {
    id: string;
    count: number;
};

const useNodeColors = () => {
    const theme = useTheme();
    const scenarioGraph = useAppSelector(getScenarioGraph);

    return (id: string): CSSProperties => {
        if (id === null) {
            return {
                background: theme.palette.background.default,
                color: blend(theme.palette.background.default, theme.palette.text.secondary, 0.75),
            };
        }
        const { type } = NodeUtils.getNodeById(id, scenarioGraph);
        const nodeColor = theme.palette.custom.getNodeStyles(type).fill;
        const nodeBackground = blend(theme.palette.common.black, nodeColor, 0.15);
        return {
            background: nodeBackground,
            color: nodeColor,
        };
    };
};

const Arrow = () => <span>→</span>;

export function CountsForNodes({ nodes, input }: { nodes: NodeCount[]; input?: boolean }) {
    const nodeColors = useNodeColors();
    return (
        <Stack spacing={0.5} useFlexGap direction="row" sx={{ flexWrap: "wrap" }}>
            {nodes
                .filter(({ id, count }) => (id ? true : count))
                .map(({ id, count }) => (
                    <Path
                        key={id}
                        label={
                            <Stack direction={input ? "row-reverse" : "row"} spacing={0.5} sx={{ alignItems: "center" }}>
                                {typeof count === "number" ? <Count>{count}</Count> : null}
                                <Arrow />
                                <span>{id || NULL_OUTPUT_NAME}</span>
                            </Stack>
                        }
                        sx={{
                            ...nodeColors(id),
                            opacity: count > 0 ? null : 0.75,
                        }}
                    />
                ))}
        </Stack>
    );
}

export function RelatedNodes({ nodeIds, reversed }: { nodeIds: string[]; reversed?: boolean }) {
    const nodeColors = useNodeColors();
    return (
        <Stack spacing={0.5} direction="row">
            {nodeIds.map((id) => (
                <Path
                    key={id}
                    label={
                        <Stack direction={reversed ? "row-reverse" : "row"} spacing={0.5} sx={{ alignItems: "center" }}>
                            <Arrow />
                            <span>{id || NULL_OUTPUT_NAME}</span>
                        </Stack>
                    }
                    size="small"
                    sx={nodeColors(id)}
                />
            ))}
        </Stack>
    );
}
