import { useTheme } from "@mui/material";
import React, { forwardRef, useRef } from "react";
import { useTranslation } from "react-i18next";
import { useForkRef } from "rooks";

import { useEventTracking } from "../../containers/event-tracking/use-event-tracking";
import { getProcessDefinitionData } from "../../reducers/selectors/getProcessDefinitionData";
import { getProcessCategory, getSelectionState, isPristine } from "../../reducers/selectors/graph";
import { getLoggedUser } from "../../reducers/selectors/settings";
import { getUi } from "../../reducers/selectors/ui";
import { getUserSettings } from "../../reducers/selectors/userSettings";
import { useAppSelector } from "../../store/storeHelpers";
import { useWindows } from "../../windowManager/useWindows";
import { Graph } from "./Graph";
import { GraphStyledWrapper } from "./graphStyledWrapper";
import { NodeDescriptionPopover } from "./NodeDescriptionPopover";
import type { GraphProps } from "./types";
import { usePortMagnetToggle } from "./usePortMagnetToggle";

// Graph wrapped to make partial (for now) refactor to TS and hooks
export default forwardRef<Graph, GraphProps>(function GraphWrapped(props, forwardedRef): React.JSX.Element {
    const { openNodeWindow } = useWindows();
    const userSettings = useAppSelector(getUserSettings);
    const pristine = useAppSelector(isPristine);
    const processCategory = useAppSelector(getProcessCategory);
    const loggedUser = useAppSelector(getLoggedUser);
    const processDefinitionData = useAppSelector(getProcessDefinitionData);
    const selectionState = useAppSelector(getSelectionState);
    const theme = useTheme();
    const translation = useTranslation();
    const { trackEvent } = useEventTracking();
    const graphRef = useRef<Graph>(null);
    const ref = useForkRef(graphRef, forwardedRef);
    const areAdvancedStickyNotesEnabled = userSettings["node.advancedStickyNotes"];
    const settings = useAppSelector(getUserSettings);
    const isSnowing = settings["scenario.isItSnowing"];

    usePortMagnetToggle(graphRef);

    return (
        <>
            <GraphStyledWrapper areAdvancedStickyNotesEnabled={areAdvancedStickyNotesEnabled}>
                <Graph
                    {...props}
                    ref={ref}
                    userSettings={userSettings}
                    showModalNodeDetails={openNodeWindow}
                    isPristine={pristine}
                    processCategory={processCategory}
                    loggedUser={loggedUser}
                    processDefinitionData={processDefinitionData}
                    selectionState={selectionState}
                    theme={theme}
                    translation={translation}
                    handleStatisticsEvent={trackEvent}
                    isItSnowing={isSnowing}
                />
            </GraphStyledWrapper>
            <NodeDescriptionPopover graphRef={graphRef} />
        </>
    );
});
