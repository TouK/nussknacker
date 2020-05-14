import {GroupType} from "../types"
import {defaultsDeep} from "lodash"
import {Reducer} from "../actions/reduxTypes"

export const reducer: Reducer<GroupType[]> = (groups = [], action) => {
  switch (action.type) {
    case "LAYOUT_CHANGED":
      return groups.map(g => {
        const layoutData = action.layout.find(({id}) => id === g.id)?.position || null
        return defaultsDeep({layoutData}, g)
      })
    case "EXPAND_GROUP":
      return groups.map(g => action.id === g.id ? {...g, expanded: true} : g)
    case "COLLAPSE_GROUP":
      return groups.map(g => action.id === g.id ? {...g, expanded: false} : g)
    case "COLLAPSE_ALL_GROUPS":
      return groups.map(g => ({...g, expanded: false}))
    default:
      return groups
  }
}
