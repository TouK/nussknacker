import { isEqual } from "lodash";
import { createSelectorCreator, lruMemoize as defaultMemoize } from "reselect";

import type { RootState } from "../../../../reducers";

const createDeepEqualSelector = createSelectorCreator(defaultMemoize, isEqual);

export const getNodesDetails = createDeepEqualSelector(
    (state: RootState) => state?.nodeDetails || {},
    (nodeDetails) => nodeDetails,
);

export const getPropertiesDetails = createDeepEqualSelector(getNodesDetails, (nodeDetails) => {
    return nodeDetails[".properties"];
});
