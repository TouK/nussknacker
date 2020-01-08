import _ from "lodash";

const getPossibleNodesInCategory = (possibleNodes, category) =>
  possibleNodes.filter(node => node.categories.includes(category))

const getNodesToAddInCategory = (processDefinitionData, category) => {
  return (processDefinitionData.nodesToAdd || []).map(group => {
    return {
      ...group,
      possibleNodes: getPossibleNodesInCategory(group.possibleNodes, category)
    }
  })
}

const getFlatNodesToAddInCategory = (processDefinitionData, category) => {
  const nodesToAdd = getNodesToAddInCategory(processDefinitionData, category)
  return _.flatMap(nodesToAdd, group => group.possibleNodes)
}

export {
  getNodesToAddInCategory,
  getFlatNodesToAddInCategory,
}