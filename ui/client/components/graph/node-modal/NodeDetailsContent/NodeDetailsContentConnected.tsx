import {useDispatch, useSelector} from "react-redux"
import {nodeValidationDataClear, updateNodeData} from "../../../../actions/nk"
import React, {useEffect} from "react"
import {NodeDetailsContent, NodeDetailsContentProps} from "../NodeDetailsContent"
import {
  AdditionalPropertiesConfig,
  DynamicParameterDefinitions,
  NodeType,
  PropertiesType,
  VariableTypes,
} from "../../../../types"
import classNames from "classnames"
import {getProcessDefinitionData} from "../../../../reducers/selectors/settings"
import {
  getAdditionalPropertiesConfig,
  getCurrentErrors,
  getDynamicParameterDefinitions,
  getExpressionType,
  getFindAvailableBranchVariables,
  getFindAvailableVariables,
  getNodeTypingInfo,
  getProcessId,
  getProcessProperties,
  getVariableTypes,
} from "./selectors"

interface Props extends NodeDetailsContentProps {
  node: NodeType,
  onChange?: (node: NodeType) => void,
  originalNodeId?: NodeType["id"],

  [k: string]: unknown,
}

function NodeDetailsContentConnected({node, ...passProps}: Props): JSX.Element {
  const {isEditMode, originalNodeId, nodeErrors} = passProps
  const nodeId = originalNodeId || node?.id

  const expressionType = useSelector(getExpressionType)
  const nodeTypingInfo = useSelector(getNodeTypingInfo)
  const variableTypes = useSelector(getVariableTypes)
  const currentErrors = useSelector(getCurrentErrors)
  const dynamicParameterDefinitions: DynamicParameterDefinitions = useSelector(getDynamicParameterDefinitions)
  const processDefinitionData = useSelector(getProcessDefinitionData)
  const additionalPropertiesConfig: AdditionalPropertiesConfig = useSelector(getAdditionalPropertiesConfig)
  const findAvailableVariables = useSelector(getFindAvailableVariables)
  const findAvailableBranchVariables = useSelector(getFindAvailableBranchVariables)
  const processProperties = useSelector(getProcessProperties)
  const processId = useSelector(getProcessId)

  const dispatch = useDispatch()

  useEffect(() => {
    dispatch(nodeValidationDataClear(nodeId))
  }, [dispatch, nodeId])

  const nodeClass = classNames("node-table", {"node-editable": isEditMode})

  const onNodeDataUpdate = (processId: string, variableTypes: VariableTypes, branchVariableTypes: Record<string, VariableTypes>, nodeData: NodeType, processProperties: PropertiesType) => dispatch(updateNodeData(processId, variableTypes, branchVariableTypes, nodeData, processProperties))
  return (
    <div className={nodeClass}>
      <NodeDetailsContent
        node={node}
        {...passProps}
        originalNodeId={nodeId}
        expressionType={expressionType(nodeId)}
        nodeTypingInfo={nodeTypingInfo(nodeId)}
        variableTypes={variableTypes(nodeId)}
        currentErrors={currentErrors(nodeId, nodeErrors)}
        dynamicParameterDefinitions={dynamicParameterDefinitions(nodeId)}
        processDefinitionData={processDefinitionData}
        additionalPropertiesConfig={additionalPropertiesConfig}
        findAvailableVariables={findAvailableVariables}
        findAvailableBranchVariables={findAvailableBranchVariables}
        processProperties={processProperties}
        processId={processId}
        updateNodeData={onNodeDataUpdate}
      />
    </div>
  )
}

export default NodeDetailsContentConnected
