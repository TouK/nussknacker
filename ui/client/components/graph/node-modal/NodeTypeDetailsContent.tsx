import {Edge, NodeId, NodeType, NodeValidationError} from "../../../types"
import React, {SetStateAction, useCallback, useEffect, useMemo} from "react"
import {useDispatch, useSelector} from "react-redux"
import {getProcessDefinitionData} from "../../../reducers/selectors/settings"
import {
  getDynamicParameterDefinitions,
  getFindAvailableBranchVariables,
  getFindAvailableVariables,
  getProcessId,
  getProcessProperties,
} from "./NodeDetailsContent/selectors"
import {adjustParameters} from "./ParametersUtils"
import {generateUUIDs} from "./nodeUtils"
import {FieldLabel} from "./FieldLabel"
import {cloneDeep, isEqual, set} from "lodash"
import {nodeValidationDataClear, validateNodeData} from "../../../actions/nk"
import NodeUtils from "../NodeUtils"
import {Source} from "./source"
import {Sink} from "./sink"
import SubprocessInputDefinition from "./subprocess-input-definition/SubprocessInputDefinition"
import SubprocessOutputDefinition from "./SubprocessOutputDefinition"
import {Filter} from "./filter"
import {EnricherProcessor} from "./enricherProcessor"
import {SubprocessInput} from "./subprocessInput"
import {JoinCustomNode} from "./joinCustomNode"
import {VariableBuilder} from "./variableBuilder"
import Variable from "./Variable"
import {Switch} from "./switch"
import {Split} from "./split"
import {Properties} from "./properties"
import {NodeDetailsFallback} from "./NodeDetailsContent/NodeDetailsFallback"

type ArrayElement<A extends readonly unknown[]> = A extends readonly (infer E)[] ? E : never

interface NodeTypeDetailsContentProps {
  originalNodeId?: NodeId,
  node: NodeType,
  edges?: Edge[],
  onChange?: (node: SetStateAction<NodeType>, edges?: SetStateAction<Edge[]>) => void,
  showValidation?: boolean,
  showSwitch?: boolean,
  fieldErrors?: NodeValidationError[],
}

export function NodeTypeDetailsContent({
  originalNodeId,
  node,
  edges,
  onChange,
  fieldErrors,
  showValidation,
  showSwitch,
}: NodeTypeDetailsContentProps): JSX.Element {
  const dispatch = useDispatch()

  const isEditMode = !!onChange

  const processDefinitionData = useSelector(getProcessDefinitionData)
  const findAvailableVariables = useSelector(getFindAvailableVariables)
  const getParameterDefinitions = useSelector(getDynamicParameterDefinitions)
  const getBranchVariableTypes = useSelector(getFindAvailableBranchVariables)
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

  const parameterDefinitions = useMemo(
    () => getParameterDefinitions(node, originalNodeId),
    [getParameterDefinitions, node, originalNodeId]
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
      branchVariableTypes: getBranchVariableTypes(node.id),
      variableTypes,
    }))
  }, [dispatch, edges, getBranchVariableTypes, node, processId, processProperties, variableTypes])

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
          fieldErrors={fieldErrors}
          findAvailableVariables={findAvailableVariables}
          isEditMode={isEditMode}
          node={node}
          originalNodeId={originalNodeId}
          parameterDefinitions={parameterDefinitions}
          renderFieldLabel={renderFieldLabel}
          setProperty={setProperty}
          showSwitch={showSwitch}
          showValidation={showValidation}
        />
      )
    case "Sink":
      return (
        <Sink
          fieldErrors={fieldErrors}
          findAvailableVariables={findAvailableVariables}
          isEditMode={isEditMode}
          node={node}
          originalNodeId={originalNodeId}
          parameterDefinitions={parameterDefinitions}
          renderFieldLabel={renderFieldLabel}
          setProperty={setProperty}
          showSwitch={showSwitch}
          showValidation={showValidation}
        />
      )
    case "SubprocessInputDefinition":
      return (
        <SubprocessInputDefinition
          addElement={addElement}
          fieldErrors={fieldErrors}
          isEditMode={isEditMode}
          node={node}
          removeElement={removeElement}
          renderFieldLabel={renderFieldLabel}
          setProperty={setProperty}
          showValidation={showValidation}
          variableTypes={variableTypes}
        />
      )
    case "SubprocessOutputDefinition":
      return (
        <SubprocessOutputDefinition
          addElement={addElement}
          fieldErrors={fieldErrors}
          isEditMode={isEditMode}
          node={node}
          originalNodeId={originalNodeId}
          removeElement={removeElement}
          renderFieldLabel={renderFieldLabel}
          setProperty={setProperty}
          showValidation={showValidation}
          variableTypes={variableTypes}
        />
      )
    case "Filter":
      return (
        <Filter
          edges={edges}
          fieldErrors={fieldErrors}
          findAvailableVariables={findAvailableVariables}
          isEditMode={isEditMode}
          node={node}
          originalNodeId={originalNodeId}
          parameterDefinitions={parameterDefinitions}
          renderFieldLabel={renderFieldLabel}
          setEditedEdges={setEditedEdges}
          setProperty={setProperty}
          showSwitch={showSwitch}
          showValidation={showValidation}
        />
      )
    case "Enricher":
    case "Processor":
      return (
        <EnricherProcessor
          fieldErrors={fieldErrors}
          findAvailableVariables={findAvailableVariables}
          isEditMode={isEditMode}
          node={node}
          originalNodeId={originalNodeId}
          parameterDefinitions={parameterDefinitions}
          renderFieldLabel={renderFieldLabel}
          setProperty={setProperty}
          showSwitch={showSwitch}
          showValidation={showValidation}
        />
      )
    case "SubprocessInput":
      return (
        <SubprocessInput
          fieldErrors={fieldErrors}
          findAvailableVariables={findAvailableVariables}
          isEditMode={isEditMode}
          node={node}
          originalNodeId={originalNodeId}
          parameterDefinitions={parameterDefinitions}
          processDefinitionData={processDefinitionData}
          renderFieldLabel={renderFieldLabel}
          setProperty={setProperty}
          showSwitch={showSwitch}
          showValidation={showValidation}
        />
      )
    case "Join":
    case "CustomNode":
      return (
        <JoinCustomNode
          fieldErrors={fieldErrors}
          findAvailableVariables={findAvailableVariables}
          isEditMode={isEditMode}
          node={node}
          originalNodeId={originalNodeId}
          parameterDefinitions={parameterDefinitions}
          processDefinitionData={processDefinitionData}
          renderFieldLabel={renderFieldLabel}
          setProperty={setProperty}
          showSwitch={showSwitch}
          showValidation={showValidation}
        />
      )
    case "VariableBuilder":
      return (
        <VariableBuilder
          addElement={addElement}
          fieldErrors={fieldErrors}
          isEditMode={isEditMode}
          node={node}
          removeElement={removeElement}
          renderFieldLabel={renderFieldLabel}
          setProperty={setProperty}
          showValidation={showValidation}
          variableTypes={variableTypes}
        />
      )
    case "Variable":
      return (
        <Variable
          fieldErrors={fieldErrors}
          isEditMode={isEditMode}
          node={node}
          originalNodeId={originalNodeId}
          renderFieldLabel={renderFieldLabel}
          setProperty={setProperty}
          showValidation={showValidation}
          variableTypes={variableTypes}
        />
      )
    case "Switch":
      return (
        <Switch
          edges={edges}
          fieldErrors={fieldErrors}
          findAvailableVariables={findAvailableVariables}
          isEditMode={isEditMode}
          node={node}
          originalNodeId={originalNodeId}
          parameterDefinitions={parameterDefinitions}
          processDefinitionData={processDefinitionData}
          renderFieldLabel={renderFieldLabel}
          setEditedEdges={setEditedEdges}
          setProperty={setProperty}
          showSwitch={showSwitch}
          showValidation={showValidation}
          variableTypes={variableTypes}
        />
      )
    case "Split":
      return (
        <Split
          isEditMode={isEditMode}
          node={node}
          renderFieldLabel={renderFieldLabel}
          setProperty={setProperty}
          showValidation={showValidation}
        />
      )
    case "Properties":
      return (
        <Properties
          fieldErrors={fieldErrors}
          isEditMode={isEditMode}
          node={node}
          processDefinitionData={processDefinitionData}
          renderFieldLabel={renderFieldLabel}
          setProperty={setProperty}
          showSwitch={showSwitch}
          showValidation={showValidation}
        />
      )
    default:
      return (
        <NodeDetailsFallback node={node}/>
      )
  }
}
