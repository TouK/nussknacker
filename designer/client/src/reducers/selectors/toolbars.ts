import { produce } from "immer";
import { createSelector } from "reselect";

import { BuiltinButtonTypes } from "../../components/toolbarSettings/buttons/buttonsMap";
import type { ToolbarsConfig } from "../../components/toolbarSettings/types";
import type { WithId } from "../../types/common";
import type { RootState } from "../index";
import type { ToolbarsSide, ToolbarsState, ToolbarsStates } from "../toolbars";
import { getSettings } from "./settings";
import { getUserSettings } from "./userSettings";

const getToolbarsState = (state: RootState): ToolbarsStates => state.toolbars || {};

const appendSurveyToolbar = produce((draft: WithId<ToolbarsConfig>) => {
    draft.topRight ||= [];
    draft.topRight.unshift({ id: "survey-panel" });
});

const appendAlignToolbar = produce((draft: WithId<ToolbarsConfig>) => {
    draft.topRight ||= [];
    draft.topRight.push({
        id: "align",
        buttons: [
            { type: BuiltinButtonTypes.alignHorizontalLeft },
            { type: BuiltinButtonTypes.alignHorizontalCenter },
            { type: BuiltinButtonTypes.alignHorizontalRight },
            { type: BuiltinButtonTypes.distributeHorizontal },
            { type: BuiltinButtonTypes.alignVerticalTop },
            { type: BuiltinButtonTypes.alignVerticalCenter },
            { type: BuiltinButtonTypes.alignVerticalBottom },
            { type: BuiltinButtonTypes.distributeVertical },
        ],
    });
});

export const getToolbarsConfig = createSelector([getSettings, getUserSettings], (settings, userSettings) => {
    const withSurveyToolbar = appendSurveyToolbar(settings?.processToolbarsConfiguration);
    if (userSettings["debug.scenaro.showNodeAlignToolbar"]) {
        return appendAlignToolbar(withSurveyToolbar);
    }
    return withSurveyToolbar;
});

export const getToolbarsConfigId = createSelector(getToolbarsConfig, getToolbarsState, (c, t) => c?.id || t?.currentConfigId);
export const getToolbars = createSelector(getToolbarsState, getToolbarsConfigId, (t, id) => t?.[`#${id}`] || ({} as ToolbarsState));
export const getToolbarsInitData = createSelector(getToolbars, (t) => t.initData || []);
export const getPositions = createSelector(getToolbars, (t) => t.positions || {});

export const getComponentGroupsToolbox = createSelector(getToolbars, (t) => t.componentGroupToolbox);
export const getClosedComponentGroups = createSelector(getComponentGroupsToolbox, (t) => t?.closed || {});

const getCollapsed = createSelector(getToolbars, (t) => t.collapsed);

export const getIsCollapsed = createSelector(getCollapsed, (collapsed) => (id: string) => !!collapsed[id]);

const emptyArray = [];
export const getOrderForPosition = (side: ToolbarsSide) => (state: RootState) => getPositions(state)[side] || emptyArray;
