import { createSelector } from "reselect";

import ProcessUtils from "../../../../common/ProcessUtils";
import type { RootState } from "../../../../reducers";
import { getScenario } from "../../../../reducers/selectors/graph";
import { getCapabilities } from "../../../../reducers/selectors/other";
import type { NodeId } from "../../../../types/node";

export const getNodeErrors = createSelector(
    getScenario,
    (state: RootState, nodeId: NodeId) => nodeId,
    (process, nodeId) => {
        return ProcessUtils.getValidationErrors(process)?.invalidNodes[nodeId] || [];
    },
);

export const getPropertiesErrors = createSelector(
    getScenario,
    (process) => ProcessUtils.getValidationErrors(process)?.processPropertiesErrors || [],
);

export const getReadOnly = createSelector(
    (state: RootState, fromProps?: boolean) => fromProps,
    (state: RootState) => getCapabilities(state),
    (fromProps, capabilities) => fromProps || !capabilities.editFrontend,
);
