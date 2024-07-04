import { createSelector } from "reselect";
import { getToolbars } from "./toolbars";
import { Panels } from "../panel";

export const panelsState = createSelector(getToolbars, (t) => t.panels || ({} as Panels));

export const isLeftPanelOpened = createSelector(panelsState, (panels) => panels?.LEFT);
export const isRightPanelOpened = createSelector(panelsState, (panels) => panels?.RIGHT);
