import { Lock, NearbyError as Warning } from "@mui/icons-material";
import { alpha, Stack, Typography } from "@mui/material";
import Moment from "moment/moment";
import React, { useMemo } from "react";
import { useSelector } from "react-redux";

import { getProcessName } from "../../../../reducers/selectors/graph";
import { getShadow } from "../../graphStyledWrapper";
import { RelatedNodes } from "./CountsForNodes";
import type { VariableContextType } from "./VariableContextTree";

type ContextTitleProps = {
    context: VariableContextType;
    showNodes?: boolean;
    reversed?: boolean;
    locked?: boolean;
};

export function ContextTitle({ context, showNodes, reversed, locked }: ContextTitleProps) {
    const scenarioName = useSelector(getProcessName);
    const label = useMemo(() => context.id.replace(new RegExp(`^${scenarioName}-`), ""), [context.id, scenarioName]);

    return (
        <Stack spacing={1}>
            <Stack spacing={1} direction="row">
                <Typography>{Moment(context.timestamp).format("HH:mm:ss.SSS YYYY-MM-DD") || label}</Typography>
                {locked ? <Lock /> : null}
                {context.error ? (
                    <Warning
                        sx={(theme) => ({
                            color: theme.palette.error.main,
                            filter: getShadow(alpha(theme.palette.error.dark, 0.2), 20, 10),
                        })}
                    />
                ) : null}
            </Stack>
            {showNodes ? <RelatedNodes nodeIds={context.nodeIds} reversed={reversed} /> : null}
        </Stack>
    );
}
