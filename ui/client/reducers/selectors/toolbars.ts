import {createSelector} from "reselect"
import {RootState} from "../index"
import {ToolbarsSide, ToolbarsState} from "../toolbars"

export const getToolbars = (state: RootState): ToolbarsState => state.toolbars
export const getPositions = createSelector(getToolbars, t => t.positions || {})

const getCollapsed = createSelector(getToolbars, t => t.collapsed)

export const getIsCollapsed = (id: string) => (state: RootState) => id && getCollapsed(state)[id]
export const getOrderForPosition = (side: ToolbarsSide) => (state: RootState) => getPositions(state)[side] || []
