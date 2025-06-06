import { createSelector } from "reselect";

import type { RootState } from "../index";

export const getWindowsIdMapping = (state: RootState) => state.nodeWindowIdMap;
export const getHasOpenedNodeWindows = createSelector(getWindowsIdMapping, (idsMap) => Object.values(idsMap).some(Boolean));
