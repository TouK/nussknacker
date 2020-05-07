import {ProcessToDisplayState, GraphState} from "./graphState"
import {GroupId, GroupType} from "../types"
import {Layout} from "../actions/nk"
import {defaultsDeep} from "lodash"
import {Reducer} from "../actions/reduxTypes"

export function fromString(groupsStr: string): Layout {
  if (!groupsStr) {
    return []
  }
  return JSON.parse(groupsStr)
}

function toString(groups: GroupId[]): string {
  return JSON.stringify(groups)
}

export const appendToProcess = (groups: GroupId[]) => (state: ProcessToDisplayState): GraphState => defaultsDeep(
  {
    properties: {
      additionalFields: {
        properties: {
          expandedGroups: toString(groups),
        },
      },
    },
  },
  state,
)

export const groups: Reducer<GroupType[]> = (groups, action) => {
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
