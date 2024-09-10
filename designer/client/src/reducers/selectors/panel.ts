import { createSelector } from "reselect";
import { defaultState } from "../panel";
import { getToolbars } from "./toolbars";

export const panelsState = createSelector(getToolbars, (t) => ({ ...defaultState, ...t.panels }));
