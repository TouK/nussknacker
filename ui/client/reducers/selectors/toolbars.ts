import {defaultToolbarsConfig} from "../../components/toolbarSettings/defaultToolbarsConfig"
import {RootState} from "../index"
import {ToolbarsSide, ToolbarsStates} from "../toolbars"
import {createSelector} from "reselect"
import {getSettings} from "./settings"
import {isArchived, isSubprocess} from "./graph"

const getToolbarsState = (state: RootState): ToolbarsStates => state.toolbars || {}
export const getToolbarsConfig = createSelector(getSettings, isSubprocess, isArchived, (settings, subprocess, archived) => settings?.processToolbarsConfiguration || defaultToolbarsConfig(subprocess, archived))
export const getToolbarsConfigId = createSelector(getToolbarsConfig, getToolbarsState, (c, t) => c?.id || t?.currentConfigId)
export const getToolbars = createSelector(getToolbarsState, getToolbarsConfigId, (t,id) => t?.[`#${id}`] || {})
export const getToolbarsInitData = createSelector(getToolbars, t => t.initData || [])
export const getPositions = createSelector(getToolbars, t => t.positions || {})

export const getComponentGroupsToolbox = createSelector(getToolbars, t => t.nodeToolbox)
export const getOpenedComponentGroups = createSelector(getComponentGroupsToolbox, t => t?.opened || {})

const getCollapsed = createSelector(getToolbars, t => t.collapsed)

export const getIsCollapsed = createSelector(getCollapsed, collapsed => (id: string) => !!collapsed[id])
export const getOrderForPosition = (side: ToolbarsSide) => (state: RootState) => getPositions(state)[side] || []

export const isLeftPanelOpened = createSelector(getToolbars, ({panels}) => panels?.left)
export const isRightPanelOpened = createSelector(getToolbars, ({panels}) => panels?.right)
