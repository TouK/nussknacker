/* eslint-disable i18next/no-literal-string */
import {
  Edge,
  EdgeKind,
  NodeType,
  NodeValidationError,
  Parameter,
  ProcessDefinitionData,
  ProcessId,
  UIParameter,
  VariableTypes,
} from "../../../types"
import AdditionalProperty, {AdditionalPropertyConfig} from "./AdditionalProperty"
import ProcessUtils from "../../../common/ProcessUtils"
import {UserSettings} from "../../../reducers/userSettings"
import {DispatchWithCallback, hasOutputVar, IdField} from "./NodeDetailsContentUtils"
import React, {SetStateAction, useCallback, useMemo} from "react"
import {get, isEqual, set, sortBy, startsWith} from "lodash"
import {FieldLabel, findParamDefinitionByName} from "./FieldLabel"
import {allValid, Error, errorValidator, Validator} from "./editors/Validators"
import {StateForSelectTestResults} from "../../../common/TestResultUtils"
import ExpressionField from "./editors/expression/ExpressionField"
import Field, {FieldType} from "./editors/field/Field"
import {refParameters, serviceParameters} from "./NodeDetailsContent/helpers"
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

type UpdateState<T> = (updateState: (currentState: T) => T) => void

export interface NodeDetailsContentProps3 {
  fieldErrors?: NodeValidationError[],
  isEditMode?: boolean,
  dynamicParameterDefinitions?: UIParameter[],
  processId?: ProcessId,
  additionalPropertiesConfig?: Record<string, AdditionalPropertyConfig>,
  showValidation?: boolean,
  showSwitch?: boolean,
  findAvailableVariables?: ReturnType<typeof ProcessUtils.findAvailableVariables>,
  processDefinitionData?: ProcessDefinitionData,
  node: NodeType,
  edges?: Edge[],
  expressionType?,
  originalNodeId?: NodeType["id"],
  nodeTypingInfo?,
  findAvailableBranchVariables?,
  processProperties?,
  pathsToMark?: string[],
  onChange?: (node: NodeType, outputEdges?: Edge[]) => void,
  variableTypes?: VariableTypes,
  userSettings: UserSettings,

  parameterDefinitions: UIParameter[],
  originalNode: NodeType,
  editedNode: NodeType,
  setEditedNode: DispatchWithCallback<SetStateAction<NodeType>>,
  updateNodeState: UpdateState<NodeType>,

  publishNodeChange,
  setEdgesState,
  testResultsState,
}

export function NodeDetailsContent3(props: NodeDetailsContentProps3): JSX.Element {
  const {
    fieldErrors = [],
    additionalPropertiesConfig,
    edges,
    editedNode,
    expressionType,
    findAvailableVariables,
    isEditMode,
    node,
    nodeTypingInfo,
    originalNode,
    originalNodeId,
    parameterDefinitions,
    pathsToMark,
    processDefinitionData,
    setEdgesState,
    showSwitch,
    showValidation,
    testResultsState,
    updateNodeState,
  } = props

  const isMarked = useCallback((path = ""): boolean => {
    return pathsToMark?.some(toMark => startsWith(toMark, path))
  }, [pathsToMark])

  //compare window uses legacy egde component
  const isCompareView = useMemo(() => isMarked(), [isMarked])

  const removeElement = useCallback((property: string, index: number): void => {
    updateNodeState((currentNode) => ({
      ...currentNode,
      [property]: currentNode[property].filter((_, i) => i !== index),
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

  const addElement = useCallback(<T extends unknown>(property: string, element: T): void => {
    updateNodeState((editedNode) => {
      const elements = editedNode[property]
      return {...editedNode, [property]: [...elements, element]}
    })
  }, [updateNodeState])

  const setNodeDataAt = useCallback(<T extends unknown>(propToMutate: string, newValue: T, defaultValue?: T): void => {
    const value = newValue == null && defaultValue != undefined ? defaultValue : newValue
    updateNodeState((currentNode) => {
      return set(currentNode, propToMutate, value)
    })
  }, [updateNodeState])

  //this is for "static" fields like expressions in filters, switches etc.
  const createStaticExpressionField = useCallback(({
    fieldName,
    fieldLabel,
    expressionProperty,
    fieldErrors,
    testResultsState,
  }: { fieldName: string, fieldLabel: string, expressionProperty: string, fieldErrors: Error[], testResultsState?: StateForSelectTestResults }): JSX.Element => {
    return (
      <ExpressionField
        fieldName={fieldName}
        fieldLabel={fieldLabel}
        exprPath={`${expressionProperty}`}
        isEditMode={isEditMode}
        editedNode={editedNode}
        isMarked={isMarked}
        showValidation={showValidation}
        showSwitch={showSwitch}
        parameterDefinition={findParamDefinitionByName(parameterDefinitions, fieldName)}
        setNodeDataAt={setNodeDataAt}
        testResultsToShow={testResultsState.testResultsToShow}
        renderFieldLabel={renderFieldLabel}
        variableTypes={findAvailableVariables(originalNodeId, undefined)}
        errors={fieldErrors}
      />
    )
  }, [editedNode, findAvailableVariables, isEditMode, isMarked, originalNodeId, parameterDefinitions, renderFieldLabel, setNodeDataAt, showSwitch, showValidation])

  const idField = useMemo(() => (
    <IdField
      isMarked={isMarked("id")}
      isEditMode={isEditMode}
      showValidation={showValidation}
      editedNode={editedNode}
      onChange={(newValue) => setNodeDataAt("id", newValue)}
    >
      {renderFieldLabel("Name")}
    </IdField>
  ), [editedNode, isEditMode, isMarked, renderFieldLabel, setNodeDataAt, showValidation])

  const createField = useCallback(<K extends keyof NodeType & string, T extends NodeType[K]>({
    fieldType,
    fieldLabel,
    fieldProperty,
    autoFocus,
    readonly,
    defaultValue,
    validators = [],
  }: {
    fieldType: FieldType,
    fieldLabel: string,
    fieldProperty: K,
    autoFocus?: boolean,
    readonly?: boolean,
    defaultValue?: T,
    validators?: Validator[],
  }): JSX.Element => {
    const readOnly = !isEditMode || readonly
    const value: T = get(editedNode, fieldProperty, null) ?? defaultValue
    const className = !showValidation || allValid(validators, [value]) ? "node-input" : "node-input node-input-with-error"
    const onChange = (newValue) => setNodeDataAt(fieldProperty, newValue, defaultValue)
    return (
      <Field
        type={fieldType}
        isMarked={isMarked(fieldProperty)}
        readOnly={readOnly}
        showValidation={showValidation}
        autoFocus={autoFocus}
        className={className}
        validators={validators}
        value={value}
        onChange={onChange}
      >
        {renderFieldLabel(fieldLabel)}
      </Field>
    )
  }, [editedNode, isEditMode, isMarked, renderFieldLabel, setNodeDataAt, showValidation])

  const DescriptionField = useCallback((): JSX.Element => {
    return createField({
      fieldType: FieldType.plainTextarea,
      fieldLabel: "Description",
      fieldProperty: "additionalFields.description",
    })
  }, [createField])

  //this is for "dynamic" parameters in sources, sinks, services etc.
  const ParameterExpressionField = useCallback(({
      parameter,
      listFieldPath,
      fieldErrors,
      testResultsState,
      expressionProperty = "expression",
    }: { parameter: Parameter, expressionProperty?: string, listFieldPath: string, fieldErrors: Error[], testResultsState: StateForSelectTestResults }): JSX.Element => {
      return (
        <ExpressionField
          fieldName={parameter.name}
          fieldLabel={parameter.name}
          exprPath={`${listFieldPath}.${expressionProperty}`}
          isEditMode={isEditMode}
          editedNode={editedNode}
          isMarked={isMarked}
          showValidation={showValidation}
          showSwitch={showSwitch}
          parameterDefinition={findParamDefinitionByName(parameterDefinitions, parameter.name)}
          setNodeDataAt={setNodeDataAt}
          testResultsToShow={testResultsState.testResultsToShow}
          renderFieldLabel={renderFieldLabel}
          variableTypes={findAvailableVariables(originalNodeId, parameterDefinitions?.find(p => p.name === parameter.name))}
          errors={fieldErrors}
        />
      )
    }
    , [editedNode, findAvailableVariables, isEditMode, isMarked, originalNodeId, parameterDefinitions, renderFieldLabel, setNodeDataAt, showSwitch, showValidation])

  const SourceSinkCommon = useCallback(({
    fieldErrors,
    children,
    testResultsState,
  }: { fieldErrors: Error[], children?: JSX.Element, testResultsState: StateForSelectTestResults }): JSX.Element => {
    return (
      <div className="node-table-body">
        {idField}
        {refParameters(editedNode).map((param, index) => {
          return (
            <div className="node-block" key={node.id + param.name + index}>
              <ParameterExpressionField
                parameter={param}
                listFieldPath={`ref.parameters[${index}]`}

                expressionProperty={"expression"}
                fieldErrors={fieldErrors}
                testResultsState={testResultsState}
              />
            </div>
          )
        })}
        {children}
        <DescriptionField/>
      </div>
    )
  }, [DescriptionField, ParameterExpressionField, editedNode, idField, node.id])

  const variableTypes = useMemo(() => findAvailableVariables(originalNodeId), [findAvailableVariables, originalNodeId])

  switch (NodeUtils.nodeType(node)) {
    case "Source":
      return <SourceSinkCommon fieldErrors={fieldErrors} testResultsState={testResultsState}/>
    case "Sink":
      return (
        <SourceSinkCommon fieldErrors={fieldErrors} testResultsState={testResultsState}>
          {(
            <div>
              {createField({
                fieldType: FieldType.checkbox,
                fieldLabel: "Disabled",
                fieldProperty: "isDisabled",
              })}
            </div>
          )}
        </SourceSinkCommon>
      )
    case "SubprocessInputDefinition":
      return (
        <SubprocessInputDefinition
          addElement={addElement}
          onChange={setNodeDataAt}
          node={editedNode}
          isMarked={isMarked}
          readOnly={!isEditMode}
          removeElement={removeElement}
          showValidation={showValidation}
          renderFieldLabel={renderFieldLabel}
          errors={fieldErrors}
          variableTypes={variableTypes}
        />
      )
    case "SubprocessOutputDefinition":
      return (
        <SubprocessOutputDefinition
          renderFieldLabel={renderFieldLabel}
          removeElement={removeElement}
          onChange={setNodeDataAt}
          node={editedNode}
          addElement={addElement}
          isMarked={isMarked}
          readOnly={!isEditMode}
          showValidation={showValidation}
          errors={fieldErrors}

          variableTypes={variableTypes}
          expressionType={expressionType || nodeTypingInfo && {fields: nodeTypingInfo}}
        />
      )
    case "Filter":
      return (
        <div className="node-table-body">
          {idField}
          {createStaticExpressionField(
            {
              fieldName: "expression",
              fieldLabel: "Expression",
              expressionProperty: "expression",
              fieldErrors: fieldErrors,
              testResultsState,
            }
          )}
          {createField({
            fieldType: FieldType.checkbox,
            fieldLabel: "Disabled",
            fieldProperty: "isDisabled",
          })}
          {!isCompareView ?
            (
              <EdgesDndComponent
                label={"Outputs"}
                nodeId={originalNodeId}
                value={edges}
                onChange={(nextEdges) => setEdgesState(nextEdges)}
                edgeTypes={[
                  {value: EdgeKind.filterTrue, onlyOne: true},
                  {value: EdgeKind.filterFalse, onlyOne: true},
                ]}
                readOnly={!isEditMode}
                fieldErrors={fieldErrors}
              />
            ) :
            null}
          <DescriptionField/>
        </div>
      )
    case "Enricher":
    case "Processor":
      return (
        <div className="node-table-body">
          {idField}
          {serviceParameters(editedNode).map((param, index) => {
            return (
              <div className="node-block" key={node.id + param.name + index}>
                <ParameterExpressionField
                  parameter={param}
                  listFieldPath={`service.parameters[${index}]`}

                  expressionProperty={"expression"}
                  fieldErrors={fieldErrors}
                  testResultsState={testResultsState}
                />
              </div>
            )
          })}
          {node.type === "Enricher" ?
            createField({
              fieldType: FieldType.input,
              fieldLabel: "Output",
              fieldProperty: "output",
              validators: [errorValidator(fieldErrors, "output")],
            }) :
            null}
          {node.type === "Processor" ?
            createField({
              fieldType: FieldType.checkbox,
              fieldLabel: "Disabled",
              fieldProperty: "isDisabled",
            }) :
            null}
          <DescriptionField/>
        </div>
      )
    case "SubprocessInput":
      return (
        <div className="node-table-body">
          {idField}
          {createField({
            fieldType: FieldType.checkbox,
            fieldLabel: "Disabled",
            fieldProperty: "isDisabled",
          })}
          <ParameterList
            processDefinitionData={processDefinitionData}
            editedNode={editedNode}
            savedNode={editedNode}
            setNodeState={newParams => setNodeDataAt("ref.parameters", newParams)}
            createListField={(param, index) => (
              <ParameterExpressionField
                parameter={param}
                listFieldPath={`ref.parameters[${index}]`}

                expressionProperty={"expression"}
                fieldErrors={fieldErrors}
                testResultsState={testResultsState}
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
          <DescriptionField/>
        </div>
      )

    case "Join":
    case "CustomNode":
      return (
        <div className="node-table-body">
          {idField}
          {
            hasOutputVar(node, processDefinitionData) && createField({
              fieldType: FieldType.input,
              fieldLabel: "Output variable name",
              fieldProperty: "outputVar",
              validators: [errorValidator(fieldErrors, "outputVar")],
            })
          }
          {NodeUtils.nodeIsJoin(editedNode) && (
            <BranchParameters
              node={editedNode}
              isMarked={isMarked}
              showValidation={showValidation}
              showSwitch={showSwitch}
              isEditMode={isEditMode}
              errors={fieldErrors}
              parameterDefinitions={parameterDefinitions}
              setNodeDataAt={setNodeDataAt}
              testResultsToShow={testResultsState.testResultsToShow}
              findAvailableVariables={findAvailableVariables}
            />
          )}
          {editedNode.parameters?.map((param, index) => {
            return (
              <div className="node-block" key={node.id + param.name + index}>
                <ParameterExpressionField
                  parameter={param}
                  listFieldPath={`parameters[${index}]`}

                  expressionProperty={"expression"}
                  fieldErrors={fieldErrors}
                  testResultsState={testResultsState}
                />
              </div>
            )
          })}
          <DescriptionField/>
        </div>
      )
    case "VariableBuilder":
      return (
        <MapVariable
          renderFieldLabel={renderFieldLabel}
          removeElement={removeElement}
          onChange={setNodeDataAt}
          node={editedNode}
          addElement={addElement}
          isMarked={isMarked}
          readOnly={!isEditMode}
          showValidation={showValidation}
          variableTypes={variableTypes}
          errors={fieldErrors}
          expressionType={expressionType || nodeTypingInfo && {fields: nodeTypingInfo}}
        />
      )
    case "Variable":
      const varExprType = expressionType || (nodeTypingInfo || {})[DEFAULT_EXPRESSION_ID]
      return (
        <Variable
          renderFieldLabel={renderFieldLabel}
          onChange={setNodeDataAt}
          node={editedNode}
          isMarked={isMarked}
          readOnly={!isEditMode}
          showValidation={showValidation}
          variableTypes={variableTypes}
          errors={fieldErrors}
          inferredVariableType={ProcessUtils.humanReadableType(varExprType)}
        />
      )
    case "Switch":
      const {node: definition} = processDefinitionData.componentGroups?.flatMap(g => g.components).find(c => c.node.type === editedNode.type)
      const currentExpression = originalNode["expression"]
      const currentExprVal = originalNode["exprVal"]
      const exprValValidator = errorValidator(fieldErrors, "exprVal")
      const showExpression = definition["expression"] ? !isEqual(definition["expression"], currentExpression) : currentExpression?.expression
      const showExprVal = !exprValValidator.isValid() || definition["exprVal"] ? definition["exprVal"] !== currentExprVal : currentExprVal
      return (
        <div className="node-table-body">
          {idField}
          {showExpression ?
            createStaticExpressionField({
              fieldName: "expression",
              fieldLabel: "Expression (deprecated)",
              expressionProperty: "expression",
              fieldErrors: fieldErrors,
              testResultsState,
            }) :
            null}
          {showExprVal ?
            createField({
              fieldType: FieldType.input,
              fieldLabel: "exprVal (deprecated)",
              fieldProperty: "exprVal",
              validators: [errorValidator(fieldErrors, "exprVal")],
            }) :
            null}
          {!isCompareView ?
            (
              <EdgesDndComponent
                label={"Conditions"}
                nodeId={originalNodeId}
                value={edges}
                onChange={(nextEdges) => setEdgesState(nextEdges)}
                edgeTypes={[
                  {value: EdgeKind.switchNext},
                  {value: EdgeKind.switchDefault, onlyOne: true, disabled: true},
                ]}
                ordered
                readOnly={!isEditMode}
                variableTypes={editedNode["exprVal"] ?
                  {
                    ...variableTypes,
                    [editedNode["exprVal"]]: expressionType || nodeTypingInfo && {fields: nodeTypingInfo},
                  } :
                  variableTypes}
                fieldErrors={fieldErrors}
              />
            ) :
            null}
          <DescriptionField/>
        </div>
      )
    case "Split":
      return (
        <div className="node-table-body">
          {idField}
          <DescriptionField/>
        </div>
      )
    case "Properties":
      const type = node.typeSpecificProperties.type
      //fixme move this configuration to some better place?
      const fields =
        node.isSubprocess ?
          [
            createField({
              fieldType: FieldType.input,
              fieldLabel: "Documentation url",
              fieldProperty: "typeSpecificProperties.docsUrl",
              autoFocus: true,
              validators: [errorValidator(fieldErrors, "docsUrl")],
            }),
          ] :
          type === "StreamMetaData" ?
            [
              createField({
                fieldType: FieldType.input,
                fieldLabel: "Parallelism",
                fieldProperty: "typeSpecificProperties.parallelism",
                autoFocus: true,
                validators: [errorValidator(fieldErrors, "parallelism")],
              }),
              createField({
                fieldType: FieldType.input,
                fieldLabel: "Checkpoint interval in seconds",
                fieldProperty: "typeSpecificProperties.checkpointIntervalInSeconds",
                validators: [errorValidator(fieldErrors, "checkpointIntervalInSeconds")],
              }),
              createField({
                fieldType: FieldType.checkbox,
                fieldLabel: "Spill state to disk",
                fieldProperty: "typeSpecificProperties.spillStateToDisk",
                validators: [errorValidator(fieldErrors, "spillStateToDisk")],
              }),
              createField({
                fieldType: FieldType.checkbox,
                fieldLabel: "Should use async interpretation",
                fieldProperty: "typeSpecificProperties.useAsyncInterpretation",
                validators: [errorValidator(fieldErrors, "useAsyncInterpretation")],
                defaultValue: processDefinitionData.defaultAsyncInterpretation,
              }),
            ] :
            type === "LiteStreamMetaData" ?
              [
                createField({
                  fieldType: FieldType.input,
                  fieldLabel: "Parallelism",
                  fieldProperty: "typeSpecificProperties.parallelism",
                  autoFocus: true,
                  validators: [errorValidator(fieldErrors, "parallelism")],
                }),
              ] :
              [createField({
                fieldType: FieldType.input,
                fieldLabel: "Query path",
                fieldProperty: "typeSpecificProperties.path",
                validators: [errorValidator(fieldErrors, "path")],
              })]
      //we sort by name, to have predictable order of properties (should be replaced by defining order in configuration)
      const additionalFields = sortBy(Object.entries(additionalPropertiesConfig), e => e[0]).map(
        ([propName, propConfig]) => (
          <AdditionalProperty
            key={propName}
            showSwitch={showSwitch}
            showValidation={showValidation}
            propertyName={propName}
            propertyConfig={propConfig}
            propertyErrors={fieldErrors}
            onChange={setNodeDataAt}
            renderFieldLabel={renderFieldLabel}
            editedNode={editedNode}
            readOnly={!isEditMode}
          />
        )
      )
      return (
        <div className="node-table-body">
          {idField}
          {[...fields, ...additionalFields]}
          <DescriptionField/>
        </div>
      )
    default:
      return (
        <div>
          Node type not known.
          <NodeDetails node={node}/>
        </div>
      )
  }
}
