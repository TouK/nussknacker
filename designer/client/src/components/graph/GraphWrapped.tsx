import { useTheme } from "@mui/material";
import React, { forwardRef, useRef } from "react";
import { useTranslation } from "react-i18next";
import { useDispatch, useSelector } from "react-redux";
import { useForkRef } from "rooks";
import { useEventTracking } from "../../containers/event-tracking";
import { getProcessCategory, getSelectionState, isPristine } from "../../reducers/selectors/graph";
import { getLoggedUser, getProcessDefinitionData } from "../../reducers/selectors/settings";
import { getUserSettings } from "../../reducers/selectors/userSettings";
import { useWindows } from "../../windowManager";
import { Graph } from "./Graph";
import { GraphStyledWrapper } from "./graphStyledWrapper";
import { NodeDescriptionPopover } from "./NodeDescriptionPopover";
import { GraphProps } from "./types";
import { bindActionCreators } from "redux";
import * as NotificationActions from "../../actions/notificationActions";

// Graph wrapped to make partial (for now) refactor to TS and hooks
export default forwardRef<Graph, GraphProps>(function GraphWrapped(props, forwardedRef): JSX.Element {
    const { openNodeWindow, confirm } = useWindows();
    const dispatch = useDispatch();
    const userSettings = useSelector(getUserSettings);
    const pristine = useSelector(isPristine);
    const processCategory = useSelector(getProcessCategory);
    const loggedUser = useSelector(getLoggedUser);
    const processDefinitionData = useSelector(getProcessDefinitionData);
    const selectionState = useSelector(getSelectionState);
    const theme = useTheme();
    const translation = useTranslation();
    const { trackEvent } = useEventTracking();
    const notifications = bindActionCreators(NotificationActions, dispatch);
    const graphRef = useRef<Graph>();
    const ref = useForkRef(graphRef, forwardedRef);

    return (
        <>
            <GraphStyledWrapper>
                <Graph
                    {...props}
                    ref={ref}
                    userSettings={userSettings}
                    showModalNodeDetails={openNodeWindow}
                    showConfirmationWindow={confirm}
                    isPristine={pristine}
                    processCategory={processCategory}
                    loggedUser={loggedUser}
                    processDefinitionData={processDefinitionData}
                    selectionState={selectionState}
                    theme={theme}
                    translation={translation}
                    handleStatisticsEvent={trackEvent}
                    notifications={notifications}
                />
            </GraphStyledWrapper>
            <NodeDescriptionPopover graphRef={graphRef} />
        </>
    );
});
