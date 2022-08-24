/* eslint-disable i18next/no-literal-string */
import React, {useCallback, useEffect, useMemo} from "react"
import {Edge, NodeId, NodeType, NodeValidationError} from "../../../types"
import NodeAdditionalInfoBox from "./NodeAdditionalInfoBox"
import {adjustParameters} from "./ParametersUtils"
import {useDispatch, useSelector} from "react-redux"
import {nodeValidationDataClear, validateNodeData} from "../../../actions/nk"
import {
  getCurrentErrors,
  getDynamicParameterDefinitions,
  getFindAvailableBranchVariables,
  getFindAvailableVariables,
  getProcessId,
  getProcessProperties,
} from "./NodeDetailsContent/selectors"
import {getProcessDefinitionData} from "../../../reducers/selectors/settings"
import {RootState} from "../../../reducers"
import {NodeTable} from "./NodeDetailsContent/NodeTable"
import {generateUUIDs} from "./nodeUtils"
import {isEqual, partition} from "lodash"
import NodeErrors from "./NodeErrors"
import {TestResultsWrapper} from "./TestResultsWrapper"
import {NodeDetailsContent3} from "./NodeDetailsContent3"
import ProcessUtils from "../../../common/ProcessUtils"
import {UpdateState} from "./NodeDetailsContentProps3"

export interface NodeDetailsContentProps {
  originalNodeId?: NodeId,
  node: NodeType,
  edges?: Edge[],
  onChange?: (node: NodeType, outputEdges?: Edge[]) => void,
  nodeErrors?: NodeValidationError[],
  showValidation?: boolean,
  showSwitch?: boolean,
}

export const NodeDetailsContent = ({
  node,
  originalNodeId,
  nodeErrors,
  onChange,
  showValidation,
  showSwitch,
  edges,
}: NodeDetailsContentProps): JSX.Element => {
  const dispatch = useDispatch()

  const processDefinitionData = useSelector(getProcessDefinitionData)
  const findAvailableVariables = useSelector(getFindAvailableVariables)
  const findAvailableBranchVariables = useSelector(getFindAvailableBranchVariables)

  const isEditMode = !!onChange
  const nodeId = originalNodeId || node.id
  const currentErrors = useSelector((state: RootState) => getCurrentErrors(state)(nodeId, nodeErrors))
  const dynamicParameterDefinitions = useSelector((state: RootState) => getDynamicParameterDefinitions(state)(nodeId))

  const processId = useSelector(getProcessId)
  const processProperties = useSelector(getProcessProperties)

  const validate = useCallback(
    (nodeData: NodeType, outgoingEdges?: Edge[]) => dispatch(
      validateNodeData(processId, {
        outgoingEdges,
        nodeData,
        processProperties,
        branchVariableTypes: findAvailableBranchVariables(nodeId),
        variableTypes: findAvailableVariables(nodeId),
      })
    ),
    [dispatch, findAvailableBranchVariables, findAvailableVariables, nodeId, processId, processProperties]
  )

  const validateAndChange = useCallback(
    (node: NodeType, edges: Edge[]) => {
      validate(node, edges)
      onChange(node, edges)
    },
    [onChange, validate]
  )

  const setEditedNode = useCallback<UpdateState<NodeType>>(
    (nodeChange) => {
      if (isEditMode) {
        const changedNode = nodeChange(node)
        validateAndChange(changedNode, edges)
      }
    },
    [edges, isEditMode, node, validateAndChange]
  )

  const setEditedEdges = useCallback(
    (edges: Edge[]) => {
      if (isEditMode) {
        validateAndChange(node, edges)
      }
    },
    [isEditMode, node, validateAndChange]
  )

  const [fieldErrors, otherErrors] = useMemo(() => partition(currentErrors, error => !!error.fieldName), [currentErrors])

  const parameterDefinitions = useMemo(() => {
    if (!dynamicParameterDefinitions) {
      return ProcessUtils.findNodeObjectTypeDefinition(node, processDefinitionData.processDefinition)?.parameters
    }
    return dynamicParameterDefinitions
  }, [dynamicParameterDefinitions, node, processDefinitionData])

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
  }, [adjustNode, setEditedNode])

  return (
    <NodeTable editable={isEditMode}>
      <NodeErrors errors={otherErrors} message="Node has errors"/>
      <TestResultsWrapper nodeId={originalNodeId}>
        <NodeDetailsContent3
          editedNode={node}
          originalNodeId={originalNodeId}
          isEditMode={isEditMode}
          showValidation={showValidation}
          showSwitch={showSwitch}
          parameterDefinitions={parameterDefinitions}
          editedEdges={edges}
          setEditedEdges={setEditedEdges}
          processDefinitionData={processDefinitionData}
          findAvailableVariables={findAvailableVariables}
          updateNodeState={setEditedNode}
          fieldErrors={fieldErrors}
        />
      </TestResultsWrapper>
      <NodeAdditionalInfoBox node={node}/>
    </NodeTable>
  )
}

