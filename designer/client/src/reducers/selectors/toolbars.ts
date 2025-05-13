import { createSelector } from "reselect";

import { ButtonsVariant } from "../../components/toolbarComponents/toolbarButtons";
import { BuiltinButtonTypes } from "../../components/toolbarSettings/buttons";
import { fallbackToolbarsConfig } from "../../components/toolbarSettings/fallbackToolbarsConfig";
import type { ToolbarsConfig } from "../../components/toolbarSettings/types";
import type { WithId } from "../../types/common";
import type { RootState } from "../index";
import type { ToolbarsState, ToolbarsStates } from "../toolbars";
import { ToolbarsSide } from "../toolbars";
import { isArchived, isFragment } from "./graph";
import { getSettings } from "./settings";

const getToolbarsState = (state: RootState): ToolbarsStates => state.toolbars || {};

const appendDefaultToolbars = ({ topRight = [], bottomRight = [], ...toolbars }: WithId<ToolbarsConfig>): WithId<ToolbarsConfig> => ({
    ...toolbars,
    [ToolbarsSide.RightTop]: [{ id: "survey-panel" }, ...topRight],
    [ToolbarsSide.RightBottom]: [
        ...bottomRight,
        {
            id: "user-settings-panel",
            buttonsVariant: ButtonsVariant.horizontal,
            buttons: [{ type: BuiltinButtonTypes.viewReset }],
        },
    ],
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
