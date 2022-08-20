import {useDispatch, useSelector} from "react-redux"
import {nodeValidationDataClear, updateNodeData} from "../../../../actions/nk"
import React, {useCallback, useEffect} from "react"
import {NodeDetailsContent} from "../NodeDetailsContent"
import {AdditionalPropertiesConfig, DynamicParameterDefinitions, Edge, NodeType} from "../../../../types"
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
import {NodeDetailsContentConnectedProps, WithNodeErrors} from "../NodeDetailsContentProps3"
import {WithTempId} from "../EdgesDndComponent"

function NodeDetailsContentConnected(props: NodeDetailsContentConnectedProps & WithNodeErrors): JSX.Element {
  const {node, isEditMode, originalNodeId, nodeErrors, onChange, pathsToMark, showValidation, showSwitch, edges} = props
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

  const onNodeDataUpdate = useCallback(
    (node: NodeType, edges: WithTempId<Edge>[]) => {
      return dispatch(updateNodeData(processId, {
        variableTypes: findAvailableVariables(originalNodeId),
        branchVariableTypes: findAvailableBranchVariables(originalNodeId),
        nodeData: node,
        processProperties,
        outgoingEdges: edges.map(e => ({...e, to: e._id || e.to})),
      }))
    },
    [dispatch, findAvailableBranchVariables, findAvailableVariables, originalNodeId, processId, processProperties]
  )

  return (
    <div className={nodeClass}>
      <NodeDetailsContent
        isEditMode={isEditMode}
        onChange={onChange}
        pathsToMark={pathsToMark}
        showValidation={showValidation}
        showSwitch={showSwitch}
        edges={edges}
        node={node}
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
