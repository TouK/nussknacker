import { Chip, Stack, styled, useTheme } from "@mui/material";
import type { SxProps } from "@mui/system";
import { blend } from "@mui/system";
import React from "react";

import { getScenarioGraph } from "../../../../reducers/selectors/graph";
import { useAppSelector } from "../../../../store/storeHelpers";
import NodeUtils from "../../NodeUtils";
import { Count } from "./Count";
import { useFilterContext } from "./FilterContext";

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

    return (id: string): SxProps => {
        if (id === null) {
            return {
                background: theme.palette.background.default,
                color: blend(theme.palette.background.default, theme.palette.text.secondary, 0.75),
            };
        }
        const { type } = NodeUtils.getNodeById(id, scenarioGraph);
        const nodeColor = theme.palette.custom.getNodeStyles(type).fill;
        return {
            background: blend(theme.palette.common.black, nodeColor, 0.15),
            color: nodeColor,
            "&.MuiChip-clickable": {
                "&:hover, &:focus": {
                    background: blend(theme.palette.common.black, nodeColor, 0.3),
                },
            },
        };
    };
};

const Arrow = () => <span>→</span>;

export function CountsForNodes({ nodes, reversed }: { nodes: NodeCount[]; reversed?: boolean }) {
    const nodeColors = useNodeColors();
    const { onToggle, filter } = useFilterContext();
    return (
        <Stack spacing={0.75} useFlexGap direction="row" sx={{ flexWrap: "wrap" }}>
            {nodes
                .filter(({ id, count }) => (id ? true : count))
                .map(({ id, count }) => (
                    <Path
                        key={id}
                        onClick={() => onToggle(id)}
                        label={
                            <Stack direction={reversed ? "row-reverse" : "row"} spacing={0.5} sx={{ alignItems: "center" }}>
                                {typeof count === "number" ? <Count>{count}</Count> : null}
                                <Arrow />
                                <span>{id || NULL_OUTPUT_NAME}</span>
                            </Stack>
                        }
                        sx={{
                            ...nodeColors(id),
                            opacity: count > 0 ? null : 0.75,
                            outline: filter.includes(id) ? "1px solid" : null,
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
