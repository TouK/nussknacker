import { createSelector } from "reselect";
import { fallbackToolbarsConfig } from "../../components/toolbarSettings/fallbackToolbarsConfig";
import { ToolbarsConfig } from "../../components/toolbarSettings/types";
import { WithId } from "../../types/common";
import { RootState } from "../index";
import { ToolbarsSide, ToolbarsState, ToolbarsStates } from "../toolbars";
import { isArchived, isFragment } from "./graph";
import { getSettings } from "./settings";

const getToolbarsState = (state: RootState): ToolbarsStates => state.toolbars || {};

const appendDefaultToolbars = ({ topRight = [], bottomRight = [], ...toolbars }: WithId<ToolbarsConfig>): WithId<ToolbarsConfig> => ({
    ...toolbars,
    [ToolbarsSide.TopRight]: [{ id: "survey-panel" }, ...topRight],
    [ToolbarsSide.BottomRight]: [...bottomRight, { id: "user-settings-panel" }],
});

export const getToolbarsConfig = createSelector(getSettings, isFragment, isArchived, (settings, fragment, archived) => {
    const toolbars = settings?.processToolbarsConfiguration || fallbackToolbarsConfig(fragment, archived);
    return appendDefaultToolbars(toolbars);
});

export const getToolbarsConfigId = createSelector(getToolbarsConfig, getToolbarsState, (c, t) => c?.id || t?.currentConfigId);
export const getToolbars = createSelector(getToolbarsState, getToolbarsConfigId, (t, id) => t?.[`#${id}`] || ({} as ToolbarsState));
export const getToolbarsInitData = createSelector(getToolbars, (t) => t.initData || []);
export const getPositions = createSelector(getToolbars, (t) => t.positions || {});

export const getComponentGroupsToolbox = createSelector(getToolbars, (t) => t.componentGroupToolbox);
export const getClosedComponentGroups = createSelector(getComponentGroupsToolbox, (t) => t?.closed || {});

const getCollapsed = createSelector(getToolbars, (t) => t.collapsed);

export const getIsCollapsed = createSelector(getCollapsed, (collapsed) => (id: string) => !!collapsed[id]);
export const getOrderForPosition = (side: ToolbarsSide) => (state: RootState) => getPositions(state)[side] || [];
