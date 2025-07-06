import { styled } from "@mui/material";
import React from "react";
import { useSelector } from "react-redux";

import { getProcessVersionId, getRunningVersion, isCurrentVersionDeployed, isLatestProcessVersion } from "../reducers/selectors/graph";

const Span = styled("span")({});

export function RunningVersion() {
    const runningVersion = useSelector(getRunningVersion);
    const currentVersionDeployed = useSelector(isCurrentVersionDeployed);
    if (!runningVersion) return null;

    return (
        // eslint-disable-next-line i18next/no-literal-string
        <Span sx={(theme) => ({ color: currentVersionDeployed ? null : theme.palette.warning.main })}>v{runningVersion}</Span>
    );
}

export function ScenarioVersion() {
    const currentVersionId = useSelector(getProcessVersionId);
    const isLatestVersion = useSelector(isLatestProcessVersion);
    if (isLatestVersion || !currentVersionId) return null;

    return (
        // eslint-disable-next-line i18next/no-literal-string
        <Span sx={(theme) => ({ color: theme.palette.primary.main })}>v{currentVersionId}</Span>
    );
}
