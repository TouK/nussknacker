import {getGraph} from "./graph"
import {createSelector} from "reselect"
import {RootState} from "../index"
import {Layout} from "../../actions/nk"

export const getLayout: (state: RootState) => Layout = createSelector(
  getGraph,
  g => g.layout,
)
