import {createSelector} from "reselect"
import {getGraph} from "./graph"

export const getExpandedGroups = createSelector(getGraph, g => g.expandedGroups)
