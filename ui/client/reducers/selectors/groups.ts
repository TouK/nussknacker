import {createSelector} from "reselect"
import {getUi} from "./ui"

export const getExpandedGroups = createSelector(getUi, g => g.expandedGroups)
