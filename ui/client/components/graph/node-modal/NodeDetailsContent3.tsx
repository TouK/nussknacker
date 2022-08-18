/* eslint-disable i18next/no-literal-string */
import {NodeType} from "../../../types"
import React, {useCallback, useMemo} from "react"
import {cloneDeep, set, startsWith} from "lodash"
import {FieldLabel} from "./FieldLabel"
import NodeUtils from "../NodeUtils"
import SubprocessInputDefinition from "./subprocess-input-definition/SubprocessInputDefinition"
import SubprocessOutputDefinition from "./SubprocessOutputDefinition"
import Variable from "./Variable"
import {NodeDetails} from "./NodeDetailsContent/NodeDetails"
import {useTestResults} from "./TestResultsWrapper"
import {NodeDetailsContentProps3} from "./NodeDetailsContentProps3"
import {
  ArrayElement,
  Filter,
  getEnricherProcessor,
  getJoinCustomNode,
  getProperties,
  getSubprocessInput,
  getSwitch,
  getVariable,
  getVariableBuilder,
  Sink,
  Source,
  Split,
  SubprocessInputDef,
  SubprocessOutputDef,
} from "./components"

export function NodeDetailsContent3(props: NodeDetailsContentProps3): JSX.Element {
  const {
    updateNodeState,
    findAvailableVariables,
    parameterDefinitions,
    originalNodeId,
    pathsToMark,
  } = props

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
    const value = newValue == null && defaultValue != undefined ? defaultValue : newValue
    updateNodeState((currentNode) => {
      const node = cloneDeep(currentNode)
      return set(node, property, value)
    })
  }, [updateNodeState])

  const variableTypes = useMemo(() => findAvailableVariables?.(originalNodeId), [findAvailableVariables, originalNodeId])

  const componentsMethods = {isMarked, renderFieldLabel, setProperty}

  switch (NodeUtils.nodeType(props.node)) {
    case "Source":
      return (
        <Source
          {...props}
          {...componentsMethods}
        />
      )
    case "Sink":
      return (
        <Sink
          {...props}
          {...componentsMethods}
        />
      )
    case "SubprocessInputDefinition":
      return (
        <SubprocessInputDef
          {...props}
          {...componentsMethods}
          addElement={addElement}
          removeElement={removeElement}
          variableTypes={variableTypes}
        />
      )
    case "SubprocessOutputDefinition":
      return (
        <SubprocessOutputDef
          {...props}
          {...componentsMethods}
          addElement={addElement}
          removeElement={removeElement}
          variableTypes={variableTypes}
        />
      )
    case "Filter":
      return (
        <Filter
          {...props}
          {...componentsMethods}
          isCompareView={isCompareView}
        />
      )
    case "Enricher":
    case "Processor":
      return getEnricherProcessor(props, isMarked, renderFieldLabel, setProperty)
    case "SubprocessInput":
      return getSubprocessInput(props, isMarked, renderFieldLabel, setProperty)
    case "Join":
    case "CustomNode":
      return getJoinCustomNode(props, isMarked, renderFieldLabel, setProperty, testResultsState)
    case "VariableBuilder":
      return getVariableBuilder(renderFieldLabel, removeElement, setProperty, props, addElement, isMarked, variableTypes)
    case "Variable":
      return getVariable(props, renderFieldLabel, setProperty, isMarked, variableTypes)
    case "Switch":
      return getSwitch(props, isMarked, renderFieldLabel, setProperty, isCompareView, variableTypes)
    case "Split":
      return (
        <Split
          {...props}
          {...componentsMethods}
        />
      )
    case "Properties":
      return getProperties(props, isMarked, renderFieldLabel, setProperty)
    default:
      return (
        <div>
          Node type not known.
          <NodeDetails node={props.node}/>
        </div>
      )
  }
}
