import { createSelector } from "reselect";

import ProcessUtils from "../../../../common/ProcessUtils";
import type { RootState } from "../../../../reducers";
import { getScenario } from "../../../../reducers/selectors/graph";
import { getCapabilities } from "../../../../reducers/selectors/other";
import type { NodeId } from "../../../../types/node";
import { getPropertiesDetails } from "../NodeDetailsContent/getNodeDetails";

export const getNodeErrors = createSelector(
    getScenario,
    (state: RootState, nodeId: NodeId) => nodeId,
    (process, nodeId) => {
        return ProcessUtils.getValidationErrors(process)?.invalidNodes[nodeId] || [];
    },
);

const getPropertiesErrors = createSelector(getScenario, (process) => {
    return ProcessUtils.getValidationErrors(process)?.processPropertiesErrors || [];
});

export const getCurrentPropertiesErrors = createSelector(getPropertiesErrors, getPropertiesDetails, (propertiesErrors, nodeDetails) => {
    return nodeDetails?.validationErrors || propertiesErrors;
});

export const getReadOnly = createSelector(
    (state: RootState, fromProps?: boolean) => fromProps,
    (state: RootState) => getCapabilities(state),
    (fromProps, capabilities) => fromProps || !capabilities.editFrontend,
);
