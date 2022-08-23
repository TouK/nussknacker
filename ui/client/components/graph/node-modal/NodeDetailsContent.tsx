/* eslint-disable i18next/no-literal-string */
import React, {useCallback, useEffect, useMemo, useState} from "react"
import {Edge, NodeId, NodeType, NodeValidationError} from "../../../types"
import NodeAdditionalInfoBox from "./NodeAdditionalInfoBox"
import {getParameterDefinitions} from "./NodeDetailsContentUtils"
import {adjustParameters} from "./ParametersUtils"
import {WithTempId} from "./EdgesDndComponent"
import {useDispatch, useSelector} from "react-redux"
import {nodeValidationDataClear, updateNodeData} from "../../../actions/nk"
import {
  getCurrentErrors,
  getDynamicParameterDefinitions,
  getExpressionType,
  getFindAvailableBranchVariables,
  getFindAvailableVariables,
  getNodeTypingInfo,
  getProcessId,
  getProcessProperties,
  getVariableTypes,
} from "./NodeDetailsContent/selectors"
import {getProcessDefinitionData} from "../../../reducers/selectors/settings"
import {RootState} from "../../../reducers"
import {NodeTable} from "./NodeDetailsContent/NodeTable"
import {generateUUIDs} from "./nodeUtils"
import {isEqual, partition} from "lodash"
import NodeErrors from "./NodeErrors"
import {TestResultsWrapper} from "./TestResultsWrapper"
import {NodeDetailsContent3} from "./NodeDetailsContent3"

export interface NodeDetailsContentProps {
  originalNodeId?: NodeId,
  node: NodeType,
  edges?: Edge[],
  onChange?: (node: NodeType, outputEdges?: Edge[]) => void,
  nodeErrors?: NodeValidationError[],
  pathsToMark?: string[],
  isEditMode?: boolean,
  showValidation?: boolean,
  showSwitch?: boolean,
}

export const NodeDetailsContent = (props: NodeDetailsContentProps): JSX.Element => {
  const {node, isEditMode, originalNodeId, nodeErrors, onChange, pathsToMark, showValidation, showSwitch, edges} = props

  const dispatch = useDispatch()
  const [originalNode] = useState(node)

  const {id} = originalNode
  const nodeId = originalNodeId || id

  useEffect(() => {
    dispatch(nodeValidationDataClear(nodeId))
  }, [dispatch, nodeId])

  const processId = useSelector(getProcessId)
  const processProperties = useSelector(getProcessProperties)
  const processDefinitionData = useSelector(getProcessDefinitionData)
  const findAvailableVariables = useSelector(getFindAvailableVariables)
  const findAvailableBranchVariables = useSelector(getFindAvailableBranchVariables)
  const expressionType = useSelector((state: RootState) => getExpressionType(state)(nodeId))
  const nodeTypingInfo = useSelector((state: RootState) => getNodeTypingInfo(state)(nodeId))
  const variableTypes = useSelector((state: RootState) => getVariableTypes(state)(nodeId))
  const currentErrors = useSelector((state: RootState) => getCurrentErrors(state)(nodeId, nodeErrors))
  const dynamicParameterDefinitions = useSelector((state: RootState) => getDynamicParameterDefinitions(state)(nodeId))

  const nodeDataUpdate = useCallback(
    (node: NodeType, edges: WithTempId<Edge>[]) => {

      const validationRequestData = {
        variableTypes: findAvailableVariables(nodeId),
        branchVariableTypes: findAvailableBranchVariables(nodeId),
        nodeData: node,
        processProperties,
        outgoingEdges: edges.map(e => ({...e, to: e._id || e.to})),
      }
      // HttpService.validateNode(processId, validationRequestData)
      return dispatch(updateNodeData(processId, validationRequestData))
    },
    [dispatch, findAvailableBranchVariables, findAvailableVariables, nodeId, processId, processProperties]
  )

  const parameterDefinitions = useMemo(() => {
    return getParameterDefinitions(processDefinitionData, originalNode, dynamicParameterDefinitions)
  }, [dynamicParameterDefinitions, originalNode, processDefinitionData])

  const adjustNode = useCallback((node: NodeType) => {
    const {adjustedNode} = adjustParameters(node, parameterDefinitions)
    return generateUUIDs(adjustedNode, ["fields", "parameters"])
  }, [parameterDefinitions])

  const [editedNode, setEditedNode] = useState<NodeType>(originalNode)

  useEffect(() => {
    setEditedNode((node) => {
      const adjustedNode = adjustNode(node)
      return isEqual(adjustedNode, node) ? node : adjustedNode
    })
  }, [adjustNode])

  const [editedEdges, setEditedEdges] = useState<WithTempId<Edge>[]>(edges)

  useEffect(() => {
    nodeDataUpdate(editedNode, editedEdges)
  }, [editedEdges, editedNode, nodeDataUpdate])

  useEffect(() => {
    onChange?.(editedNode, editedEdges)
  }, [editedEdges, editedNode, onChange])

  const [fieldErrors, otherErrors] = useMemo(() => partition(currentErrors, error => !!error.fieldName), [currentErrors])

  return (
    <NodeTable editable={isEditMode}>
      <NodeErrors errors={otherErrors} message="Node has errors"/>
      <TestResultsWrapper nodeId={id}>
        <NodeDetailsContent3
          originalNode={originalNode}
          editedNode={editedNode}
          originalNodeId={originalNodeId}
          isEditMode={isEditMode}
          pathsToMark={pathsToMark}
          showValidation={showValidation}
          showSwitch={showSwitch}
          parameterDefinitions={parameterDefinitions}
          editedEdges={editedEdges}
          setEditedEdges={setEditedEdges}
          processDefinitionData={processDefinitionData}
          findAvailableVariables={findAvailableVariables}
          expressionType={expressionType}
          nodeTypingInfo={nodeTypingInfo}
          variableTypes={variableTypes}
          updateNodeState={setEditedNode}
          fieldErrors={fieldErrors}
        />
      </TestResultsWrapper>
      <NodeAdditionalInfoBox node={originalNode}/>
    </NodeTable>
  )
}

