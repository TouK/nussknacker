import NodeUtils from "../NodeUtils"
import {NodeType, Parameter, UIParameter} from "../../../types"
import {cloneDeep} from "lodash"

export type AdjustReturn = {
    node: NodeType,
    unusedParameters: Array<Parameter>,
}

const findUnusedParameters = (parameters: Array<Parameter>, definitions: Array<UIParameter>) => {
  return parameters.filter(param => !definitions.find(def => def.name == param.name))
}

const propertiesPath = (node) => {
  switch (NodeUtils.nodeType(node)) {
    case "SubprocessInput":
      return "ref.parameters"
    case "CustomNode":
      return "parameters"
    default:
      return null
  }
}

export const adjustParameters = (node: NodeType, parameterDefinitions: Array<UIParameter>, baseNode: NodeType): AdjustReturn => {
  const path = propertiesPath(node)
  const baseNodeParameters = baseNode && baseNode[path]
  if (path) {
    const currentParameters = node[path]
    const adjustedParameters = parameterDefinitions.map(def => {
      const currentParam = currentParameters.find(p => p.name == def.name)
      const parameterFromBase = baseNodeParameters?.find(p => p.name == def.name)
      //TODO: pass default values with UI
      const parameterFromDefinition = {name: def.name, expression: {expression: "", language: "spel"}}
      return currentParam || parameterFromBase || parameterFromDefinition
    })
    const cloned = cloneDeep(node)
    cloned[path] = adjustedParameters
    return {
      node: cloned,
      unusedParameters: findUnusedParameters(currentParameters, parameterDefinitions),
    }

  } else {
    return {
      node: node,
      unusedParameters: [],
    }
  }
}
