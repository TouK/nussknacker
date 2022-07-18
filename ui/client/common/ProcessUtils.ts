/* eslint-disable i18next/no-literal-string */
import {flatten, get, isEmpty, isEqual, keys, map, mapValues, omit, pickBy, reduce, transform} from "lodash"
import {
  GlobalVariables,
  NodeId,
  NodeObjectTypeDefinition,
  NodeResults,
  NodeType,
  Process,
  ProcessDefinition,
  TypingResult,
  UIParameter,
} from "../types"

class ProcessUtils {

  nothingToSave = (state): boolean => {
    const fetchedProcessDetails = state.graphReducer.fetchedProcessDetails
    const processToDisplay = state.graphReducer.processToDisplay
    //TODO: validationResult should be removed from processToDisplay...
    const omitValidation = (details) => omit(details, ["validationResult"])
    return !isEmpty(fetchedProcessDetails) ? isEqual(omitValidation(fetchedProcessDetails.json), omitValidation(processToDisplay)) : true
  }

  canExport = (state): boolean => {
    const fetchedProcessDetails = state.graphReducer.fetchedProcessDetails
    return isEmpty(fetchedProcessDetails) ? false : !isEmpty(fetchedProcessDetails.json.nodes)
  }

  //fixme maybe return hasErrors flag from backend?
  hasNeitherErrorsNorWarnings = (process) => {
    return this.hasNoErrors(process) && this.hasNoWarnings(process)
  }

  extractInvalidNodes = (invalidNodes) => {
    return flatten(Object.keys(invalidNodes || {}).map((key, idx) => invalidNodes[key].map((error) => {
      return {error: error, key: key}
    })))
  }

  hasNoErrors = (process) => {
    const result = (process.validationResult || {}).errors
    return !result || Object.keys(result.invalidNodes || {}).length == 0 &&
      (result.globalErrors || []).length == 0 && (result.processPropertiesErrors || []).length == 0
  }

  hasNoWarnings = (process) => {
    const warnings = (process.validationResult || {}).warnings
    return isEmpty(warnings) || Object.keys(warnings.invalidNodes || {}).length == 0
  }

  hasNoPropertiesErrors = process => {
    const result = (process.validationResult || {}).errors || {}
    return isEmpty(result.processPropertiesErrors)
  }

  //see BranchEndDefinition.artificialNodeId
  findContextForBranch = (node, branchId) => {
    return `$edge-${branchId}-${node.id}`
  }

  //see BranchEndDefinition.artificialNodeId
  findVariablesForBranches = (nodeResults: NodeResults) => (nodeId) => {
    //we find all nodes matching pattern encoding branch and edge and extract branch id
    const escapedNodeId = this.escapeNodeIdForRegexp(nodeId)
    return transform(nodeResults || {}, function (result, nodeResult, key: string) {
      const branch = key.match(new RegExp(`^\\$edge-(.*)-${escapedNodeId}$`))
      if (branch && branch.length > 1) {
        result[branch[1]] = nodeResult.variableTypes
      }
    }, {})
  }

  //https://developer.mozilla.org/en-US/docs/Web/JavaScript/Guide/Regular_Expressions#Escaping
  escapeNodeIdForRegexp = (id) => id && id.replace(/[.*+\-?^${}()|[\]\\]/g, "\\$&")

  findAvailableVariables = (processDefinition: ProcessDefinition, processCategory: string, process: Process) => (nodeId: NodeId, parameterDefinition: UIParameter) => {
    const globalVariablesWithMismatchCategory = this._findGlobalVariablesWithMismatchCategory(processDefinition.globalVariables, processCategory)
    const variablesFromValidation = process?.validationResult?.nodeResults?.[nodeId]?.variableTypes
    const variablesForNode = variablesFromValidation || this._findVariablesBasedOnGraph(nodeId, process, processDefinition)
    const variablesToHideForParam = parameterDefinition?.variablesToHide || []
    const withoutVariablesToHide = pickBy(variablesForNode, (va, key) => !variablesToHideForParam.includes(key))
    const additionalVariablesForParam = parameterDefinition?.additionalVariables || {}
    const variables = {...withoutVariablesToHide, ...additionalVariablesForParam}
    //Filtering by category - we show variables only with the same category as process, removing these which are in excludeList
    return pickBy(variables, (va, key) => globalVariablesWithMismatchCategory.indexOf(key) === -1)
  }

  //It's not pretty but works.. This should be done at backend with properly category hierarchy
  _findGlobalVariablesWithMismatchCategory = (globalVariables: GlobalVariables, processCategory: string) => {
    return keys(pickBy(globalVariables, variable => variable.categories.indexOf(processCategory) === -1))
  }

  //FIXME: handle source/sink/exceptionHandler properly here - we don't want to use #input etc here!
  _findVariablesBasedOnGraph = (nodeId: NodeId, process: Process, processDefinition: ProcessDefinition) => {
    const filteredGlobalVariables = pickBy(processDefinition.globalVariables, variable => variable.returnType !== null)
    const globalVariables = mapValues(filteredGlobalVariables, (v) => {
      return v.returnType
    })
    const variablesDefinedBeforeNode = this._findVariablesDeclaredBeforeNode(nodeId, process, processDefinition)
    return {
      ...globalVariables,
      ...variablesDefinedBeforeNode,
    }
  }

  _findVariablesDeclaredBeforeNode = (nodeId: NodeId, process: Process, processDefinition: ProcessDefinition) => {
    const previousNodes = this._findPreviousNodes(nodeId, process)
    const variablesDefinedBeforeNodeList = previousNodes.flatMap((nodeId) => {
      return this._findVariablesDefinedInProcess(nodeId, process, processDefinition)
    })
    return this._listOfObjectsToObject(variablesDefinedBeforeNodeList)
  }

  _listOfObjectsToObject = (list) => {
    return reduce(list, (memo, current) => {
      return {...memo, ...current}
    }, {})
  }

  _findVariablesDefinedInProcess = (nodeId: NodeId, process: Process, processDefinition: ProcessDefinition) => {
    const node = process.nodes.find((node) => node.id === nodeId)
    const nodeObjectTypeDefinition = this.findNodeObjectTypeDefinition(node, processDefinition)
    const clazzName = get(nodeObjectTypeDefinition, "returnType")
    const unknown = {type: "Unknown", refClazzName: "java.lang.Object"}
    switch (node.type) {
      case "Source": {
        return isEmpty(clazzName) ? [] : [{input: clazzName}]
      }
      case "SubprocessInputDefinition": {
        return node.parameters?.map(param => ({[param.name]: param.typ}))
      }
      case "Enricher": {
        return [{[node.output]: clazzName}]
      }
      case "CustomNode":
      case "Join": {
        const outputVariableName = node.outputVar
        const outputClazz = clazzName
        return isEmpty(outputClazz) ? [] : [{[outputVariableName]: outputClazz}]
      }
      case "VariableBuilder": {
        return [{[node.varName]: unknown}]
      }
      case "Variable": {
        return [{[node.varName]: unknown}]
      }
      case "Switch": {
        return [node.exprVal ? {[node.exprVal]: unknown} : {}]
      }
      default: {
        return []
      }
    }
  }

  //TODO: this should be done without these switches..
  findNodeObjectTypeDefinition = (node: NodeType, processDefinition: ProcessDefinition): NodeObjectTypeDefinition => {
    if (node) {
      const nodeDefinitionId = this.findNodeDefinitionId(node)
      switch (node.type) {
        case "Source": {
          return processDefinition.sourceFactories?.[nodeDefinitionId]
        }
        case "Sink": {
          return processDefinition.sinkFactories?.[nodeDefinitionId]
        }
        case "Enricher":
        case "Processor": {
          return processDefinition.services?.[nodeDefinitionId]
        }
        case "Join":
        case "CustomNode": {
          return processDefinition.customStreamTransformers?.[nodeDefinitionId]
        }
        case "SubprocessInput": {
          return processDefinition.subprocessInputs?.[nodeDefinitionId]
        }
      }
    }
    return {}
  }

  //TODO: this should be done without these switches..
  findNodeDefinitionId = (node: NodeType): string | null => {
    switch (node.type) {
      case "Source":
      case "Sink": {
        return node.ref.typ
      }
      case "SubprocessInput": {
        return node.ref.id
      }
      case "Enricher":
      case "Processor": {
        return node.service.id
      }
      case "Join":
      case "CustomNode": {
        return node.nodeType
      }
      case "VariableBuilder": {
        //todo remove when VariableBuilder will be removed
        return "mapVariable"
      }
      default: {
        return null
      }
    }
  }

  findNodeDefinitionIdOrType = (node) => this.findNodeDefinitionId(node) || node.type || null

  getNodeBaseTypeCamelCase = (node) => node.type && node.type.charAt(0).toLowerCase() + node.type.slice(1)

  findNodeConfigName = (node): string => {
    // First we try to find id of node (config for specific custom node by id).
    // If it is falsy then we try to extract config name from node type (config for build-in components e.g. variable, join).
    // If all above are falsy then it means that node is special process properties node without id and type.
    return this.findNodeDefinitionId(node) || this.getNodeBaseTypeCamelCase(node) || "$properties"
  }

  humanReadableType = (typingResult: TypingResult): string => isEmpty(typingResult) ? null : typingResult.display

  _findPreviousNodes = (nodeId: NodeId, process: Process): NodeId[] => {
    const nodeEdge = process.edges.find((edge) => edge.to === nodeId)
    if (isEmpty(nodeEdge)) {
      return []
    } else {
      const previousNodes = this._findPreviousNodes(nodeEdge.from, process)
      return [nodeEdge.from].concat(previousNodes)
    }
  }

  prepareFilterCategories = (categories, loggedUser) => map((categories || []).filter(c => loggedUser.canRead(c)), (e) => {
    return {
      value: e,
      label: e,
    }
  })
}

export default new ProcessUtils()
