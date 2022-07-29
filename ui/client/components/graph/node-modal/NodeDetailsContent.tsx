/* eslint-disable i18next/no-literal-string */
import {cloneDeep, get, has, isEqual, partition, set, sortBy, startsWith} from "lodash"
import React, {PropsWithChildren} from "react"
import {v4 as uuid4} from "uuid"
import {DEFAULT_EXPRESSION_ID} from "../../../common/graph/constants"
import ProcessUtils from "../../../common/ProcessUtils"
import TestResultUtils, {TestResults} from "../../../common/TestResultUtils"
import {InputWithFocus} from "../../withFocus"
import NodeUtils from "../NodeUtils"
import MapVariable from "./../node-modal/MapVariable"
import AdditionalProperty, {AdditionalPropertyConfig} from "./AdditionalProperty"
import BranchParameters from "./BranchParameters"
import ExpressionField from "./editors/expression/ExpressionField"
import Field, {FieldType} from "./editors/field/Field"
import {allValid, Error, errorValidator, mandatoryValueValidator, Validator} from "./editors/Validators"
import NodeAdditionalInfoBox from "./NodeAdditionalInfoBox"
import NodeErrors from "./NodeErrors"
import ParameterList from "./ParameterList"
import {adjustParameters} from "./ParametersUtils"
import SubprocessInputDefinition from "./subprocess-input-definition/SubprocessInputDefinition"
import SubprocessOutputDefinition from "./SubprocessOutputDefinition"
import TestErrors from "./tests/TestErrors"
import TestResultsComponent from "./tests/TestResults"
import TestResultsSelect from "./tests/TestResultsSelect"
import Variable from "./Variable"
import {refParameters, serviceParameters} from "./NodeDetailsContent/helpers"
import {EdgesDndComponent, WithTempId} from "./EdgesDndComponent"
import {NodeDetails} from "./NodeDetailsContent/NodeDetails"
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
import {UserSettings} from "../../../reducers/userSettings"
import {ValidationRequest} from "../../../actions/nk"
import {FieldLabel, findParamDefinitionByName} from "./FieldLabel"

export interface NodeDetailsContentProps {
  testResults?,
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
  updateNodeData?: (processId: string, validationRequestData: ValidationRequest) => void,
  findAvailableBranchVariables?,
  processProperties?,
  pathsToMark?: string[],
  onChange?: (node: NodeType, outputEdges?: Edge[]) => void,
  variableTypes?: VariableTypes,
  userSettings: UserSettings,
}

interface State {
  testResultsToShow,
  testResultsToHide,
  testResultsIdToShow,
  editedNode: NodeType,
  originalNode: NodeType,
  unusedParameters,
  codeCompletionEnabled,
  edges: WithTempId<Edge>[],
}

function getParameterDefinitions(processDefinitionData: ProcessDefinitionData, node: NodeType, dynamicParameterDefinitions?: UIParameter[]): UIParameter[] {
  return dynamicParameterDefinitions || ProcessUtils.findNodeObjectTypeDefinition(node, processDefinitionData.processDefinition)?.parameters
}

function hasOutputVar(node: NodeType, processDefinitionData: ProcessDefinitionData): boolean {
  const returnType = ProcessUtils.findNodeObjectTypeDefinition(node, processDefinitionData.processDefinition)?.returnType
  return !!returnType || !!node.outputVar
}

function Wrapper({
  otherErrors,
  testResults,
  testResultsIdToShow,
  selectResults,
  testResultsToShow,
  children,
  node,
  editedNode,
  processId,
}: PropsWithChildren<{
  node: NodeType,
  editedNode: NodeType,
  processId: ProcessId,
  otherErrors: NodeValidationError[],
  testResults: TestResults,
  testResultsIdToShow: any,
  selectResults: (id?, testResults?: TestResults) => void,
  testResultsToShow: any,
}>): JSX.Element {
  return (
    <>
      <NodeErrors errors={otherErrors} message={"Node has errors"}/>
      <TestResultsSelect
        results={testResults}
        resultsIdToShow={testResultsIdToShow}
        selectResults={selectResults}
      />
      <TestErrors resultsToShow={testResultsToShow}/>
      {children}
      <TestResultsComponent nodeId={node.id} resultsToShow={testResultsToShow}/>

      <NodeAdditionalInfoBox node={editedNode} processId={processId}/>
    </>
  )
}

// here `componentDidUpdate` is complicated to clear unsaved changes in modal
export class NodeDetailsContent extends React.Component<NodeDetailsContentProps, State> {
  parameterDefinitions: UIParameter[]

  constructor(props: NodeDetailsContentProps) {
    super(props)

    this.initalizeWithProps(props)
    const {edges, testResults, node, isEditMode, dynamicParameterDefinitions, processDefinitionData} = props
    const {
      node: editedNode,
      unusedParameters,
    } = adjustParameters(node, getParameterDefinitions(processDefinitionData, node, dynamicParameterDefinitions))

    const stateForSelectTestResults = TestResultUtils.stateForSelectTestResults(null, testResults)

    this.state = {
      ...stateForSelectTestResults,
      editedNode,
      originalNode: node,
      unusedParameters,
      codeCompletionEnabled: true,
      testResultsToHide: new Set(),
      edges,
    }

    //In most cases this is not needed, as parameter definitions should be present in validation response
    //However, in dynamic cases (as adding new topic/schema version) this can lead to stale parameters
    if (isEditMode) {
      this.updateNodeDataIfNeeded(editedNode)
    }
    this.generateUUID("fields", "parameters")
  }

  initalizeWithProps(props: NodeDetailsContentProps): void {
    const {dynamicParameterDefinitions, node, processDefinitionData} = props
    this.parameterDefinitions = getParameterDefinitions(processDefinitionData, node, dynamicParameterDefinitions)
  }

  generateUUID(...properties) {
    properties.forEach((property) => {
      if (has(this.state.editedNode, property)) {
        get(this.state.editedNode, property, []).forEach((el) => el.uuid = el.uuid || uuid4())
      }
    })
  }

  //TODO: get rid of this method as deprecated in React
  UNSAFE_componentWillReceiveProps(nextProps: Readonly<NodeDetailsContentProps>, nextContext: any) {
    this.initalizeWithProps(nextProps)
    const nextPropsNode = nextProps.node

    if (
      !isEqual(this.props.node, nextPropsNode) ||
      !isEqual(this.props.edges, nextProps.edges)
    ) {
      this.updateNodeState(nextPropsNode, [])
      //In most cases this is not needed, as parameter definitions should be present in validation response
      //However, in dynamic cases (as adding new topic/schema version) this can lead to stale parameters
      this.updateNodeDataIfNeeded(nextPropsNode)
    }
    if (!isEqual(this.props.dynamicParameterDefinitions, nextProps.dynamicParameterDefinitions)) {
      this.adjustStateWithParameters(nextPropsNode)
    }
  }

  adjustStateWithParameters(nodeToAdjust) {
    const {node, unusedParameters} = adjustParameters(nodeToAdjust, this.parameterDefinitions)
    this.updateNodeState(node, unusedParameters)
  }

  updateNodeDataIfNeeded(currentNode) {
    if (this.props.isEditMode) {
      this.props.updateNodeData(this.props.processId, {
        variableTypes: this.props.findAvailableVariables(this.props.originalNodeId),
        branchVariableTypes: this.props.findAvailableBranchVariables(this.props.originalNodeId),
        nodeData: currentNode,
        processProperties: this.props.processProperties,
        outgoingEdges: this.state.edges.map(e => ({...e, to: e._id || e.to})),
      })
    } else {
      this.updateNodeState(currentNode, [])
    }
  }

  componentDidUpdate(prevProps, prevState) {
    if (!isEqual(prevProps.node, this.props.node) || !isEqual(prevProps.testResults, this.props.testResults)) {
      this.selectTestResults()
    }
    if (
      !isEqual(prevState.editedNode, this.state.editedNode) ||
      !isEqual(prevState.edges, this.state.edges)
    ) {
      this.updateNodeDataIfNeeded(this.state.editedNode)
    }
  }

  removeElement = (property, index) => {
    if (has(this.state.editedNode, property)) {
      const node = cloneDeep(this.state.editedNode)
      get(node, property).splice(index, 1)

      this.updateNodeState(node, this.state.unusedParameters)
    }
  }

  addElement = (property, element) => {
    if (has(this.state.editedNode, property)) {
      const node = cloneDeep(this.state.editedNode)
      get(node, property).push(element)

      this.updateNodeState(node, this.state.unusedParameters)
    }
  }

  idField = () => this.createField({
    fieldType: FieldType.input,
    fieldLabel: "Name",
    fieldProperty: "id",
    autoFocus: true,
    validators: [mandatoryValueValidator],
  })

  customNode = (fieldErrors: Error[]): JSX.Element => {
    const {
      editedNode,
      testResultsToHide,
      testResultsToShow,
      edges,
      originalNode,
    } = this.state
    const {
      findAvailableVariables,
      originalNodeId,
      node,
      isEditMode,
      showValidation,
      expressionType,
      nodeTypingInfo,
      processDefinitionData,
      showSwitch,
      additionalPropertiesConfig,
      pathsToMark,
    } = this.props

    const variableTypes = findAvailableVariables(originalNodeId)
    //compare window uses legacy egde component
    const isCompareView = pathsToMark !== undefined

    switch (NodeUtils.nodeType(node)) {
      case "Source":
        return this.sourceSinkCommon({fieldErrors: fieldErrors})
      case "Sink":
        const toAppend: JSX.Element = (
          <div>
            {this.createField({
              fieldType: FieldType.checkbox,
              fieldLabel: "Disabled",
              fieldProperty: "isDisabled",
            })}
          </div>
        )
        return this.sourceSinkCommon({fieldErrors: fieldErrors, toAppend: toAppend})
      case "SubprocessInputDefinition":
        return (
          <SubprocessInputDefinition
            addElement={this.addElement}
            onChange={this.setNodeDataAt}
            node={editedNode}
            isMarked={this.isMarked}
            readOnly={!isEditMode}
            removeElement={this.removeElement}
            showValidation={showValidation}
            renderFieldLabel={this.renderFieldLabel}
            errors={fieldErrors}
            variableTypes={variableTypes}
          />
        )
      case "SubprocessOutputDefinition":
        return (
          <SubprocessOutputDefinition
            renderFieldLabel={this.renderFieldLabel}
            removeElement={this.removeElement}
            onChange={this.setNodeDataAt}
            node={editedNode}
            addElement={this.addElement}
            isMarked={this.isMarked}
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
            {this.idField()}
            {this.createStaticExpressionField(
              {
                fieldName: "expression",
                fieldLabel: "Expression",
                expressionProperty: "expression",
                fieldErrors: fieldErrors,
              }
            )}
            {this.createField({
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
                  onChange={(nextEdges) => this.setState(({edges}) => {
                    if (nextEdges !== edges) {
                      return {edges: nextEdges}
                    }
                  }, this.publishNodeChange)}
                  edgeTypes={[
                    {value: EdgeKind.filterTrue, onlyOne: true},
                    {value: EdgeKind.filterFalse, onlyOne: true},
                  ]}
                  readOnly={!isEditMode}
                  fieldErrors={fieldErrors}
                />
              ) :
              null}
            {this.descriptionField()}
          </div>
        )
      case "Enricher":
      case "Processor":
        return (
          <div className="node-table-body">
            {this.idField()}
            {serviceParameters(editedNode).map((param, index) => {
              return (
                <div className="node-block" key={node.id + param.name + index}>
                  {this.createParameterExpressionField(
                    {
                      parameter: param,
                      expressionProperty: "expression",
                      listFieldPath: `service.parameters[${index}]`,
                      fieldErrors: fieldErrors,
                    }
                  )}
                </div>
              )
            })}
            {node.type === "Enricher" ?
              this.createField({
                fieldType: FieldType.input,
                fieldLabel: "Output",
                fieldProperty: "output",
                validators: [errorValidator(fieldErrors, "output")],
              }) :
              null}
            {node.type === "Processor" ?
              this.createField({
                fieldType: FieldType.checkbox,
                fieldLabel: "Disabled",
                fieldProperty: "isDisabled",
              }) :
              null}
            {this.descriptionField()}
          </div>
        )
      case "SubprocessInput":
        return (
          <div className="node-table-body">
            {this.idField()}
            {this.createField({
              fieldType: FieldType.checkbox,
              fieldLabel: "Disabled",
              fieldProperty: "isDisabled",
            })}
            <ParameterList
              processDefinitionData={processDefinitionData}
              editedNode={editedNode}
              savedNode={editedNode}
              setNodeState={newParams => this.setNodeDataAt("ref.parameters", newParams)}
              createListField={(param, index) => this.createParameterExpressionField(
                {
                  parameter: param,
                  expressionProperty: "expression",
                  listFieldPath: `ref.parameters[${index}]`,
                  fieldErrors: fieldErrors,
                }
              )}
              createReadOnlyField={params => (
                <div className="node-row">
                  {this.renderFieldLabel(params.name)}
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
            {this.descriptionField()}
          </div>
        )

      case "Join":
      case "CustomNode":
        return (
          <div className="node-table-body">
            {this.idField()}
            {
              hasOutputVar(node, processDefinitionData) && this.createField({
                fieldType: FieldType.input,
                fieldLabel: "Output variable name",
                fieldProperty: "outputVar",
                validators: [errorValidator(fieldErrors, "outputVar")],
              })
            }
            {NodeUtils.nodeIsJoin(editedNode) && (
              <BranchParameters
                node={editedNode}
                isMarked={this.isMarked}
                showValidation={showValidation}
                showSwitch={showSwitch}
                isEditMode={isEditMode}
                errors={fieldErrors}
                parameterDefinitions={this.parameterDefinitions}
                setNodeDataAt={this.setNodeDataAt}
                testResultsToShow={testResultsToShow}
                testResultsToHide={testResultsToHide}
                toggleTestResult={this.toggleTestResult}
                findAvailableVariables={findAvailableVariables}
              />
            )}
            {editedNode.parameters?.map((param, index) => {
              return (
                <div className="node-block" key={node.id + param.name + index}>
                  {this.createParameterExpressionField(
                    {
                      parameter: param,
                      expressionProperty: "expression",
                      listFieldPath: `parameters[${index}]`,
                      fieldErrors: fieldErrors,
                    }
                  )}
                </div>
              )
            })}
            {this.descriptionField()}
          </div>
        )
      case "VariableBuilder":
        return (
          <MapVariable
            renderFieldLabel={this.renderFieldLabel}
            removeElement={this.removeElement}
            onChange={this.setNodeDataAt}
            node={editedNode}
            addElement={this.addElement}
            isMarked={this.isMarked}
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
            renderFieldLabel={this.renderFieldLabel}
            onChange={this.setNodeDataAt}
            node={editedNode}
            isMarked={this.isMarked}
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
            {this.idField()}
            {showExpression ?
              this.createStaticExpressionField({
                fieldName: "expression",
                fieldLabel: "Expression (deprecated)",
                expressionProperty: "expression",
                fieldErrors: fieldErrors,
              }) :
              null}
            {showExprVal ?
              this.createField({
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
                  onChange={(nextEdges) => this.setState(({edges}) => {
                    if (nextEdges !== edges) {
                      return {edges: nextEdges}
                    }
                  }, this.publishNodeChange)
                  }
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
            {this.descriptionField()}
          </div>
        )
      case "Split":
        return (
          <div className="node-table-body">
            {this.idField()}
            {this.descriptionField()}
          </div>
        )
      case "Properties":
        const type = node.typeSpecificProperties.type
        //fixme move this configuration to some better place?
        const fields =
          node.isSubprocess ?
            [
              this.createField({
                fieldType: FieldType.input,
                fieldLabel: "Documentation url",
                fieldProperty: "typeSpecificProperties.docsUrl",
                autoFocus: true,
                validators: [errorValidator(fieldErrors, "docsUrl")],
              }),
            ] :
            type === "StreamMetaData" ?
              [
                this.createField({
                  fieldType: FieldType.input,
                  fieldLabel: "Parallelism",
                  fieldProperty: "typeSpecificProperties.parallelism",
                  autoFocus: true,
                  validators: [errorValidator(fieldErrors, "parallelism")],
                }),
                this.createField({
                  fieldType: FieldType.input,
                  fieldLabel: "Checkpoint interval in seconds",
                  fieldProperty: "typeSpecificProperties.checkpointIntervalInSeconds",
                  validators: [errorValidator(fieldErrors, "checkpointIntervalInSeconds")],
                }),
                this.createField({
                  fieldType: FieldType.checkbox,
                  fieldLabel: "Spill state to disk",
                  fieldProperty: "typeSpecificProperties.spillStateToDisk",
                  validators: [errorValidator(fieldErrors, "spillStateToDisk")],
                }),
                this.createField({
                  fieldType: FieldType.checkbox,
                  fieldLabel: "Should use async interpretation",
                  fieldProperty: "typeSpecificProperties.useAsyncInterpretation",
                  validators: [errorValidator(fieldErrors, "useAsyncInterpretation")],
                  defaultValue: processDefinitionData.defaultAsyncInterpretation,
                }),
              ] :
              type === "LiteStreamMetaData" ?
                [
                  this.createField({
                    fieldType: FieldType.input,
                    fieldLabel: "Parallelism",
                    fieldProperty: "typeSpecificProperties.parallelism",
                    autoFocus: true,
                    validators: [errorValidator(fieldErrors, "parallelism")],
                  }),
                ] :
                [this.createField({
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
              onChange={this.setNodeDataAt}
              renderFieldLabel={this.renderFieldLabel}
              editedNode={editedNode}
              readOnly={!isEditMode}
            />
          )
        )
        return (
          <div className="node-table-body">
            {[this.idField(), ...fields, ...additionalFields]}
            {this.descriptionField()}
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

  sourceSinkCommon({fieldErrors, toAppend}: { fieldErrors: Error[], toAppend?: JSX.Element }): JSX.Element {
    const {editedNode} = this.state
    const {node} = this.props
    return (
      <div className="node-table-body">
        {this.idField()}
        {refParameters(editedNode).map((param, index) => {
          return (
            <div className="node-block" key={node.id + param.name + index}>
              {this.createParameterExpressionField(
                {
                  parameter: param,
                  expressionProperty: "expression",
                  listFieldPath: `ref.parameters[${index}]`,
                  fieldErrors: fieldErrors,
                }
              )}
            </div>
          )
        })}
        {toAppend}
        {this.descriptionField()}
      </div>
    )
  }

  createField<K extends keyof State["editedNode"] & string, T extends State["editedNode"][K]>({
    fieldType,
    fieldLabel,
    fieldProperty,
    autoFocus = false,
    validators = [],
    readonly,
    defaultValue,
  }: {
    fieldType: FieldType,
    fieldLabel: string,
    fieldProperty: K,
    autoFocus?: boolean,
    validators?: Validator[],
    readonly?: boolean,
    defaultValue?: T,
  }): JSX.Element {
    const {isEditMode, showValidation} = this.props
    const {editedNode} = this.state

    const isMarked = this.isMarked(fieldProperty)
    const readOnly = !isEditMode || readonly
    const value: T = get(editedNode, fieldProperty, null) ?? defaultValue
    const className = !showValidation || allValid(validators, [value]) ? "node-input" : "node-input node-input-with-error"
    const onChange = (newValue) => this.setNodeDataAt(fieldProperty, newValue, defaultValue)
    return (
      <Field
        type={fieldType}
        isMarked={isMarked}
        readOnly={readOnly}
        showValidation={showValidation}
        autoFocus={autoFocus}
        className={className}
        validators={validators}
        value={value}
        onChange={onChange}
      >
        {this.renderFieldLabel(fieldLabel)}
      </Field>
    )
  }

  //this is for "static" fields like expressions in filters, switches etc.
  createStaticExpressionField = ({
    fieldName,
    fieldLabel,
    expressionProperty,
    fieldErrors,
  }: { fieldName: string, fieldLabel: string, expressionProperty: string, fieldErrors: Error[] }): JSX.Element => {
    return this.doCreateExpressionField({
      fieldName,
      fieldLabel,
      fieldErrors,
      exprPath: `${expressionProperty}`,
    })
  }

  //this is for "dynamic" parameters in sources, sinks, services etc.
  createParameterExpressionField = ({
    parameter,
    expressionProperty,
    listFieldPath,
    fieldErrors,
  }: { parameter: Parameter, expressionProperty: string, listFieldPath: string, fieldErrors: Error[] }): JSX.Element => {
    return this.doCreateExpressionField({
      fieldName: parameter.name,
      fieldLabel: parameter.name,
      exprPath: `${listFieldPath}.${expressionProperty}`,
      fieldErrors,
      parameter: this.parameterDefinitions?.find(p => p.name === parameter.name),
    })
  }

  doCreateExpressionField = ({
    fieldName,
    fieldLabel,
    exprPath,
    fieldErrors,
    parameter,
  }: { fieldName: string, fieldLabel: string, exprPath: string, fieldErrors: Error[], parameter?: UIParameter }): JSX.Element => {
    const {showValidation, showSwitch, isEditMode, findAvailableVariables, originalNodeId} = this.props
    const {editedNode, testResultsToShow, testResultsToHide} = this.state

    return (
      <ExpressionField
        fieldName={fieldName}
        fieldLabel={fieldLabel}
        exprPath={exprPath}
        isEditMode={isEditMode}
        editedNode={editedNode}
        isMarked={this.isMarked}
        showValidation={showValidation}
        showSwitch={showSwitch}
        parameterDefinition={findParamDefinitionByName(this.parameterDefinitions, fieldName)}
        setNodeDataAt={this.setNodeDataAt}
        testResultsToShow={testResultsToShow}
        testResultsToHide={testResultsToHide}
        toggleTestResult={this.toggleTestResult}
        renderFieldLabel={this.renderFieldLabel}
        variableTypes={findAvailableVariables(originalNodeId, parameter)}
        errors={fieldErrors}
      />
    )
  }

  isMarked = (path: string): boolean => {
    const {pathsToMark} = this.props
    return pathsToMark?.some(toMark => startsWith(toMark, path))
  }

  toggleTestResult = (fieldName) => {
    const newTestResultsToHide = cloneDeep(this.state.testResultsToHide)
    newTestResultsToHide.has(fieldName) ? newTestResultsToHide.delete(fieldName) : newTestResultsToHide.add(fieldName)
    this.setState({testResultsToHide: newTestResultsToHide})
  }

  publishNodeChange = (): void => {
    this.props.onChange?.(this.state.editedNode, this.state.edges)
  }

  setNodeDataAt = (propToMutate, newValue, defaultValue?) => {
    const value = newValue == null && defaultValue != undefined ? defaultValue : newValue
    this.setState(
      ({editedNode}) => {
        return {editedNode: set(cloneDeep(editedNode), propToMutate, value)}
      },
      this.publishNodeChange,
    )
  }

  updateNodeState = (editedNode, unusedParameters) => {
    this.setState({editedNode, unusedParameters}, this.publishNodeChange)
  }

  descriptionField = () => {
    return this.createField({
      fieldType: FieldType.plainTextarea,
      fieldLabel: "Description",
      fieldProperty: "additionalFields.description",
    })
  }

  selectTestResults = (id?, testResults?: TestResults) => {
    const stateForSelect = TestResultUtils.stateForSelectTestResults(id, testResults)
    if (stateForSelect) {
      this.setState(stateForSelect)
    }
  }

  renderFieldLabel = (paramName: string): JSX.Element => (
    <FieldLabel
      nodeId={this.props.originalNodeId}
      parameterDefinitions={this.parameterDefinitions}
      paramName={paramName}
    />
  )

  render() {
    const {
      currentErrors = [],
      processId,
      node,
      testResults,
    } = this.props
    const {editedNode} = this.state

    const [fieldErrors, otherErrors] = partition(currentErrors, error => !!error.fieldName)

    const {testResultsIdToShow, testResultsToShow} = this.state
    const selectResults = (id?: number, testResults?: TestResults) => {
      const stateForSelect = TestResultUtils.stateForSelectTestResults(id, testResults)
      if (stateForSelect) {
        this.setState(stateForSelect)
      }
    }

    return (
      <Wrapper
        node={node}
        otherErrors={otherErrors}
        testResults={testResults}
        editedNode={editedNode}
        processId={processId}

        testResultsToShow={testResultsToShow}
        testResultsIdToShow={testResultsIdToShow}
        selectResults={selectResults}
      >
        {this.customNode(fieldErrors)}
      </Wrapper>
    )
  }
}
