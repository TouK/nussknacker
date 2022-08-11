/* eslint-disable i18next/no-literal-string */
import {
  Edge,
  EdgeKind,
  NodeType,
  NodeValidationError,
  ProcessDefinitionData,
  UIParameter,
  VariableTypes,
} from "../../../types"
import AdditionalProperty, {AdditionalPropertyConfig} from "./AdditionalProperty"
import ProcessUtils from "../../../common/ProcessUtils"
import {DispatchWithCallback, hasOutputVar} from "./NodeDetailsContentUtils"
import React, {SetStateAction, useCallback, useMemo} from "react"
import {cloneDeep, isEqual, set, sortBy, startsWith} from "lodash"
import {FieldLabel} from "./FieldLabel"
import {errorValidator} from "./editors/Validators"
import {FieldType} from "./editors/field/Field"
import {serviceParameters} from "./NodeDetailsContent/helpers"
import NodeUtils from "../NodeUtils"
import SubprocessInputDefinition from "./subprocess-input-definition/SubprocessInputDefinition"
import SubprocessOutputDefinition from "./SubprocessOutputDefinition"
import {EdgesDndComponent} from "./EdgesDndComponent"
import ParameterList from "./ParameterList"
import {InputWithFocus} from "../../withFocus"
import BranchParameters from "./BranchParameters"
import MapVariable from "./MapVariable"
import {DEFAULT_EXPRESSION_ID} from "../../../common/graph/constants"
import Variable from "./Variable"
import {NodeDetails} from "./NodeDetailsContent/NodeDetails"
import {
  CompFunctions,
  CreateField,
  DescriptionField,
  IdField,
  ParameterExpressionField,
  SourceSinkCommon,
  StaticExpressionField,
} from "./SourceSinkCommon"
import {useTestResults} from "./TestResultsWrapper"

type UpdateState<T> = (updateState: (currentState: Readonly<T>) => T) => void

export interface NodeDetailsContentProps3 {
  fieldErrors?: NodeValidationError[],
  isEditMode?: boolean,
  additionalPropertiesConfig?: Record<string, AdditionalPropertyConfig>,
  showValidation?: boolean,
  showSwitch?: boolean,
  findAvailableVariables?: ReturnType<typeof ProcessUtils.findAvailableVariables>,
  processDefinitionData?: ProcessDefinitionData,
  node: NodeType,
  edges?: Edge[],
  originalNodeId?: NodeType["id"],
  pathsToMark?: string[],
  expressionType?,
  nodeTypingInfo?,
  onChange?: (node: NodeType, outputEdges?: Edge[]) => void,
  variableTypes?: VariableTypes,

  parameterDefinitions: UIParameter[],
  originalNode: NodeType,
  editedNode: NodeType,
  setEditedNode: DispatchWithCallback<SetStateAction<NodeType>>,
  updateNodeState: UpdateState<NodeType>,

  setEdgesState,
}

export type ArrayElement<A extends readonly unknown[]> = A extends readonly (infer E)[] ? E : never

function DisableField(props: CompFunctions & NodeDetailsContentProps3) {
  return (
    <CreateField
      {...props}
      fieldType={FieldType.checkbox}
      fieldLabel={"Disabled"}
      fieldProperty={"isDisabled"}
    />
  )
}

export function NodeDetailsContent3(props: NodeDetailsContentProps3): JSX.Element {
  const testResultsState = useTestResults()
  const isMarked = useCallback((path = ""): boolean => {
    return props.pathsToMark?.some(toMark => startsWith(toMark, path))
  }, [props.pathsToMark])

  //compare window uses legacy egde component
  const isCompareView = useMemo(() => isMarked(), [isMarked])

  const removeElement = useCallback((property: keyof NodeType, index: number): void => {
    props.updateNodeState((currentNode) => ({
      ...currentNode,
      [property]: currentNode[property]?.filter((_, i) => i !== index) || [],
    }))
  }, [props])

  const renderFieldLabel = useCallback((paramName: string): JSX.Element => {
    return (
      <FieldLabel
        nodeId={props.originalNodeId}
        parameterDefinitions={props.parameterDefinitions}
        paramName={paramName}
      />
    )
  }, [props.originalNodeId, props.parameterDefinitions])

  const addElement = useCallback(<K extends keyof NodeType>(property: K, element: ArrayElement<NodeType[K]>): void => {
    props.updateNodeState((currentNode) => ({
      ...currentNode,
      [property]: [...currentNode[property], element],
    }))
  }, [props])

  const setProperty = useCallback(<K extends keyof NodeType>(property: K, newValue: NodeType[K], defaultValue?: NodeType[K]): void => {
    const value = newValue == null && defaultValue != undefined ? defaultValue : newValue
    props.updateNodeState((currentNode) => {
      const node = cloneDeep(currentNode)
      return set(node, property, value)
    })
  }, [props])

  const variableTypes = useMemo(() => props.findAvailableVariables(props.originalNodeId), [props])

  switch (NodeUtils.nodeType(props.node)) {
    case "Source":
      return (
        <SourceSinkCommon
          {...props}
          {...{isMarked, renderFieldLabel, setProperty}}
        />
      )
    case "Sink":
      return (
        <SourceSinkCommon
          {...props}
          {...{isMarked, renderFieldLabel, setProperty}}
        >
          <div>
            <DisableField
              {...props}
              {...{isMarked, renderFieldLabel, setProperty}}
            />
          </div>
        </SourceSinkCommon>
      )
    case "SubprocessInputDefinition":
      return (
        <SubprocessInputDefinition
          addElement={addElement}
          onChange={setProperty}
          node={props.editedNode}
          isMarked={isMarked}
          readOnly={!props.isEditMode}
          removeElement={removeElement}
          showValidation={props.showValidation}
          renderFieldLabel={renderFieldLabel}
          errors={props.fieldErrors || []}
          variableTypes={variableTypes}
        />
      )
    case "SubprocessOutputDefinition":
      return (
        <SubprocessOutputDefinition
          renderFieldLabel={renderFieldLabel}
          removeElement={removeElement}
          onChange={setProperty}
          node={props.editedNode}
          addElement={addElement}
          isMarked={isMarked}
          readOnly={!props.isEditMode}
          showValidation={props.showValidation}
          errors={props.fieldErrors || []}

          variableTypes={variableTypes}
          expressionType={props.expressionType || props.nodeTypingInfo && {fields: props.nodeTypingInfo}}
        />
      )
    case "Filter":
      return (
        <div className="node-table-body">
          <IdField
            {...props}
            {...{isMarked, renderFieldLabel, setProperty}}
          />
          <StaticExpressionField
            {...props}
            {...{isMarked, renderFieldLabel, setProperty}}
            fieldLabel={"Expression"}
          />
          <DisableField
            {...props}
            {...{isMarked, renderFieldLabel, setProperty}}
          />
          {!isCompareView ?
            (
              <EdgesDndComponent
                label={"Outputs"}
                nodeId={props.originalNodeId}
                value={props.edges}
                onChange={(nextEdges) => props.setEdgesState(nextEdges)}
                edgeTypes={[
                  {value: EdgeKind.filterTrue, onlyOne: true},
                  {value: EdgeKind.filterFalse, onlyOne: true},
                ]}
                readOnly={!props.isEditMode}
                fieldErrors={props.fieldErrors || []}
              />
            ) :
            null}
          <DescriptionField
            {...props}
            {...{isMarked, renderFieldLabel, setProperty}}
          />
        </div>
      )
    case "Enricher":
    case "Processor":
      return (
        <div className="node-table-body">
          <IdField
            {...props}
            {...{isMarked, renderFieldLabel, setProperty}}
          />
          {serviceParameters(props.editedNode).map((param, index) => {
            return (
              <div className="node-block" key={props.node.id + param.name + index}>
                <ParameterExpressionField
                  {...props}
                  {...{isMarked, renderFieldLabel, setProperty}}
                  parameter={param}
                  listFieldPath={`service.parameters[${index}]`}
                />
              </div>
            )
          })}
          {props.node.type === "Enricher" ?
            (
              <CreateField
                {...props}
                {...{isMarked, renderFieldLabel, setProperty}}
                fieldType={FieldType.input}
                fieldLabel={"Output"}
                fieldProperty={"output"}
                validators={[errorValidator(props.fieldErrors || [], "output")]}
              />
            ) :
            null}
          {props.node.type === "Processor" ?
            (
              <DisableField
                {...props}
                {...{isMarked, renderFieldLabel, setProperty}}
              />
            ) :
            null}
          <DescriptionField
            {...props}
            {...{isMarked, renderFieldLabel, setProperty}}
          />
        </div>
      )
    case "SubprocessInput":
      return (
        <div className="node-table-body">
          <IdField
            {...props}
            {...{isMarked, renderFieldLabel, setProperty}}
          />
          <DisableField
            {...props}
            {...{isMarked, renderFieldLabel, setProperty}}
          />
          <ParameterList
            processDefinitionData={props.processDefinitionData}
            editedNode={props.editedNode}
            savedNode={props.editedNode}
            setNodeState={newParams => setProperty("ref.parameters", newParams)}
            createListField={(param, index) => (
              <ParameterExpressionField
                {...props}
                {...{isMarked, renderFieldLabel, setProperty}}
                parameter={param}
                listFieldPath={`ref.parameters[${index}]`}
              />
            )}
            createReadOnlyField={params => (
              <div className="node-row">
                {renderFieldLabel(params.name)}
                <div className="node-value">
                  <InputWithFocus
                    type="text"
                    className="node-input"
                    value={params.expression.expression}
                    disabled={true}
                  />
                </div>
              </div>
            )}
          />
          <DescriptionField
            {...props}
            {...{isMarked, renderFieldLabel, setProperty}}
          />
        </div>
      )

    case "Join":
    case "CustomNode":
      return (
        <div className="node-table-body">
          <IdField
            {...props}
            {...{isMarked, renderFieldLabel, setProperty}}
          />
          {
            hasOutputVar(props.node, props.processDefinitionData) && (
              <CreateField
                {...props}
                {...{isMarked, renderFieldLabel, setProperty}}
                fieldType={FieldType.input}
                fieldLabel={"Output variable name"}
                fieldProperty={"outputVar"}
                validators={[errorValidator(props.fieldErrors || [], "outputVar")]}
              />
            )
          }
          {NodeUtils.nodeIsJoin(props.editedNode) && (
            <BranchParameters
              node={props.editedNode}
              isMarked={isMarked}
              showValidation={props.showValidation}
              showSwitch={props.showSwitch}
              isEditMode={props.isEditMode}
              errors={props.fieldErrors || []}
              parameterDefinitions={props.parameterDefinitions}
              setNodeDataAt={setProperty}
              testResultsToShow={testResultsState.testResultsToShow}
              findAvailableVariables={props.findAvailableVariables}
            />
          )}
          {props.editedNode.parameters?.map((param, index) => {
            return (
              <div className="node-block" key={props.node.id + param.name + index}>
                <ParameterExpressionField
                  {...props}
                  {...{isMarked, renderFieldLabel, setProperty}}
                  parameter={param}
                  listFieldPath={`parameters[${index}]`}
                />
              </div>
            )
          })}
          <DescriptionField
            {...props}
            {...{isMarked, renderFieldLabel, setProperty}}
          />
        </div>
      )
    case "VariableBuilder":
      return (
        <MapVariable
          renderFieldLabel={renderFieldLabel}
          removeElement={removeElement}
          onChange={setProperty}
          node={props.editedNode}
          addElement={addElement}
          isMarked={isMarked}
          readOnly={!props.isEditMode}
          showValidation={props.showValidation}
          variableTypes={variableTypes}
          errors={props.fieldErrors || []}
          expressionType={props.expressionType || props.nodeTypingInfo && {fields: props.nodeTypingInfo}}
        />
      )
    case "Variable":
      const varExprType = props.expressionType || (props.nodeTypingInfo || {})[DEFAULT_EXPRESSION_ID]
      return (
        <Variable
          renderFieldLabel={renderFieldLabel}
          onChange={setProperty}
          node={props.editedNode}
          isMarked={isMarked}
          readOnly={!props.isEditMode}
          showValidation={props.showValidation}
          variableTypes={variableTypes}
          errors={props.fieldErrors || []}
          inferredVariableType={ProcessUtils.humanReadableType(varExprType)}
        />
      )
    case "Switch":
      const {node: definition} = props.processDefinitionData.componentGroups?.flatMap(g => g.components).find(c => c.node.type === props.editedNode.type)
      const currentExpression = props.originalNode["expression"]
      const currentExprVal = props.originalNode["exprVal"]
      const exprValValidator = errorValidator(props.fieldErrors || [], "exprVal")
      const showExpression = definition["expression"] ? !isEqual(definition["expression"], currentExpression) : currentExpression?.expression
      const showExprVal = !exprValValidator.isValid() || definition["exprVal"] ? definition["exprVal"] !== currentExprVal : currentExprVal
      return (
        <div className="node-table-body">
          <IdField
            {...props}
            {...{isMarked, renderFieldLabel, setProperty}}
          />
          {showExpression ?
            (
              <StaticExpressionField
                {...props}
                {...{isMarked, renderFieldLabel, setProperty}}
                fieldLabel={"Expression (deprecated)"}
              />
            ) :
            null}
          {showExprVal ?
            (
              <CreateField
                {...props}
                {...{isMarked, renderFieldLabel, setProperty}}
                fieldType={FieldType.input}
                fieldLabel={"exprVal (deprecated)"}
                fieldProperty={"exprVal"}
                validators={[errorValidator(props.fieldErrors || [], "exprVal")]}
              />
            ) :
            null}
          {!isCompareView ?
            (
              <EdgesDndComponent
                label={"Conditions"}
                nodeId={props.originalNodeId}
                value={props.edges}
                onChange={(nextEdges) => props.setEdgesState(nextEdges)}
                edgeTypes={[
                  {value: EdgeKind.switchNext},
                  {value: EdgeKind.switchDefault, onlyOne: true, disabled: true},
                ]}
                ordered
                readOnly={!props.isEditMode}
                variableTypes={props.editedNode["exprVal"] ?
                  {
                    ...variableTypes,
                    [props.editedNode["exprVal"]]: props.expressionType || props.nodeTypingInfo && {fields: props.nodeTypingInfo},
                  } :
                  variableTypes}
                fieldErrors={props.fieldErrors || []}
              />
            ) :
            null}
          <DescriptionField
            {...props}
            {...{isMarked, renderFieldLabel, setProperty}}
          />
        </div>
      )
    case "Split":
      return (
        <div className="node-table-body">
          <IdField
            {...props}
            {...{isMarked, renderFieldLabel, setProperty}}
          />
          <DescriptionField
            {...props}
            {...{isMarked, renderFieldLabel, setProperty}}
          />
        </div>
      )
    case "Properties":
      const type = props.node.typeSpecificProperties.type
      //fixme move this configuration to some better place?
      //we sort by name, to have predictable order of properties (should be replaced by defining order in configuration)
      return (
        <div className="node-table-body">
          <IdField
            {...props}
            {...{isMarked, renderFieldLabel, setProperty}}
          />
          {props.node.isSubprocess ?
            (
              <CreateField
                {...props}
                {...{isMarked, renderFieldLabel, setProperty}}
                fieldType={FieldType.input}
                fieldLabel={"Documentation url"}
                fieldProperty={"typeSpecificProperties.docsUrl"}
                validators={[errorValidator(props.fieldErrors || [], "docsUrl")]}
                autoFocus
              />
            ) :
            type === "StreamMetaData" ?
              (
                <>
                  <CreateField
                    {...props}
                    {...{isMarked, renderFieldLabel, setProperty}}
                    fieldType={FieldType.input}
                    fieldLabel={"Parallelism"}
                    fieldProperty={"typeSpecificProperties.parallelism"}
                    validators={[errorValidator(props.fieldErrors || [], "parallelism")]}
                    autoFocus
                  />
                  <CreateField
                    {...props}
                    {...{isMarked, renderFieldLabel, setProperty}}
                    fieldType={FieldType.input}
                    fieldLabel={"Checkpoint interval in seconds"}
                    fieldProperty={"typeSpecificProperties.checkpointIntervalInSeconds"}
                    validators={[errorValidator(props.fieldErrors || [], "checkpointIntervalInSeconds")]}
                  />
                  <CreateField
                    {...props}
                    {...{isMarked, renderFieldLabel, setProperty}}
                    fieldType={FieldType.checkbox}
                    fieldLabel={"Spill state to disk"}
                    fieldProperty={"typeSpecificProperties.spillStateToDisk"}
                    validators={[errorValidator(props.fieldErrors || [], "spillStateToDisk")]}
                  />
                  <CreateField
                    {...props}
                    {...{isMarked, renderFieldLabel, setProperty}}
                    fieldType={FieldType.checkbox}
                    fieldLabel={"Should use async interpretation"}
                    fieldProperty={"typeSpecificProperties.useAsyncInterpretation"}
                    validators={[errorValidator(props.fieldErrors || [], "useAsyncInterpretation")]}
                    defaultValue={props.processDefinitionData.defaultAsyncInterpretation}
                  />
                </>
              ) :
              type === "LiteStreamMetaData" ?
                (
                  <CreateField
                    {...props}
                    {...{isMarked, renderFieldLabel, setProperty}}
                    fieldType={FieldType.input}
                    fieldLabel={"Parallelism"}
                    fieldProperty={"typeSpecificProperties.parallelism"}
                    validators={[errorValidator(props.fieldErrors || [], "parallelism")]}
                    autoFocus
                  />
                ) :
                (
                  <CreateField
                    {...props}
                    {...{isMarked, renderFieldLabel, setProperty}}
                    fieldType={FieldType.input}
                    fieldLabel={"Query path"}
                    fieldProperty={"typeSpecificProperties.path"}
                    validators={[errorValidator(props.fieldErrors || [], "path")]}
                  />
                )
          }
          {sortBy(Object.entries(props.additionalPropertiesConfig), ([name]) => name).map(([propName, propConfig]) => (
            <AdditionalProperty
              key={propName}
              showSwitch={props.showSwitch}
              showValidation={props.showValidation}
              propertyName={propName}
              propertyConfig={propConfig}
              propertyErrors={props.fieldErrors || []}
              onChange={setProperty}
              renderFieldLabel={renderFieldLabel}
              editedNode={props.editedNode}
              readOnly={!props.isEditMode}
            />
          ))}
          <DescriptionField
            {...props}
            {...{isMarked, renderFieldLabel, setProperty}}
          />
        </div>
      )
    default:
      return (
        <div>
          Node type not known.
          <NodeDetails node={props.node}/>
        </div>
      )
  }
}
