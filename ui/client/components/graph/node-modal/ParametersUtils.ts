import NodeUtils from "../NodeUtils"
import {NodeType, Parameter, UIParameter} from "../../../types"
import {cloneDeep, get, set} from "lodash"

export type AdjustReturn = {
    node: NodeType,
    //currently not used, but maybe we can e.g. display them somewhere?
    unusedParameters: Array<Parameter>,
}

const findUnusedParameters = (parameters: Array<Parameter>, definitions: Array<UIParameter>) => {
  return parameters.filter(param => !definitions.find(def => def.name == param.name))
}

const parametersPath = (node) => {
  switch (NodeUtils.nodeType(node)) {
    case "CustomNode":
      return "parameters"
    case "Join":
      return "parameters"
    case "Source":
    case "Sink":
      return "ref.parameters"
    case "Enricher":
      return "service.parameters"
    case "Processor":
      return "service.parameters"
    default:
      return null
  }
}

//We want to change parameters in node based on current node definition. This function can be used in
//two cases: dynamic parameters handling and automatic node migrations (e.g. in subprocesses). Currently we use it only for dynamic parameters
export const adjustParameters = (node: NodeType, parameterDefinitions: Array<UIParameter>, baseNode: NodeType): AdjustReturn => {
  const path = parametersPath(node)
  //Currently we try to check if parameter exists in node in toolbox
  const baseNodeParameters = baseNode && get(baseNode, path)
  if (path) {
    const currentParameters = get(node, path)
    //TODO: currently dynamic branch parameters are *not* supported...
    const adjustedParameters = parameterDefinitions.filter(def => !def.branchParam).map(def => {
      const currentParam = currentParameters.find(p => p.name == def.name)
      const parameterFromBase = baseNodeParameters?.find(p => p.name == def.name)
      //TODO: pass default values from BE, then parameterFromBase wont' be needed
      const parameterFromDefinition = {name: def.name, expression: {expression: "", language: "spel"}}
      return currentParam || parameterFromBase || parameterFromDefinition
    })
    const cloned = cloneDeep(node)
    set(cloned, path, adjustedParameters)
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
