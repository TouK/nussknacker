import {createSelector} from "reselect"
import {getAdditionalFields} from "./graph"

export const getGroups = createSelector(getAdditionalFields, f => f?.groups || [])
export const getExpandedGroups = createSelector(getGroups, g => g.filter(g => g.expanded).map(g => g.id))
