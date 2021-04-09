import {createSelector} from "reselect"
import {RootState} from "../index"
import {ToolbarsState} from "../toolbars"

export const getToolbars = (state: RootState): ToolbarsState => state.toolbars

const getCollapsed = createSelector(getToolbars, t => t.collapsed)

export const getIsCollapsed = (id: string) => (state: RootState) => id && getCollapsed(state)[id]
