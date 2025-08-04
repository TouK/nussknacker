import { isEqual } from "lodash";
import { createSelectorCreator, lruMemoize as defaultMemoize } from "reselect";

import type { RootState } from "../../../../reducers";

const createDeepEqualSelector = createSelectorCreator(defaultMemoize, isEqual);

const getNodesDetails = (state: RootState) => state.nodeDetails;
export const getNodeDetails = createDeepEqualSelector(getNodesDetails, (nodeDetails) => (nodeId: string) => nodeDetails[nodeId]);
