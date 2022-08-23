/* eslint-disable i18next/no-literal-string */
import React, {useCallback, useEffect, useMemo, useState} from "react"
import {Edge, NodeId, NodeType, NodeValidationError} from "../../../types"
import NodeAdditionalInfoBox from "./NodeAdditionalInfoBox"
import {getParameterDefinitions} from "./NodeDetailsContentUtils"
import {adjustParameters} from "./ParametersUtils"
import {WithTempId} from "./EdgesDndComponent"
import {useDispatch, useSelector} from "react-redux"
import {nodeValidationDataClear, validateNodeData} from "../../../actions/nk"
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
import {useDiffMark} from "./PathsToMark"

export interface NodeDetailsContentProps {
  originalNodeId?: NodeId,
  node: NodeType,
  edges?: Edge[],
  onChange?: (node: NodeType, outputEdges?: Edge[]) => void,
  nodeErrors?: NodeValidationError[],
  isEditMode?: boolean,
  showValidation?: boolean,
  showSwitch?: boolean,
}

export const NodeDetailsContent = ({
  node,
  isEditMode,
  originalNodeId,
  nodeErrors,
  onChange,
  showValidation,
  showSwitch,
  edges,
}: NodeDetailsContentProps): JSX.Element => {
  const dispatch = useDispatch()

  const [originalNode, _setOriginalNode] = useState(node)
  const {id} = originalNode
  const nodeId = originalNodeId || id

  //used here and passed
  const processDefinitionData = useSelector(getProcessDefinitionData)
  const findAvailableVariables = useSelector(getFindAvailableVariables)
  const findAvailableBranchVariables = useSelector(getFindAvailableBranchVariables)
  const variableTypes = useSelector((state: RootState) => getVariableTypes(state)(nodeId))
  const currentErrors = useSelector((state: RootState) => getCurrentErrors(state)(nodeId, nodeErrors))
  const dynamicParameterDefinitions = useSelector((state: RootState) => getDynamicParameterDefinitions(state)(nodeId))
  const [editedNode, setEditedNode] = useState<NodeType>(originalNode)
  const [editedEdges, setEditedEdges] = useState<WithTempId<Edge>[]>(edges)

  //used only here
  const processId = useSelector(getProcessId)
  const processProperties = useSelector(getProcessProperties)

  const validate = useCallback(
    (node: NodeType, edges?: WithTempId<Edge>[]) => {
      const validationRequestData = {
        variableTypes: findAvailableVariables(nodeId),
        branchVariableTypes: findAvailableBranchVariables(nodeId),
        nodeData: node,
        processProperties,
        outgoingEdges: edges?.map(e => ({...e, to: e._id || e.to})),
      }
      return dispatch(validateNodeData(processId, validationRequestData))
    },
    [dispatch, findAvailableBranchVariables, findAvailableVariables, nodeId, processId, processProperties]
  )

  //passed only
  const expressionType = useSelector((state: RootState) => getExpressionType(state)(nodeId))
  const nodeTypingInfo = useSelector((state: RootState) => getNodeTypingInfo(state)(nodeId))
  const [fieldErrors, otherErrors] = useMemo(() => partition(currentErrors, error => !!error.fieldName), [currentErrors])

  const parameterDefinitions = useMemo(() => {
    return getParameterDefinitions(processDefinitionData, originalNode, dynamicParameterDefinitions)
  }, [dynamicParameterDefinitions, originalNode, processDefinitionData])

  const adjustNode = useCallback((node: NodeType) => {
    const {adjustedNode} = adjustParameters(node, parameterDefinitions)
    return generateUUIDs(adjustedNode, ["fields", "parameters"])
  }, [parameterDefinitions])

  useEffect(() => {
    dispatch(nodeValidationDataClear(nodeId))
  }, [dispatch, nodeId])

  useEffect(() => {
    setEditedNode((node) => {
      const adjustedNode = adjustNode(node)
      return isEqual(adjustedNode, node) ? node : adjustedNode
    })
  }, [adjustNode])

  useEffect(() => {
    if (isEditMode) {
      validate(editedNode, editedEdges)
    }
  }, [editedEdges, editedNode, isEditMode, validate])

  useEffect(() => {
    if (isEditMode) {
      onChange?.(editedNode, editedEdges)
    }
  }, [editedEdges, editedNode, isEditMode, onChange])

  //fixme: workaround for compare view
  const [, isCompareView] = useDiffMark()
  useEffect(() => {
    if (isCompareView) {
      _setOriginalNode(node)
      setEditedNode(node)
    }
  }, [isCompareView, node])

  return (
    <NodeTable editable={isEditMode}>
      <NodeErrors errors={otherErrors} message="Node has errors"/>
      <TestResultsWrapper nodeId={id}>
        <NodeDetailsContent3
          originalNode={originalNode}
          editedNode={editedNode}
          originalNodeId={originalNodeId}
          isEditMode={isEditMode}
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

