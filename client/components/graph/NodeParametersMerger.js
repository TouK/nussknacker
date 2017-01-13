import _ from 'lodash'
import NodeUtils from "./NodeUtils"

class MissingNodeParamsMerger {

  _nodeTypeConfig = {
    "Enricher":   { field: "service",  idField: "service.id", extractObj: (node) => {return node.service}},
    "Processor":  { field: "service",  idField: "service.id", extractObj: (node) => {return node.service}},
    "Sink":       { field: "ref",      idField: "ref.typ",    extractObj: (node) => {return node.ref}},
    "Source":     { field: "ref",      idField: "ref.typ",    extractObj: (node) => {return node.ref}},
    "CustomNode": { field: null,       idField: "nodeType",   extractObj: (node) => {return node}}
  }

  addMissingParametersToNode = (processDefinitionData, node) => {
    const nodeType = NodeUtils.nodeType(node)
    const config = this._nodeTypeConfig[nodeType]
    if (nodeType == 'Properties') {
      const paramsDefinition = processDefinitionData.processDefinition.exceptionHandlerFactory.parameters
      const nodeParams = node.exceptionHandler.parameters
      return this._doAddMissingParametersToNode(paramsDefinition, nodeParams, node, 'exceptionHandler',
        (param) => { return {name: param.name, value: ''} }
      )
    } else if (!_.isEmpty(config)) {
      const nodeFromDefinition = this._findNodeFromDefinition(processDefinitionData, config.idField, node, nodeType)
      const paramsDefinition = (config.extractObj(nodeFromDefinition) || {}).parameters
      const nodeParams = config.extractObj(node).parameters
      return this._doAddMissingParametersToNode(paramsDefinition, nodeParams, node, config.field, (param) => { return param })
    } else {
      return node
    }
  }

  _findNodeFromDefinition = (processDefinitionData, idField, node, nodeType) => {
    const objectFromDefinition = _.flatMap(processDefinitionData.nodesToAdd, ((obj) => { return obj.possibleNodes }))
      .find((obj) => obj.type.toLowerCase() == nodeType.toLowerCase() && _.get(obj.node, idField) == _.get(node, idField))
    return objectFromDefinition ? objectFromDefinition.node : {}
  }

  _doAddMissingParametersToNode = (paramsDefinition, nodeParams, node, fieldName, defaultParam) => {
    const mergedParameters = _.uniq((paramsDefinition || []).concat(nodeParams)
      .map((param) => nodeParams.find((nodeParam) => nodeParam.name == param.name) || defaultParam(param)))
    const nodeWithMergedParams = _.cloneDeep(node)
    _.set(nodeWithMergedParams, fieldName ? `${fieldName}.parameters` : 'parameters', mergedParameters)
    return nodeWithMergedParams
  }

}

export default new MissingNodeParamsMerger()