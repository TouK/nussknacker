import { createSelector } from "reselect";

import { getValidationErrors } from "../../../../common/ProcessUtilsAsSelectors";
import type { RootState } from "../../../../reducers";
import { getCapabilities } from "../../../../reducers/selectors/other";
import type { NodeId } from "../../../../types";

export const getNodeErrors = createSelector(
    getValidationErrors,
    (state: RootState, nodeId: NodeId) => nodeId,
    (_getValidationErrors, nodeId) => {
        return _getValidationErrors?.invalidNodes[nodeId] || [];
    },
);

export const getPropertiesErrors = createSelector(
    getValidationErrors,
    (_getValidationErrors) => _getValidationErrors?.processPropertiesErrors || [],
);

export const getReadOnly = createSelector(
    (state: RootState, fromProps?: boolean) => fromProps,
    (state: RootState) => getCapabilities(state),
    (fromProps, capabilities) => fromProps || !capabilities.editFrontend,
);
