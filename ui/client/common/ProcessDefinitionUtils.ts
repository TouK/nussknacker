import {flatMap} from "lodash"
import {NodeCategory, NodesGroup, PossibleNode, ProcessDefinitionData} from "../types"

function getPossibleNodesInCategory(possibleNodes: PossibleNode[], category: NodeCategory) {
  return possibleNodes.filter(node => node.categories.includes(category))
}

export function getNodesToAddInCategory(processDefinitionData: ProcessDefinitionData, category: NodeCategory): NodesGroup[] {
  return (processDefinitionData.nodesToAdd || []).map(group => {
    return {
      ...group,
      possibleNodes: getPossibleNodesInCategory(group.possibleNodes, category),
    }
  })
}

export function getFlatNodesToAddInCategory(processDefinitionData: ProcessDefinitionData, category: NodeCategory): PossibleNode[] {
  const nodesToAdd = getNodesToAddInCategory(processDefinitionData, category)
  return flatMap(nodesToAdd, group => group.possibleNodes)
}

export function filterNodesByLabel(filter: string): (nodesGroup: NodesGroup) => NodesGroup {
  const searchText = filter.toLowerCase()
  const predicate = ({label}: PossibleNode) => label.toLowerCase().includes(searchText)
  return (nodesGroup: NodesGroup): NodesGroup => ({
    ...nodesGroup,
    possibleNodes: nodesGroup.possibleNodes.filter(predicate),
  })
}
