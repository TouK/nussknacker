import { Insights, Lock, NearbyError as Warning } from "@mui/icons-material";
import { alpha, Stack, styled, Typography } from "@mui/material";
import Moment from "moment/moment";
import React, { useMemo } from "react";
import { useSelector } from "react-redux";

import TestingIcon from "../../../../assets/img/toolbarButtons/test.svg";
import { VisibleDataType } from "../../../../reducers/graph";
import { getVisibleDataType } from "../../../../reducers/selectors/getLiveData";
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
    const visibleDataType = useSelector(getVisibleDataType);
    const label = useMemo(() => context.id.replace(new RegExp(`^${scenarioName}-`), ""), [context.id, scenarioName]);

    return (
        <Stack spacing={1} sx={{ flex: 1 }}>
            <Stack spacing={1} direction="row" sx={{ alignItems: "center" }}>
                <Insights
                    component={context.error ? Warning : locked ? Lock : visibleDataType === VisibleDataType.test ? TestingIcon : null}
                    sx={(theme) => ({
                        width: ".9em",
                        height: ".9em",
                        opacity: locked || context.error ? 1 : 0.5,
                        color: context.error ? theme.palette.error.main : null,
                        filter: context.error ? getShadow(alpha(theme.palette.error.dark, 0.2), 20, 10) : null,
                    })}
                />
                <Typography noWrap sx={{ fontFamily: '"Roboto Mono", Monaco, monospace' }}>
                    {Moment(context.timestamp).format("HH:mm:ss.SSS") || label}
                </Typography>
                <Typography noWrap sx={{ fontFamily: '"Roboto Mono", Monaco, monospace', opacity: 0.5 }}>
                    {Moment(context.timestamp).format("YYYY-MM-DD") || label}
                </Typography>
            </Stack>
            {showNodes ? <RelatedNodes nodeIds={context.nodeIds} reversed={reversed} /> : null}
        </Stack>
    );
}
