/* eslint-disable i18next/no-literal-string */
import NodeUtils from "../../NodeUtils"
import {concat} from "lodash"
import {DEFAULT_EXPRESSION_ID} from "../../../../common/graph/constants"
import SubprocessOutputDefinition from "../SubprocessOutputDefinition"
import MapVariable from "../MapVariable"
import Variable from "../Variable"
import {branchErrorFieldName} from "../BranchParameters"
import {AdditionalPropertiesConfig, DynamicParameterDefinitions, NodeType, Parameter} from "../../../../types"

function joinFields(editedNode: NodeType, parametersFromDefinition: Parameter[] = []): string[] {
  const commonFields = ["id", "outputVar"]
  const paramFields = parametersFromDefinition.map(param => param.name)
  const branchParamsFields = editedNode?.branchParameters?.flatMap(branchParam => branchParam.parameters.map(param => branchErrorFieldName(param.name, branchParam.branchId)))
  return concat(commonFields, paramFields, branchParamsFields == null ? [] : branchParamsFields)
}

export function refParameters(editedNode: NodeType): { name: string }[] {
  return editedNode.ref.parameters || []
}

export function serviceParameters(editedNode: NodeType): { name: string }[] {
  return editedNode.service.parameters || []
}

export function getAvailableFields(editedNode: NodeType, node: NodeType, additionalPropertiesConfig: AdditionalPropertiesConfig, dynamicParameterDefinitions: DynamicParameterDefinitions): string[] {
  if (dynamicParameterDefinitions) {
    return joinFields(editedNode, dynamicParameterDefinitions)
  }

  switch (NodeUtils.nodeType(editedNode)) {
    case "Source": {
      const commonFields = ["id"]
      return concat(commonFields, refParameters(editedNode).map(param => param.name))
    }
    case "Sink": {
      const commonFields = ["id", DEFAULT_EXPRESSION_ID]
      return concat(commonFields, refParameters(editedNode).map(param => param.name))
    }
    case "SubprocessInputDefinition": {
      return ["id"]
    }
    case "SubprocessOutputDefinition":
      return SubprocessOutputDefinition.availableFields(editedNode)
    case "Filter":
      return ["id", DEFAULT_EXPRESSION_ID]
    case "Enricher":
      const commonFields = ["id", "output"]
      const paramFields = serviceParameters(editedNode).map(param => param.name)
      return concat(commonFields, paramFields)
    case "Processor": {
      const commonFields = ["id"]
      const paramFields = serviceParameters(editedNode).map(param => param.name)
      return concat(commonFields, paramFields)
    }
    case "SubprocessInput": {
      const commonFields = ["id"]
      const paramFields = refParameters(editedNode).map(param => param.name)
      return concat(commonFields, paramFields)
    }
    case "Join": {
      return joinFields(editedNode, editedNode.parameters)
    }
    case "CustomNode": {
      const commonFields = ["id", "outputVar"]
      const paramFields = editedNode.parameters?.map(param => param.name)
      return concat(commonFields, paramFields)
    }
    case "VariableBuilder":
      return MapVariable.availableFields(editedNode)
    case "Variable":
      return Variable.availableFields
    case "Switch":
      return ["id", DEFAULT_EXPRESSION_ID, "exprVal", "edge"]
    case "Split":
      return ["id"]
    case "Properties": {
      const fields = node.isSubprocess ?
        ["docsUrl"] :
        node.typeSpecificProperties.type === "StreamMetaData" ?
          ["parallelism", "checkpointIntervalInSeconds", "spillStateToDisk", "useAsyncInterpretation"] :
          ["path"]
      const additionalFields = Object.entries(additionalPropertiesConfig).map(([fieldName, fieldConfig]) => fieldName)
      return concat(fields, additionalFields)
    }
    default:
      return []
  }
}
