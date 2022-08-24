/* eslint-disable i18next/no-literal-string */
import React, {SetStateAction, useCallback, useEffect, useMemo} from "react"
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
import {cloneDeep, isEqual, partition, set} from "lodash"
import NodeErrors from "./NodeErrors"
import {TestResultsWrapper} from "./TestResultsWrapper"
import {FieldLabel} from "./FieldLabel"
import {
  ArrayElement,
  EnricherProcessor,
  Filter,
  JoinCustomNode,
  Properties,
  Sink,
  Source,
  Split,
  SubprocessInput,
  SubprocessInputDef,
  SubprocessOutputDef,
  Switch,
  VariableBuilder,
  VariableDef,
} from "./components"
import NodeUtils from "../NodeUtils"
import {NodeDetailsFallback} from "./NodeDetailsContent/NodeDetailsFallback"

export const NodeDetailsContent = ({
  originalNodeId,
  node,
  edges,
  onChange,
  nodeErrors,
  showValidation,
  showSwitch,
}: {
  originalNodeId?: NodeId,
  node: NodeType,
  edges?: Edge[],
  onChange?: (node: SetStateAction<NodeType>, edges?: SetStateAction<Edge[]>) => void,
  nodeErrors?: NodeValidationError[],
  showValidation?: boolean,
  showSwitch?: boolean,
}): JSX.Element => {
  const currentErrors = useSelector((state: RootState) => getCurrentErrors(state)(originalNodeId, nodeErrors))
  const [fieldErrors, otherErrors] = useMemo(() => partition(currentErrors, error => !!error.fieldName), [currentErrors])

  return (
    <NodeTable editable={!!onChange}>
      <NodeErrors errors={otherErrors} message="Node has errors"/>
      <TestResultsWrapper nodeId={originalNodeId}>
        <NodeDetailsContent2
          originalNodeId={originalNodeId}
          node={node}
          edges={edges}
          onChange={onChange}
          fieldErrors={fieldErrors}
          showValidation={showValidation}
          showSwitch={showSwitch}
        />
      </TestResultsWrapper>
      <NodeAdditionalInfoBox node={node}/>
    </NodeTable>
  )
}

export const NodeDetailsContent2 = ({
  originalNodeId,
  node,
  edges,
  onChange,
  fieldErrors,
  showValidation,
  showSwitch,
}: {
  originalNodeId?: NodeId,
  node: NodeType,
  edges?: Edge[],
  onChange?: (node: SetStateAction<NodeType>, edges?: SetStateAction<Edge[]>) => void,
  showValidation?: boolean,
  showSwitch?: boolean,
  fieldErrors?: NodeValidationError[],
}): JSX.Element => {
  const dispatch = useDispatch()

  const isEditMode = !!onChange

  const processDefinitionData = useSelector(getProcessDefinitionData)
  const findAvailableVariables = useSelector(getFindAvailableVariables)
  const parameterDefinitions = useSelector((state: RootState) => {
    return getDynamicParameterDefinitions(state)(node)
  })

  const _branchVariableTypes = useSelector((state: RootState) => getFindAvailableBranchVariables(state))
  const branchVariableTypes = useMemo(() => _branchVariableTypes(originalNodeId), [_branchVariableTypes, originalNodeId])
  const processId = useSelector(getProcessId)
  const processProperties = useSelector(getProcessProperties)

  const variableTypes = useMemo(() => findAvailableVariables?.(originalNodeId), [findAvailableVariables, originalNodeId])

  const change = useCallback(
    (node: SetStateAction<NodeType>, edges: SetStateAction<Edge[]>) => {
      if (isEditMode) {
        onChange(node, edges)
      }
    },
    [isEditMode, onChange]
  )

  const setEditedNode = useCallback(
    (n: SetStateAction<NodeType>) => change(n, edges),
    [edges, change]
  )

  const setEditedEdges = useCallback(
    (e: SetStateAction<Edge[]>) => change(node, e),
    [node, change]
  )

  const adjustNode = useCallback((node: NodeType) => {
    const {adjustedNode} = adjustParameters(node, parameterDefinitions)
    return generateUUIDs(adjustedNode, ["fields", "parameters"])
  }, [parameterDefinitions])

  const renderFieldLabel = useCallback((paramName: string): JSX.Element => {
    return (
      <FieldLabel
        nodeId={originalNodeId}
        parameterDefinitions={parameterDefinitions}
        paramName={paramName}
      />
    )
  }, [originalNodeId, parameterDefinitions])

  const removeElement = useCallback((property: keyof NodeType, index: number): void => {
    setEditedNode((currentNode) => ({
      ...currentNode,
      [property]: currentNode[property]?.filter((_, i) => i !== index) || [],
    }))
  }, [setEditedNode])

  const addElement = useCallback(<K extends keyof NodeType>(property: K, element: ArrayElement<NodeType[K]>): void => {
    setEditedNode((currentNode) => ({
      ...currentNode,
      [property]: [...currentNode[property], element],
    }))
  }, [setEditedNode])

  const setProperty = useCallback(<K extends keyof NodeType>(property: K, newValue: NodeType[K], defaultValue?: NodeType[K]): void => {
    setEditedNode((currentNode) => {
      const value = newValue == null && defaultValue != undefined ? defaultValue : newValue
      const node = cloneDeep(currentNode)
      return set(node, property, value)
    })
  }, [setEditedNode])

  useEffect(() => {
    dispatch(nodeValidationDataClear(originalNodeId))
  }, [dispatch, originalNodeId])

  useEffect(() => {
    dispatch(validateNodeData(processId, {
      outgoingEdges: edges,
      nodeData: node,
      processProperties,
      branchVariableTypes,
      variableTypes,
    }))
  }, [branchVariableTypes, dispatch, edges, node, processId, processProperties, variableTypes])

  useEffect(() => {
    setEditedNode((node) => {
      const adjustedNode = adjustNode(node)
      return isEqual(adjustedNode, node) ? node : adjustedNode
    })
  }, [adjustNode, setEditedNode])

  switch (NodeUtils.nodeType(node)) {
    case "Source":
      return (
        <Source
          originalNodeId={originalNodeId}
          isEditMode={isEditMode}
          showValidation={showValidation}
          showSwitch={showSwitch}
          node={node}
          findAvailableVariables={findAvailableVariables}
          parameterDefinitions={parameterDefinitions}
          fieldErrors={fieldErrors}
          renderFieldLabel={renderFieldLabel}
          setProperty={setProperty}
        />
      )
    case "Sink":
      return (
        <Sink
          originalNodeId={originalNodeId}
          isEditMode={isEditMode}
          showValidation={showValidation}
          showSwitch={showSwitch}
          node={node}
          findAvailableVariables={findAvailableVariables}
          parameterDefinitions={parameterDefinitions}
          fieldErrors={fieldErrors}
          renderFieldLabel={renderFieldLabel}
          setProperty={setProperty}
        />
      )
    case "SubprocessInputDefinition":
      return (
        <SubprocessInputDef
          isEditMode={isEditMode}
          showValidation={showValidation}
          node={node}
          fieldErrors={fieldErrors}
          renderFieldLabel={renderFieldLabel}
          setProperty={setProperty}
          addElement={addElement}
          removeElement={removeElement}
          variableTypes={variableTypes}
        />
      )
    case "SubprocessOutputDefinition":
      return (
        <SubprocessOutputDef
          isEditMode={isEditMode}
          showValidation={showValidation}
          node={node}
          fieldErrors={fieldErrors}
          renderFieldLabel={renderFieldLabel}
          setProperty={setProperty}
          addElement={addElement}
          removeElement={removeElement}
          variableTypes={variableTypes}
        />
      )
    case "Filter":
      return (
        <Filter
          originalNodeId={originalNodeId}
          isEditMode={isEditMode}
          showValidation={showValidation}
          showSwitch={showSwitch}
          node={node}
          edges={edges}
          setEditedEdges={setEditedEdges}
          findAvailableVariables={findAvailableVariables}
          parameterDefinitions={parameterDefinitions}
          fieldErrors={fieldErrors}
          renderFieldLabel={renderFieldLabel}
          setProperty={setProperty}
        />
      )
    case "Enricher":
    case "Processor":
      return (
        <EnricherProcessor
          originalNodeId={originalNodeId}
          isEditMode={isEditMode}
          showValidation={showValidation}
          showSwitch={showSwitch}
          node={node}
          findAvailableVariables={findAvailableVariables}
          parameterDefinitions={parameterDefinitions}
          fieldErrors={fieldErrors}
          renderFieldLabel={renderFieldLabel}
          setProperty={setProperty}
        />
      )
    case "SubprocessInput":
      return (
        <SubprocessInput
          originalNodeId={originalNodeId}
          isEditMode={isEditMode}
          showValidation={showValidation}
          showSwitch={showSwitch}
          node={node}
          findAvailableVariables={findAvailableVariables}
          processDefinitionData={processDefinitionData}
          parameterDefinitions={parameterDefinitions}
          fieldErrors={fieldErrors}
          renderFieldLabel={renderFieldLabel}
          setProperty={setProperty}
        />
      )
    case "Join":
    case "CustomNode":
      return (
        <JoinCustomNode
          originalNodeId={originalNodeId}
          isEditMode={isEditMode}
          showValidation={showValidation}
          showSwitch={showSwitch}
          node={node}
          findAvailableVariables={findAvailableVariables}
          processDefinitionData={processDefinitionData}
          parameterDefinitions={parameterDefinitions}
          fieldErrors={fieldErrors}
          renderFieldLabel={renderFieldLabel}
          setProperty={setProperty}
        />
      )
    case "VariableBuilder":
      return (
        <VariableBuilder
          isEditMode={isEditMode}
          showValidation={showValidation}
          node={node}
          fieldErrors={fieldErrors}
          renderFieldLabel={renderFieldLabel}
          setProperty={setProperty}
          removeElement={removeElement}
          addElement={addElement}
          variableTypes={variableTypes}
        />
      )
    case "Variable":
      return (
        <VariableDef
          isEditMode={isEditMode}
          originalNodeId={originalNodeId}
          showValidation={showValidation}
          node={node}
          fieldErrors={fieldErrors}
          renderFieldLabel={renderFieldLabel}
          setProperty={setProperty}
          variableTypes={variableTypes}
        />
      )
    case "Switch":
      return (
        <Switch
          originalNodeId={originalNodeId}
          isEditMode={isEditMode}
          showValidation={showValidation}
          showSwitch={showSwitch}
          node={node}
          edges={edges}
          setEditedEdges={setEditedEdges}
          findAvailableVariables={findAvailableVariables}
          processDefinitionData={processDefinitionData}
          parameterDefinitions={parameterDefinitions}
          fieldErrors={fieldErrors}
          renderFieldLabel={renderFieldLabel}
          setProperty={setProperty}
          variableTypes={variableTypes}
        />
      )
    case "Split":
      return (
        <Split
          isEditMode={isEditMode}
          showValidation={showValidation}
          node={node}
          renderFieldLabel={renderFieldLabel}
          setProperty={setProperty}
        />
      )
    case "Properties":
      return (
        <Properties
          isEditMode={isEditMode}
          showValidation={showValidation}
          showSwitch={showSwitch}
          node={node}
          processDefinitionData={processDefinitionData}
          fieldErrors={fieldErrors}
          renderFieldLabel={renderFieldLabel}
          setProperty={setProperty}
        />
      )
    default:
      return (
        <NodeDetailsFallback node={node}/>
      )
  }
}

