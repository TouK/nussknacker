import _ from 'lodash'
import NodeUtils from "../components/graph/NodeUtils";

class ProcessUtils {

  nothingToSave = (state) => {
    const fetchedProcessDetails = state.graphReducer.fetchedProcessDetails
    const processToDisplay = state.graphReducer.processToDisplay
    return !_.isEmpty(fetchedProcessDetails) ? _.isEqual(fetchedProcessDetails.json, processToDisplay) : true
  }

  canExport = (state) => {
      const fetchedProcessDetails = state.graphReducer.fetchedProcessDetails;
      return _.isEmpty(fetchedProcessDetails) ? false : !_.isEmpty(fetchedProcessDetails.json.nodes)
  }

  //fixme maybe return hasErrors flag from backend?
  hasNeitherErrorsNorWarnings = (process) => {
    return this.hasNoErrors(process) && this.hasNoWarnings(process)
  }

  extractInvalidNodes = (invalidNodes) => {
    return _.flatten(Object.keys(invalidNodes || {}).map((key, idx) => invalidNodes[key].map((error) => {
      return {"error": error, "key": key}
    })))
  }

  hasNoErrors = (process) => {
    const result = (process.validationResult || {}).errors
    return !result || (Object.keys(result.invalidNodes || {}).length == 0
      && (result.globalErrors || []).length == 0 && (result.processPropertiesErrors || []).length == 0)
  }

  hasNoWarnings = (process) => {
    const warnings = (process.validationResult || {}).warnings
    return _.isEmpty(warnings) || Object.keys(warnings.invalidNodes || {}).length == 0
  }

  hasNoPropertiesErrors = process => {
    const result = (process.validationResult || {}).errors || {}
    return _.isEmpty(result.processPropertiesErrors)
  }

  findAvailableVariables = (nodeId, process, processDefinition, fieldName, processCategory) => {
    const globalVariablesWithoutProcessCategory = this._findGlobalVariablesWithoutProcessCategory(processDefinition.globalVariables, processCategory)
    const variablesFromValidation = _.get(process.validationResult, "variableTypes." + nodeId)
    const variablesForNode = variablesFromValidation || this._findVariablesBasedOnGraph(nodeId, process, processDefinition)
    const additionalVariablesForParam = nodeId ? this._additionalVariablesForParameter(nodeId, process, processDefinition, fieldName) : {}
    const variables = {...variablesForNode, ...additionalVariablesForParam}

    //Filtering by category - we show variables only with the same category as process, removing these which are in blackList
    return _.pickBy(variables, (va, key) => _.indexOf(globalVariablesWithoutProcessCategory, key) === -1)
  }

  //It's not pretty but works.. This should be done at backend with properly category hierarchy
  _findGlobalVariablesWithoutProcessCategory = (globalVariables, processCategory) => {
    return _.keys(_.pickBy(globalVariables, variable => _.indexOf(variable.categories, processCategory) === -1))
  }

  _additionalVariablesForParameter = (nodeId, process, processDefinition, fieldName) => {
    const node = NodeUtils.getNodeById(nodeId, process)
    const nodeDefinition = this.findNodeObjectTypeDefinition(node, processDefinition) || {}
    const parameter = (nodeDefinition.parameters || []).find(p => p.name === fieldName) || {}
    return parameter.additionalVariables || {}
  }

  //FIXME: handle source/sink/exceptionHandler properly here - we don't want to use #input etc here!
  _findVariablesBasedOnGraph = (nodeId, process, processDefinition) => {
    const filteredGlobalVariables = _.pickBy(processDefinition.globalVariables, variable => variable.returnType !== null)
    const globalVariables = _.mapValues(filteredGlobalVariables, (v) => {return v.returnType})
    const variablesDefinedBeforeNode = this._findVariablesDeclaredBeforeNode(nodeId, process, processDefinition);
    return {
      ...globalVariables,
      ...variablesDefinedBeforeNode
    }
  }

  _findVariablesDeclaredBeforeNode = (nodeId, process, processDefinition) => {
    const previousNodes = this._findPreviousNodes(nodeId, process, processDefinition)
    const variablesDefinedBeforeNodeList = _.flatMap(previousNodes, (nodeId) => {
      return this._findVariablesDefinedInProcess(nodeId, process, processDefinition)
    })
    return this._listOfObjectsToObject(variablesDefinedBeforeNodeList);
  }

  _listOfObjectsToObject = (list) => {
    return _.reduce(list, (memo, current) => { return {...memo, ...current}},  {})
  }

  _findVariablesDefinedInProcess = (nodeId, process, processDefinition) => {
    const node = _.find(process.nodes, (node) => node.id == nodeId)
    const nodeObjectTypeDefinition = this.findNodeObjectTypeDefinition(node, processDefinition)
    const clazzName = _.get(nodeObjectTypeDefinition, 'returnType')
    switch (node.type) {
      case "Source": {
        return [{"input": clazzName}]
      }
      case "SubprocessInputDefinition": {
        return node.parameters.map(param => ({[param.name]: param.typ }))
      }
      case "Enricher": {
        return [{[node.output]: clazzName}]
      }
      case "CustomNode":
      case "Join": {
        const outputVariableName = node.outputVar
        const outputClazz = clazzName
        return _.isEmpty(outputClazz) ? [] : [ {[outputVariableName]: outputClazz} ]
      }
      case "VariableBuilder": {
        return [{[node.varName]: {refClazzName: "java.lang.Object"}}]
      }
      case "Variable": {
        return [{[node.varName]: {refClazzName: "java.lang.Object"}}]
      }
      case "Switch": {
        return [{[node.exprVal]: {refClazzName: "java.lang.Object"}}]
      }
      default: {
        return []
      }
    }
  }

  //TODO: this should be done without these switches..
  findNodeObjectTypeDefinition = (node, processDefinition) => {
    if (node == null) {
      return {}
    }

    const nodeDefinitionId = this.findNodeDefinitionId(node)
    switch (node.type) {
      case "Source": {
        return _.get(processDefinition, `sourceFactories[${nodeDefinitionId}]`)
      }
      case "Sink": {
        return _.get(processDefinition, `sinkFactories[${nodeDefinitionId}]`)
      }
      case "Enricher":
      case "Processor": {
        return _.get(processDefinition, `services[${nodeDefinitionId}]`)
      }
      case "Join":
      case "CustomNode": {
        return _.get(processDefinition, `customStreamTransformers[${nodeDefinitionId}]`)
      }
      case "SubprocessInput": {
        return _.get(processDefinition, `subprocessInputs[${nodeDefinitionId}]`)
      }
      default: {
        return {}
      }
    }
  }

  //TODO: this should be done without these switches..
  findNodeDefinitionId = (node) => {
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
      default: {
        return null;
      }
    }
  }

  findNodeDefinitionIdOrType = (node) =>
    this.findNodeDefinitionId(node) || node.type || null

  findNodeConfigName = (node) => {
      return this.findNodeDefinitionId(node) || (node.type && node.type.charAt(0).toLowerCase() + node.type.slice(1));
  }

  humanReadableType = (refClazzOrName) => {
    const refClazzName = (refClazzOrName || {}).refClazzName || refClazzOrName
    if (_.isEmpty(refClazzName)) {
      return ""
    } else {
      const typeSplitted = _.split(refClazzName, ".")
      const lastClazzNamePart = _.last(typeSplitted).replace("$", "")
      return _.upperFirst(lastClazzNamePart)
    }
  }

  _findPreviousNodes = (nodeId, process) => {
    const nodeEdge = _.find(process.edges, (edge) => _.isEqual(edge.to, nodeId))
    if (_.isEmpty(nodeEdge)) {
      return []
    } else {
      const previousNodes = this._findPreviousNodes(nodeEdge.from, process)
      return _.concat([nodeEdge.from], previousNodes)
    }
  }

  prepareFilterCategories = (categories, loggedUser) => _.map((categories || []).filter(c => loggedUser.canRead(c)), (e) => {
    return {
      value: e,
      label: e
    }
  })
}

export default new ProcessUtils()
