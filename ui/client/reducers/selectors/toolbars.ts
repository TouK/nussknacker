import {RootState} from "../index"
import {createSelector} from "reselect"
import {ToolbarsState} from "../toolbars"

export const getToolbars = (state: RootState): ToolbarsState => state.toolbars

export const getNodeToolbox = createSelector(getToolbars, t => t.nodeToolbox)
export const getOpenedNodeGroups = createSelector(getNodeToolbox, t => t?.opened || {})

const getCollapsed = createSelector(getToolbars, t => t.collapsed)

export const getIsCollapsed = (id: string) => (state: RootState) => id && getCollapsed(state)[id]
