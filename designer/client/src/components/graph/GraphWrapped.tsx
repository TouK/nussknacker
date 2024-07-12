import React, { forwardRef } from "react";
import { useWindows } from "../../windowManager";
import { Graph } from "./Graph";
import { useSelector } from "react-redux";
import { getUserSettings } from "../../reducers/selectors/userSettings";
import { getProcessCategory, getSelectionState, isPristine } from "../../reducers/selectors/graph";
import { getLoggedUser, getProcessDefinitionData } from "../../reducers/selectors/settings";
import { GraphProps } from "./types";
import { useTheme } from "@mui/material";
import { useEventTracking } from "../../containers/event-tracking";
import { GraphStyledWrapper } from "./graphStyledWrapper";

// Graph wrapped to make partial (for now) refactor to TS and hooks
export default forwardRef<Graph, GraphProps>(function GraphWrapped(props, forwardedRef): JSX.Element {
    const { openNodeWindow } = useWindows();
    const userSettings = useSelector(getUserSettings);
    const pristine = useSelector(isPristine);
    const processCategory = useSelector(getProcessCategory);
    const loggedUser = useSelector(getLoggedUser);
    const processDefinitionData = useSelector(getProcessDefinitionData);
    const selectionState = useSelector(getSelectionState);
    const theme = useTheme();
    const { trackEvent } = useEventTracking();

    return (
        <GraphStyledWrapper>
            <Graph
                {...props}
                ref={forwardedRef}
                userSettings={userSettings}
                showModalNodeDetails={openNodeWindow}
                isPristine={pristine}
                processCategory={processCategory}
                loggedUser={loggedUser}
                processDefinitionData={processDefinitionData}
                selectionState={selectionState}
                theme={theme}
                handleStatisticsEvent={trackEvent}
            />
        </GraphStyledWrapper>
    );
});
