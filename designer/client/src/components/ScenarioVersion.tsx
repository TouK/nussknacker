import { Link, styled, Typography } from "@mui/material";
import React, { useCallback } from "react";
import { useSelector } from "react-redux";

import { updateSearchQuery } from "../actions/nk/scenarioActivities";
import { getProcessVersionId, getRunningVersion, isCurrentVersionDeployed, isLatestProcessVersion } from "../reducers/selectors/graph";
import { useAppDispatch } from "../store/configureStore";
import { predefinedQueries } from "./toolbars/activities/useActivitiesSearch";

const Span = styled("span")({});

export function RunningVersion() {
    const runningVersion = useSelector(getRunningVersion);
    const currentVersionDeployed = useSelector(isCurrentVersionDeployed);
    const dispatch = useAppDispatch();

    const handleGoToRunningVersion = useCallback(() => {
        const element = document.querySelector('[data-rfd-drag-handle-draggable-id="activities-panel"]');
        if (element) {
            element.scrollIntoView({ behavior: "smooth", block: "start" });
        } else {
            console.log("Element not found");
        }

        dispatch(updateSearchQuery(predefinedQueries.runningVersionQuery));
    }, [dispatch]);

    if (!runningVersion) return null;

    return (
        <Typography
            data-testid="runningVersion"
            component={Link}
            onClick={handleGoToRunningVersion}
            sx={{
                color: (theme) => (currentVersionDeployed ? theme.palette.primary.main : theme.palette.warning.main),
                cursor: "pointer",
            }}
            variant="body2"
        >
            {/* eslint-disable-next-line i18next/no-literal-string */}v{runningVersion}
        </Typography>
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
