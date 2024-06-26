import { createSelector } from "reselect";
import { getToolbars } from "./toolbars";

export const panelsState = createSelector(getToolbars, (t) => t.panels);

export const isLeftPanelOpened = createSelector(panelsState, (panels) => panels?.LEFT);
export const isRightPanelOpened = createSelector(panelsState, (panels) => panels?.RIGHT);
