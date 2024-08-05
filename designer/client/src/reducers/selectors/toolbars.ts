import { createSelector } from "reselect";
import { defaultToolbarsConfig } from "../../components/toolbarSettings/defaultToolbarsConfig";
import { RootState } from "../index";
import { ToolbarsSide, ToolbarsState, ToolbarsStates } from "../toolbars";
import { isArchived, isFragment } from "./graph";
import { getSettings } from "./settings";

const getToolbarsState = (state: RootState): ToolbarsStates => state.toolbars || {};
export const getToolbarsConfig = createSelector(
    getSettings,
    isFragment,
    isArchived,
    (settings, fragment, archived) => settings?.processToolbarsConfiguration || defaultToolbarsConfig(fragment, archived),
);
export const getToolbarsConfigId = createSelector(getToolbarsConfig, getToolbarsState, (c, t) => c?.id || t?.currentConfigId);
export const getToolbars = createSelector(getToolbarsState, getToolbarsConfigId, (t, id) => t?.[`#${id}`] || ({} as ToolbarsState));
export const getToolbarsInitData = createSelector(getToolbars, (t) => t.initData || []);
export const getPositions = createSelector(getToolbars, (t) => t.positions || {});

export const getComponentGroupsToolbox = createSelector(getToolbars, (t) => t.componentGroupToolbox);
export const getClosedComponentGroups = createSelector(getComponentGroupsToolbox, (t) => t?.closed || {});

const getCollapsed = createSelector(getToolbars, (t) => t.collapsed);

export const getIsCollapsed = createSelector(getCollapsed, (collapsed) => (id: string) => !!collapsed[id]);
export const getOrderForPosition = (side: ToolbarsSide) => (state: RootState) => getPositions(state)[side] || [];
