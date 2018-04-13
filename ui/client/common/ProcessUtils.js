import _ from 'lodash'

class ProcessUtils {

  nothingToSave = (state) => {
    const fetchedProcessDetails = state.graphReducer.fetchedProcessDetails
    const processToDisplay = state.graphReducer.processToDisplay
    return !_.isEmpty(fetchedProcessDetails) ? _.isEqual(fetchedProcessDetails.json, processToDisplay) : true
  }

  processDisplayName = (processId, versionId) => {
    return `${processId}:v${versionId}`
  }

  isInGroupingMode = (state) => {
    return state.graphReducer.groupingState != null
  }

  //fixme maybe return hasErrors flag from backend?
  hasNoErrorsNorWarnings = (process) => {
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

  findAvailableVariables = (nodeId, process, processDefinition) => {
    const variablesFromValidation = _.get(process.validationResult, "variableTypes." + nodeId)
    return variablesFromValidation || this._findVariablesBasedOnGraph(nodeId, process, processDefinition)
  }

  //FIXME: handle source/sink/exceptionHandler properly here - we don't want to use #input etc here!
  _findVariablesBasedOnGraph = (nodeId, process, processDefinition) => {
    const globalVariables = _.mapValues(processDefinition.globalVariables, (v) => {return v.returnType})
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
      case "CustomNode": {
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
      case "CustomNode": {
        return node.nodeType
      }
      default: {
        return null;
      }
    }
  }

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

  search = (processes, objectToFind) => {
    if (_.isEmpty(objectToFind)) {
      return []
    } else {
      return _.flatMap(processes, (p) => {
        const nodesWithSearchedObjects = _.filter(_.get(p, 'json.nodes', []), (n) => {
          const nodeDef = this.findNodeDefinitionId(n)
          return _.isEqual(nodeDef, objectToFind)
        })
        return _.map(nodesWithSearchedObjects, (n) => {
          return {
            process: p,
            node: n
          }
        })
      })
    }
  }

}

export default new ProcessUtils()
