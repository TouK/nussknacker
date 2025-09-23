import { createSelector } from "reselect";

import { ButtonsVariant } from "../../components/toolbarComponents/toolbarButtons/ToolbarButtons";
import { BuiltinButtonTypes } from "../../components/toolbarSettings/buttons/buttonsMap";
import type { ToolbarsConfig } from "../../components/toolbarSettings/types";
import type { WithId } from "../../types/common";
import type { RootState } from "../index";
import type { ToolbarsState, ToolbarsStates } from "../toolbars";
import { ToolbarsSide } from "../toolbars";
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

export const getToolbarsConfig = createSelector(getSettings, (settings) => {
    return appendDefaultToolbars(settings?.processToolbarsConfiguration);
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
