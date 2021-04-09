import {createSelector} from "reselect"
import {RootState} from "../index"
import {ToolboxState} from "../toolbox"

export const getToolbox = (state: RootState): ToolboxState => state.toolbox

export const getOpenedNodeGroups = createSelector(getToolbox, t => t?.opened || {})
