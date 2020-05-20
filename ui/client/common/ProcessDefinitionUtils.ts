import {flatMap} from "lodash"
import {ProcessDefinitionData, PossibleNode, NodeCategory} from "../types"

function getPossibleNodesInCategory(possibleNodes: PossibleNode[], category: NodeCategory) {
  return possibleNodes.filter(node => node.categories.includes(category))
}

export function getNodesToAddInCategory(processDefinitionData: ProcessDefinitionData, category: NodeCategory) {
  return (processDefinitionData.nodesToAdd || []).map(group => {
    return {
      ...group,
      possibleNodes: getPossibleNodesInCategory(group.possibleNodes, category),
    }
  })
}

export function getFlatNodesToAddInCategory(processDefinitionData: ProcessDefinitionData, category: NodeCategory) {
  const nodesToAdd = getNodesToAddInCategory(processDefinitionData, category)
  return flatMap(nodesToAdd, group => group.possibleNodes)
}
