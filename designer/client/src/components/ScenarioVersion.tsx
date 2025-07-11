import { Button, styled } from "@mui/material";
import React from "react";
import { useDispatch, useSelector } from "react-redux";

import { updateSearchQuery } from "../actions/nk/scenarioActivities";
import { getProcessVersionId, getRunningVersion, isCurrentVersionDeployed, isLatestProcessVersion } from "../reducers/selectors/graph";

const Span = styled("span")({});

export function RunningVersion() {
    const runningVersion = useSelector(getRunningVersion);
    const currentVersionDeployed = useSelector(isCurrentVersionDeployed);
    const dispatch = useDispatch();

    if (!runningVersion) return null;

    return (
        <Button
            onClick={() => dispatch(updateSearchQuery(`scenarioVersion:running version`))}
            sx={{ color: currentVersionDeployed ? "inherit" : (theme) => theme.palette.warning.main }}
            variant="text"
        >
            {/* eslint-disable-next-line i18next/no-literal-string */}v{runningVersion}
        </Button>
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
