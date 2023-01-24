import {createSelector} from "reselect"
import {RootState} from "../index"

export const getGraph = (state: RootState): RootState["graphReducer"] => state.graphReducer

export const getFetchedProcessDetails = createSelector(getGraph, g => g.fetchedProcessDetails)
export const getProcessId = createSelector(getFetchedProcessDetails, d => d?.name)

