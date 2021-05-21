import {defaultToolbarsConfig} from "../../components/toolbarSettings/defaultToolbarsConfig"
import {RootState} from "../index"
import {ToolbarsSide, ToolbarsState, ToolbarsStates} from "../toolbars"
import {createSelector} from "reselect"

const getToolbarsState = (state: RootState): ToolbarsStates => state.toolbars || {}
export const getToolbars = createSelector(getToolbarsState, (t): ToolbarsState => {
  return t[`#${t.currentConfigId}`] || {}
})
export const getToolbarsInitData = createSelector(getToolbars, t => t.initData || [])
export const getPositions = createSelector(getToolbars, t => t.positions || {})

export const getNodeToolbox = createSelector(getToolbars, t => t.nodeToolbox)
export const getOpenedNodeGroups = createSelector(getNodeToolbox, t => t?.opened || {})

const getCollapsed = createSelector(getToolbars, t => t.collapsed)

export const getIsCollapsed = (id: string) => (state: RootState) => id && getCollapsed(state)[id]
export const getOrderForPosition = (side: ToolbarsSide) => (state: RootState) => getPositions(state)[side] || []

export const getToolbarsConfig = () => defaultToolbarsConfig

export const isLeftPanelOpened = createSelector(getToolbars, ({panels}) => panels.left)
export const isRightPanelOpened = createSelector(getToolbars, ({panels}) => panels.right)
