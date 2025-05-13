import { NearbyError as Warning } from "@mui/icons-material";
import { alpha, Stack, Typography } from "@mui/material";
import React, { useMemo } from "react";
import { useSelector } from "react-redux";

import { getProcessName } from "../../../../reducers/selectors/graph";
import { getShadow } from "../../graphStyledWrapper";
import { NextNodes } from "./CountsForNodes";
import type { VariableContextType } from "./VariableContextTree";

export function ContextTitle({ context, showNodes }: { context: VariableContextType; showNodes?: boolean }) {
    const scenarioName = useSelector(getProcessName);
    const label = useMemo(() => context.id.replace(new RegExp(`^${scenarioName}-`), ""), [context.id, scenarioName]);

    return (
        <Stack spacing={1}>
            <Stack spacing={1} direction="row">
                <Typography>{label}</Typography>
                {context.error ? (
                    <Warning
                        sx={(theme) => ({
                            color: theme.palette.error.main,
                            filter: getShadow(alpha(theme.palette.error.dark, 0.2), 20, 10),
                        })}
                    />
                ) : null}
            </Stack>
            {showNodes ? <NextNodes nodeIds={context.nodeIds} /> : null}
        </Stack>
    );
}
