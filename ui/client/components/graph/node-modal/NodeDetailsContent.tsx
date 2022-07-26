/* eslint-disable i18next/no-literal-string */
import {cloneDeep, get, has, isEqual, partition, set, sortBy, startsWith} from "lodash"
import React from "react"
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
import Field from "./editors/field/Field"
import {allValid, Error, errorValidator, mandatoryValueValidator} from "./editors/Validators"
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
  NodeId,
  NodeType,
  NodeValidationError,
  ParameterConfig,
  ProcessDefinitionData,
  ProcessId,
  UIParameter,
  VariableTypes,
} from "../../../types"
import {UserSettings} from "../../../reducers/userSettings"
import {ValidationRequest} from "../../../actions/nk"

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

function findParamDefinitionByName(definitions: UIParameter[], paramName: string): UIParameter {
  return definitions?.find((param) => param.name === paramName)
}

function getNodeParams(processDefinitionData: ProcessDefinitionData, nodeId: NodeId): Record<string, ParameterConfig> {
  return processDefinitionData.componentsConfig[nodeId]?.params
}

function getParameterDefinitions(processDefinitionData: ProcessDefinitionData, node: NodeType, dynamicParameterDefinitions?: UIParameter[]): UIParameter[] {
  return dynamicParameterDefinitions || ProcessUtils.findNodeObjectTypeDefinition(node, processDefinitionData.processDefinition)?.parameters
}

function hasOutputVar(node: NodeType, processDefinitionData: ProcessDefinitionData): boolean {
  const returnType = ProcessUtils.findNodeObjectTypeDefinition(node, processDefinitionData.processDefinition)?.returnType
  return !!returnType || !!node.outputVar
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
      unusedParameters
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

  idField = () => this.createField("input", "Name", "id", true, [mandatoryValueValidator])

  customNode = (fieldErrors: Error[]): JSX.Element => {
    const {edges, editedNode, originalNode, testResultsToHide, testResultsToShow} = this.state
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
        return this.sourceSinkCommon(null, fieldErrors)
      case "Sink":
        const toAppend = (
          <div>
            {this.createField("checkbox", "Disabled", "isDisabled")}
          </div>
        )
        return this.sourceSinkCommon(toAppend, fieldErrors)
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
              "expression",
              "Expression",
              "expression",
              fieldErrors
            )}
            {this.createField("checkbox", "Disabled", "isDisabled")}
            {!isCompareView ?
              (
                <EdgesDndComponent
                  label={"Outputs"}
                  nodeId={originalNodeId}
                  value={this.state.edges}
                  onChange={(edges) => {
                    if (edges !== this.state.edges) {
                      this.setState({edges}, this.publishNodeChange)
                    }
                  }}
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
                    param,
                    "expression",
                    `service.parameters[${index}]`,
                    fieldErrors
                  )}
                </div>
              )
            })}
            {node.type === "Enricher" ?
              this.createField(
                "input",
                "Output",
                "output",
                false,
                [errorValidator(fieldErrors, "output")],
              ) :
              null}
            {node.type === "Processor" ? this.createField("checkbox", "Disabled", "isDisabled") : null}
            {this.descriptionField()}
          </div>
        )
      case "SubprocessInput":
        return (
          <div className="node-table-body">
            {this.idField()}
            {this.createField("checkbox", "Disabled", "isDisabled")}
            <ParameterList
              processDefinitionData={processDefinitionData}
              editedNode={editedNode}
              savedNode={editedNode}
              setNodeState={newParams => this.setNodeDataAt("ref.parameters", newParams)}
              createListField={(param, index) => this.createParameterExpressionField(
                param,
                "expression",
                `ref.parameters[${index}]`,
                fieldErrors
              )}
              createReadOnlyField={params => (
                <div className="node-row">{this.renderFieldLabel(params.name)}
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
              hasOutputVar(node, processDefinitionData) && this.createField(
                "input",
                "Output variable name",
                "outputVar",
                false,
                [errorValidator(fieldErrors, "outputVar")],
                "outputVar",
                false,
                null,
              )
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
                    param,
                    "expression",
                    `parameters[${index}]`,
                    fieldErrors
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
        const currentExpression = this.state.originalNode["expression"]
        const currentExprVal = this.state.originalNode["exprVal"]
        const exprValValidator = errorValidator(fieldErrors, "exprVal")
        const showExpression = definition["expression"] ? !isEqual(definition["expression"], currentExpression) : currentExpression?.expression
        const showExprVal = !exprValValidator.isValid() || definition["exprVal"] ? definition["exprVal"] !== currentExprVal : currentExprVal
        return (
          <div className="node-table-body">
            {this.idField()}
            {showExpression ?
              this.createStaticExpressionField("expression", "Expression (deprecated)", "expression", fieldErrors) :
              null}
            {showExprVal ?
              this.createField("input", "exprVal (deprecated)", "exprVal", false, [exprValValidator]) :
              null}
            {!isCompareView ?
              (
                <EdgesDndComponent
                  label={"Conditions"}
                  nodeId={originalNodeId}
                  value={edges}
                  onChange={(edges) => {
                    if (edges !== edges) {
                      this.setState({edges}, this.publishNodeChange)
                    }
                  }}
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
              this.createField(
                "input",
                "Documentation url",
                "typeSpecificProperties.docsUrl",
                true,
                [errorValidator(fieldErrors, "docsUrl")],
                "docsUrl",
                null,
                null,
                "docsUrl",
              ),
            ] :
            type === "StreamMetaData" ?
              [
                this.createField(
                  "input",
                  "Parallelism",
                  "typeSpecificProperties.parallelism",
                  true,
                  [errorValidator(fieldErrors, "parallelism")],
                  "parallelism",
                  null,
                  null,
                  "parallelism",
                ),
                this.createField(
                  "input",
                  "Checkpoint interval in seconds",
                  "typeSpecificProperties.checkpointIntervalInSeconds",
                  false,
                  [errorValidator(fieldErrors, "checkpointIntervalInSeconds")],
                  "checkpointIntervalInSeconds",
                  null,
                  null,
                  "interval-seconds",
                ),
                this.createField(
                  "checkbox",
                  "Spill state to disk",
                  "typeSpecificProperties.spillStateToDisk",
                  false,
                  [errorValidator(fieldErrors, "spillStateToDisk")],
                  "spillStateToDisk",
                  false,
                  false,
                  "split-state-disk",
                ),
                this.createField(
                  "checkbox",
                  "Should use async interpretation",
                  "typeSpecificProperties.useAsyncInterpretation",
                  false,
                  [errorValidator(fieldErrors, "useAsyncInterpretation")],
                  "useAsyncInterpretation",
                  false,
                  processDefinitionData.defaultAsyncInterpretation,
                  "use-async",
                ),
              ] :
              type === "LiteStreamMetaData" ?
                [
                  this.createField(
                    "input",
                    "Parallelism",
                    "typeSpecificProperties.parallelism",
                    true,
                    [errorValidator(fieldErrors, "parallelism")],
                    "parallelism",
                    null,
                    null,
                    "parallelism",
                  ),
                ] :
                [this.createField(
                  "input",
                  "Query path",
                  "typeSpecificProperties.path",
                  false,
                  [errorValidator(fieldErrors, "path")],
                  "path",
                  null,
                  null,
                  "query-path",
                )]
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

  sourceSinkCommon(toAppend, fieldErrors) {
    const editedNode = this.state?.editedNode
    return (
      <div className="node-table-body">
        {this.idField()}
        {refParameters(editedNode).map((param, index) => {
          return (
            <div className="node-block" key={this.props.node.id + param.name + index}>
              {this.createParameterExpressionField(
                param,
                "expression",
                `ref.parameters[${index}]`,
                fieldErrors
              )}
            </div>
          )
        })}
        {toAppend}
        {this.descriptionField()}
      </div>
    )
  }

  createField = (fieldType: string, fieldLabel: string, fieldProperty: string, autofocus = false, validators = [], fieldName?: string, readonly?: boolean, defaultValue?: boolean, key?: string) => {
    return this.doCreateField(
      fieldType,
      fieldLabel,
      fieldName,
      get(this.state.editedNode, fieldProperty, null) ?? defaultValue,
      (newValue) => this.setNodeDataAt(fieldProperty, newValue, defaultValue),
      readonly,
      this.isMarked(fieldProperty),
      key || fieldProperty || fieldLabel,
      autofocus,
      validators,
    )
  }

  //this is for "static" fields like expressions in filters, switches etc.
  createStaticExpressionField = (fieldName, fieldLabel, expressionProperty, fieldErrors) => {
    return this.doCreateExpressionField(fieldName, fieldLabel, `${expressionProperty}`, fieldErrors)
  }

  //this is for "dynamic" parameters in sources, sinks, services etc.
  createParameterExpressionField = (parameter, expressionProperty, listFieldPath, fieldErrors) => {
    const paramDefinition = this.parameterDefinitions?.find(p => p.name === parameter.name)
    return this.doCreateExpressionField(parameter.name, parameter.name, `${listFieldPath}.${expressionProperty}`, fieldErrors, paramDefinition)
  }

  doCreateExpressionField = (fieldName, fieldLabel, exprPath, fieldErrors, parameter?) => {
    const {showValidation, showSwitch, isEditMode, node, findAvailableVariables} = this.props
    const variableTypes = findAvailableVariables(this.props.originalNodeId, parameter)
    return (
      <ExpressionField
        fieldName={fieldName}
        fieldLabel={fieldLabel}
        exprPath={exprPath}
        isEditMode={isEditMode}
        editedNode={this.state.editedNode}
        isMarked={this.isMarked}
        showValidation={showValidation}
        showSwitch={showSwitch}
        parameterDefinition={findParamDefinitionByName(this.parameterDefinitions, fieldName)}
        setNodeDataAt={this.setNodeDataAt}
        testResultsToShow={this.state.testResultsToShow}
        testResultsToHide={this.state.testResultsToHide}
        toggleTestResult={this.toggleTestResult}
        renderFieldLabel={this.renderFieldLabel}
        variableTypes={variableTypes}
        errors={fieldErrors}
      />
    )
  }

  isMarked = (path) => {
    return this.props.pathsToMark?.some(toMark => startsWith(toMark, path))
  }

  toggleTestResult = (fieldName) => {
    const newTestResultsToHide = cloneDeep(this.state.testResultsToHide)
    newTestResultsToHide.has(fieldName) ? newTestResultsToHide.delete(fieldName) : newTestResultsToHide.add(fieldName)
    this.setState({testResultsToHide: newTestResultsToHide})
  }

  doCreateField = (
    fieldType: string,
    fieldLabel: string,
    fieldName: string,
    fieldValue: string,
    handleChange: (newValue: any) => void,
    forceReadonly: boolean,
    isMarked: boolean,
    key: string,
    autofocus = false,
    validators = [],
  ) => {
    const readOnly = !this.props.isEditMode || forceReadonly
    const showValidation = this.props.showValidation

    return (
      <Field
        fieldType={fieldType}
        renderFieldLabel={() => this.renderFieldLabel(fieldLabel)}
        isMarked={isMarked}
        readOnly={readOnly}
        value={fieldValue || ""}
        autofocus={autofocus}
        showValidation={showValidation}
        validators={validators}
        onChange={handleChange}
        key={key}
        className={!showValidation || allValid(validators, [fieldValue]) ? "node-input" : "node-input node-input-with-error"}
      />
    )
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
    return this.createField("plain-textarea", "Description", "additionalFields.description")
  }

  selectTestResults = (id?, testResults?: TestResults) => {
    const stateForSelect = TestResultUtils.stateForSelectTestResults(id, testResults)
    if (stateForSelect) {
      this.setState(stateForSelect)
    }
  }

  renderFieldLabel = (paramName: string) => {
    const {processDefinitionData, node} = this.props
    const parameter = findParamDefinitionByName(this.parameterDefinitions, paramName)
    const params = getNodeParams(processDefinitionData, node.id)
    const label = params?.[paramName]?.label ?? paramName
    return (
      <div className="node-label" title={paramName}>{label}:
        {parameter ?
          <div className="labelFooter">{ProcessUtils.humanReadableType(parameter.typ)}</div> :
          null}
      </div>
    )
  }

  render() {
    const {
      currentErrors = [],
      processId,
      node,
      testResults,
    } = this.props

    const [fieldErrors, otherErrors] = partition(currentErrors, error => !!error.fieldName)

    return (
      <>
        <NodeErrors errors={otherErrors} message={"Node has errors"}/>
        <TestResultsSelect
          results={testResults}
          resultsIdToShow={this.state.testResultsIdToShow}
          selectResults={this.selectTestResults}
        />
        <TestErrors resultsToShow={this.state.testResultsToShow}/>
        {this.customNode(fieldErrors)}
        <TestResultsComponent nodeId={node.id} resultsToShow={this.state.testResultsToShow}/>

        <NodeAdditionalInfoBox node={this.state.editedNode} processId={processId}/>
      </>
    )
  }
}
