/* eslint-disable i18next/no-literal-string */
import {NodeType} from "../../../types"
import React, {useCallback, useMemo} from "react"
import {cloneDeep, set, startsWith} from "lodash"
import {FieldLabel} from "./FieldLabel"
import NodeUtils from "../NodeUtils"
import {NodeDetailsFallback} from "./NodeDetailsContent/NodeDetailsFallback"
import {useTestResults} from "./TestResultsWrapper"
import {NodeDetailsContentProps3} from "./NodeDetailsContentProps3"
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

export function NodeDetailsContent3({
  additionalPropertiesConfig,
  editedEdges,
  editedNode,
  expressionType,
  fieldErrors,
  findAvailableVariables,
  isEditMode,
  nodeTypingInfo,
  node,
  originalNode,
  originalNodeId,
  parameterDefinitions,
  pathsToMark,
  processDefinitionData,
  setEditedEdges,
  showSwitch,
  showValidation,
  updateNodeState,
}: NodeDetailsContentProps3): JSX.Element {

  const testResultsState = useTestResults()

  const isMarked = useCallback((path = ""): boolean => {
    return pathsToMark?.some(toMark => startsWith(toMark, path))
  }, [pathsToMark])

  //compare window uses legacy egde component
  const isCompareView = useMemo(() => isMarked(), [isMarked])

  const removeElement = useCallback((property: keyof NodeType, index: number): void => {
    updateNodeState((currentNode) => ({
      ...currentNode,
      [property]: currentNode[property]?.filter((_, i) => i !== index) || [],
    }))
  }, [updateNodeState])

  const renderFieldLabel = useCallback((paramName: string): JSX.Element => {
    return (
      <FieldLabel
        nodeId={originalNodeId}
        parameterDefinitions={parameterDefinitions}
        paramName={paramName}
      />
    )
  }, [originalNodeId, parameterDefinitions])

  const addElement = useCallback(<K extends keyof NodeType>(property: K, element: ArrayElement<NodeType[K]>): void => {
    updateNodeState((currentNode) => ({
      ...currentNode,
      [property]: [...currentNode[property], element],
    }))
  }, [updateNodeState])

  const setProperty = useCallback(<K extends keyof NodeType>(property: K, newValue: NodeType[K], defaultValue?: NodeType[K]): void => {
    updateNodeState((currentNode) => {
      const value = newValue == null && defaultValue != undefined ? defaultValue : newValue
      const node = cloneDeep(currentNode)
      return set(node, property, value)
    })
  }, [updateNodeState])

  const variableTypes = useMemo(() => findAvailableVariables?.(originalNodeId), [findAvailableVariables, originalNodeId])

  switch (NodeUtils.nodeType(node)) {
    case "Source":
      return (
        <Source
          node={node}
          originalNodeId={originalNodeId}
          isEditMode={isEditMode}
          showValidation={showValidation}
          showSwitch={showSwitch}
          editedNode={editedNode}
          findAvailableVariables={findAvailableVariables}
          parameterDefinitions={parameterDefinitions}
          fieldErrors={fieldErrors}

          isMarked={isMarked}
          renderFieldLabel={renderFieldLabel}
          setProperty={setProperty}
        />
      )
    case "Sink":
      return (
        <Sink
          node={node}
          originalNodeId={originalNodeId}
          isEditMode={isEditMode}
          showValidation={showValidation}
          showSwitch={showSwitch}
          editedNode={editedNode}
          findAvailableVariables={findAvailableVariables}
          parameterDefinitions={parameterDefinitions}
          fieldErrors={fieldErrors}

          isMarked={isMarked}
          renderFieldLabel={renderFieldLabel}
          setProperty={setProperty}
        />
      )
    case "SubprocessInputDefinition":
      return (
        <SubprocessInputDef
          isEditMode={isEditMode}
          showValidation={showValidation}
          editedNode={editedNode}
          fieldErrors={fieldErrors}

          isMarked={isMarked}
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
          editedNode={editedNode}
          expressionType={expressionType}
          nodeTypingInfo={nodeTypingInfo}
          fieldErrors={fieldErrors}

          isMarked={isMarked}
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
          editedNode={editedNode}
          editedEdges={editedEdges}
          setEditedEdges={setEditedEdges}
          findAvailableVariables={findAvailableVariables}
          parameterDefinitions={parameterDefinitions}
          fieldErrors={fieldErrors}

          isMarked={isMarked}
          renderFieldLabel={renderFieldLabel}
          setProperty={setProperty}
          isCompareView={isCompareView}
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
          editedNode={editedNode}
          findAvailableVariables={findAvailableVariables}
          parameterDefinitions={parameterDefinitions}
          fieldErrors={fieldErrors}

          isMarked={isMarked}
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
          editedNode={editedNode}
          findAvailableVariables={findAvailableVariables}
          processDefinitionData={processDefinitionData}
          parameterDefinitions={parameterDefinitions}
          fieldErrors={fieldErrors}

          isMarked={isMarked}
          renderFieldLabel={renderFieldLabel}
          setProperty={setProperty}
        />
      )
    case "Join":
    case "CustomNode":
      return (
        <JoinCustomNode
          node={node}
          originalNodeId={originalNodeId}
          isEditMode={isEditMode}
          showValidation={showValidation}
          showSwitch={showSwitch}
          editedNode={editedNode}
          findAvailableVariables={findAvailableVariables}
          processDefinitionData={processDefinitionData}
          parameterDefinitions={parameterDefinitions}
          fieldErrors={fieldErrors}

          isMarked={isMarked}
          renderFieldLabel={renderFieldLabel}
          setProperty={setProperty}
          testResultsState={testResultsState}
        />
      )
    case "VariableBuilder":
      return (
        <VariableBuilder
          isEditMode={isEditMode}
          showValidation={showValidation}
          editedNode={editedNode}
          expressionType={expressionType}
          nodeTypingInfo={nodeTypingInfo}
          fieldErrors={fieldErrors}
          isMarked={isMarked}
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
          showValidation={showValidation}
          editedNode={editedNode}
          expressionType={expressionType}
          nodeTypingInfo={nodeTypingInfo}
          fieldErrors={fieldErrors}
          isMarked={isMarked}
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
          originalNode={originalNode}
          editedNode={editedNode}
          editedEdges={editedEdges}
          setEditedEdges={setEditedEdges}
          findAvailableVariables={findAvailableVariables}
          processDefinitionData={processDefinitionData}
          expressionType={expressionType}
          nodeTypingInfo={nodeTypingInfo}
          parameterDefinitions={parameterDefinitions}
          fieldErrors={fieldErrors}

          isMarked={isMarked}
          renderFieldLabel={renderFieldLabel}
          setProperty={setProperty}
          isCompareView={isCompareView}
          variableTypes={variableTypes}
        />
      )
    case "Split":
      return (
        <Split
          isEditMode={isEditMode}
          showValidation={showValidation}
          editedNode={editedNode}
          isMarked={isMarked}
          renderFieldLabel={renderFieldLabel}
          setProperty={setProperty}
        />
      )
    case "Properties":
      return (
        <Properties
          node={node}
          isEditMode={isEditMode}
          showValidation={showValidation}
          showSwitch={showSwitch}
          editedNode={editedNode}
          additionalPropertiesConfig={additionalPropertiesConfig}
          processDefinitionData={processDefinitionData}
          fieldErrors={fieldErrors}

          isMarked={isMarked}
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
