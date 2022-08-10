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
import {cloneDeep, get, has, isEqual, partition, set, sortBy, startsWith} from "lodash"
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

export interface NodeDetailsContentProps3 {
  isEditMode?: boolean,
  dynamicParameterDefinitions?: UIParameter[],
  currentErrors?: NodeValidationError[],
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

  publishNodeChange,
  setEdgesState,
  updateNodeState,
  testResultsState,
}

export function NodeDetailsContent3(props: NodeDetailsContentProps3): JSX.Element {
  const {
    setEdgesState,
    updateNodeState,
    pathsToMark,
    currentErrors = [],
    editedNode,
    parameterDefinitions,
    showSwitch: showSwitch1,
    findAvailableVariables: findAvailableVariables1,
    originalNodeId: originalNodeId1,
    isEditMode: isEditMode1,
    showValidation: showValidation1,
    testResultsState,
  } = props

  const [fieldErrors, otherErrors] = useMemo(() => partition(currentErrors, error => !!error.fieldName), [currentErrors])

  const isMarked = useCallback((path: string): boolean => {
    return pathsToMark?.some(toMark => startsWith(toMark, path))
  }, [pathsToMark])

  const removeElement = useCallback((property: string, index: number): void => {
    if (has(editedNode, property)) {
      const node = cloneDeep(editedNode)
      get(node, property).splice(index, 1)

      updateNodeState(() => ({editedNode: node}))
    }
  }, [editedNode, updateNodeState])

  const renderFieldLabel = useCallback((paramName: string): JSX.Element => {
    return (
      <FieldLabel
        nodeId={originalNodeId1}
        parameterDefinitions={parameterDefinitions}
        paramName={paramName}
      />
    )
  }, [originalNodeId1, parameterDefinitions])

  const addElement = useCallback(<T extends unknown>(property: string, element: T): void => {
    updateNodeState((editedNode) => {
      const elements = editedNode[property]
      return {editedNode: {...editedNode, [property]: [...elements, element]}}
    })
  }, [updateNodeState])

  const setNodeDataAt = useCallback(<T extends unknown>(propToMutate: string, newValue: T, defaultValue?: T): void => {
    const value = newValue == null && defaultValue != undefined ? defaultValue : newValue
    updateNodeState((current) => {
      const node = cloneDeep(current)
      return {editedNode: set(node, propToMutate, value)}
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
        isEditMode={isEditMode1}
        editedNode={editedNode}
        isMarked={isMarked}
        showValidation={showValidation1}
        showSwitch={showSwitch1}
        parameterDefinition={findParamDefinitionByName(parameterDefinitions, fieldName)}
        setNodeDataAt={setNodeDataAt}
        testResultsToShow={testResultsState.testResultsToShow}
        renderFieldLabel={renderFieldLabel}
        variableTypes={findAvailableVariables1(originalNodeId1, undefined)}
        errors={fieldErrors}
      />
    )
  }, [editedNode, findAvailableVariables1, isEditMode1, isMarked, originalNodeId1, parameterDefinitions, renderFieldLabel, setNodeDataAt, showSwitch1, showValidation1])

  const idField1 = useCallback((): JSX.Element => (
    <IdField
      isMarked={isMarked("id")}
      isEditMode={isEditMode1}
      showValidation={showValidation1}
      editedNode={editedNode}
      onChange={(newValue) => setNodeDataAt("id", newValue)}
    >
      {renderFieldLabel("Name")}
    </IdField>
  ), [editedNode, isEditMode1, isMarked, renderFieldLabel, setNodeDataAt, showValidation1])

  const {isEditMode, showValidation} = props
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

  const descriptionField = useCallback((): JSX.Element => {
    return createField({
      fieldType: FieldType.plainTextarea,
      fieldLabel: "Description",
      fieldProperty: "additionalFields.description",
    })
  }, [createField])

  //this is for "dynamic" parameters in sources, sinks, services etc.
  const createParameterExpressionField = useCallback(({
      parameter,
      expressionProperty,
      listFieldPath,
      fieldErrors,
      testResultsState,
    }: { parameter: Parameter, expressionProperty: string, listFieldPath: string, fieldErrors: Error[], testResultsState: StateForSelectTestResults }): JSX.Element => {
      return (
        <ExpressionField
          fieldName={parameter.name}
          fieldLabel={parameter.name}
          exprPath={`${listFieldPath}.${expressionProperty}`}
          isEditMode={isEditMode1}
          editedNode={editedNode}
          isMarked={isMarked}
          showValidation={showValidation1}
          showSwitch={showSwitch1}
          parameterDefinition={findParamDefinitionByName(parameterDefinitions, parameter.name)}
          setNodeDataAt={setNodeDataAt}
          testResultsToShow={testResultsState.testResultsToShow}
          renderFieldLabel={renderFieldLabel}
          variableTypes={findAvailableVariables1(originalNodeId1, parameterDefinitions?.find(p => p.name === parameter.name))}
          errors={fieldErrors}
        />
      )
    }
    , [editedNode, findAvailableVariables1, isEditMode1, isMarked, originalNodeId1, parameterDefinitions, renderFieldLabel, setNodeDataAt, showSwitch1, showValidation1])

  const sourceSinkCommon = useCallback(({
    fieldErrors,
    children,
    testResultsState,
  }: { fieldErrors: Error[], children?: JSX.Element, testResultsState: StateForSelectTestResults }): JSX.Element => {
    return (
      <div className="node-table-body">
        {idField1()}
        {refParameters(editedNode).map((param, index) => (
          <div className="node-block" key={props.node.id + param.name + index}>
            {createParameterExpressionField(
              {
                parameter: param,
                expressionProperty: "expression",
                listFieldPath: `ref.parameters[${index}]`,
                fieldErrors: fieldErrors,
                testResultsState,
              }
            )}
          </div>
        ))}
        {children}
        {descriptionField()}
      </div>
    )
  }, [createParameterExpressionField, descriptionField, editedNode, idField1, props.node.id])

  const {
    edges,
    originalNode,
    findAvailableVariables,
    originalNodeId,
    node,
    expressionType,
    nodeTypingInfo,
    processDefinitionData,
    showSwitch,
    additionalPropertiesConfig,
  } = props

  const customNode = useCallback((fieldErrors: Error[], testResultsState: StateForSelectTestResults): JSX.Element => {

      const {testResultsToShow} = testResultsState

      const variableTypes = findAvailableVariables(originalNodeId)
      //compare window uses legacy egde component
      const isCompareView = pathsToMark !== undefined

      switch (NodeUtils.nodeType(node)) {
        case "Source":
          return sourceSinkCommon({fieldErrors, testResultsState})
        case "Sink":
          const toAppend: JSX.Element = (
            <div>
              {createField({
                fieldType: FieldType.checkbox,
                fieldLabel: "Disabled",
                fieldProperty: "isDisabled",
              })}
            </div>
          )
          return sourceSinkCommon({fieldErrors, children: toAppend, testResultsState})
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
              {idField1()}
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
              {descriptionField()}
            </div>
          )
        case "Enricher":
        case "Processor":
          return (
            <div className="node-table-body">
              {idField1()}
              {serviceParameters(editedNode).map((param, index) => {
                return (
                  <div className="node-block" key={node.id + param.name + index}>
                    {createParameterExpressionField(
                      {
                        parameter: param,
                        expressionProperty: "expression",
                        listFieldPath: `service.parameters[${index}]`,
                        fieldErrors: fieldErrors,
                        testResultsState,
                      }
                    )}
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
              {descriptionField()}
            </div>
          )
        case "SubprocessInput":
          return (
            <div className="node-table-body">
              {idField1()}
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
                createListField={(param, index) => createParameterExpressionField(
                  {
                    parameter: param,
                    expressionProperty: "expression",
                    listFieldPath: `ref.parameters[${index}]`,
                    fieldErrors: fieldErrors,
                    testResultsState,
                  }
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
              {descriptionField()}
            </div>
          )

        case "Join":
        case "CustomNode":
          return (
            <div className="node-table-body">
              {idField1()}
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
                  testResultsToShow={testResultsToShow}
                  findAvailableVariables={findAvailableVariables}
                />
              )}
              {editedNode.parameters?.map((param, index) => {
                return (
                  <div className="node-block" key={node.id + param.name + index}>
                    {createParameterExpressionField(
                      {
                        parameter: param,
                        expressionProperty: "expression",
                        listFieldPath: `parameters[${index}]`,
                        fieldErrors: fieldErrors,
                        testResultsState,
                      }
                    )}
                  </div>
                )
              })}
              {descriptionField()}
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
              {idField1()}
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
              {descriptionField()}
            </div>
          )
        case "Split":
          return (
            <div className="node-table-body">
              {idField1()}
              {descriptionField()}
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
              {idField1()}
              {[...fields, ...additionalFields]}
              {descriptionField()}
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
    , [addElement, additionalPropertiesConfig, createField, createParameterExpressionField, createStaticExpressionField, descriptionField, edges, editedNode, expressionType, findAvailableVariables, idField1, isEditMode, isMarked, node, nodeTypingInfo, originalNode, originalNodeId, parameterDefinitions, pathsToMark, processDefinitionData, removeElement, renderFieldLabel, setEdgesState, setNodeDataAt, showSwitch, showValidation, sourceSinkCommon])

  return (
    <>
      {customNode(fieldErrors, testResultsState || {})}
    </>
  )
}
