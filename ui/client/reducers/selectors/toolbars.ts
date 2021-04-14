import {defaultToolbarsConfig} from "../../components/toolbarSettings/defaultToolbarsConfig"
import {RootState} from "../index"
import {ToolbarsSide, ToolbarsState} from "../toolbars"
import {createSelector} from "reselect"

export const getToolbars = (state: RootState): ToolbarsState => state.toolbars
export const getPositions = createSelector(getToolbars, t => t.positions || {})

export const getNodeToolbox = createSelector(getToolbars, t => t.nodeToolbox)
export const getOpenedNodeGroups = createSelector(getNodeToolbox, t => t?.opened || {})

const getCollapsed = createSelector(getToolbars, t => t.collapsed)

export const getIsCollapsed = (id: string) => (state: RootState) => id && getCollapsed(state)[id]
export const getOrderForPosition = (side: ToolbarsSide) => (state: RootState) => getPositions(state)[side] || []

export const getToolbarsConfig = () => defaultToolbarsConfig
