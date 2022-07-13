import {useDispatch, useSelector} from "react-redux"
import {nodeValidationDataClear, updateNodeData, ValidationRequest} from "../../../../actions/nk"
import React, {useCallback, useEffect} from "react"
import {NodeDetailsContent, NodeDetailsContentProps} from "../NodeDetailsContent"
import {AdditionalPropertiesConfig, DynamicParameterDefinitions} from "../../../../types"
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
import {useUserSettings} from "../../../../common/userSettings"

interface Props extends Omit<NodeDetailsContentProps, "userSettings"> {
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
  const [userSettings] = useUserSettings()

  const dispatch = useDispatch()

  useEffect(() => {
    dispatch(nodeValidationDataClear(nodeId))
  }, [dispatch, nodeId])

  const nodeClass = classNames("node-table", {"node-editable": isEditMode})

  const onNodeDataUpdate = useCallback(
    (processId: string, validationRequestData: ValidationRequest) => dispatch(updateNodeData(processId, validationRequestData)),
    [dispatch]
  )

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
        userSettings={userSettings}
      />
    </div>
  )
}

export default NodeDetailsContentConnected
