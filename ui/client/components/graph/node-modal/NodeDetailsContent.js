import classNames from "classnames"
import _ from "lodash"
import React from "react"
import {connect} from "react-redux"
import {v4 as uuid4} from "uuid"
import ActionsUtils from "../../../actions/ActionsUtils"
import {DEFAULT_EXPRESSION_ID} from "../../../common/graph/constants"
import * as JsonUtils from "../../../common/JsonUtils"
import ProcessUtils from "../../../common/ProcessUtils"
import TestResultUtils from "../../../common/TestResultUtils"
import {allValid, errorValidator, mandatoryValueValidator} from "./editors/Validators"
import {InputWithFocus} from "../../withFocus"
import NodeUtils from "../NodeUtils"
import MapVariable from "./../node-modal/MapVariable"
import Variable from "./Variable"
import BranchParameters, {branchErrorFieldName} from "./BranchParameters"
import ExpressionField from "./editors/expression/ExpressionField"
import Field from "./editors/field/Field"
import NodeErrors from "./NodeErrors"
import ParameterList from "./ParameterList"
import SubprocessInputDefinition from "./subprocess-input-definition/SubprocessInputDefinition"
import TestErrors from "./tests/TestErrors"
import TestResults from "./tests/TestResults"
import TestResultsSelect from "./tests/TestResultsSelect"
import AdditionalProperty from "./AdditionalProperty"
import NodeAdditionalInfoBox from "./NodeAdditionalInfoBox"
import SubprocessOutputDefinition from "./SubprocessOutputDefinition"
import {adjustParameters} from "./ParametersUtils"
import {getProcessCategory} from "../../../reducers/selectors/graph"

//move state to redux?
// here `componentDidUpdate` is complicated to clear unsaved changes in modal
export class NodeDetailsContent extends React.Component {

  constructor(props) {
    super(props)

    this.initalizeWithProps(props)
    const nodeToAdjust = props.node
    const {node, unusedParameters} = adjustParameters(nodeToAdjust, this.parameterDefinitions, this.nodeDefinitionByName(nodeToAdjust))

    this.state = {
      ...TestResultUtils.stateForSelectTestResults(null, this.props.testResults),
      editedNode: node,
      unusedParameters: unusedParameters,
      codeCompletionEnabled: true,
      testResultsToHide: new Set(),
    }
    //In most cases this is not needed, as parameter definitions should be present in validation response
    //However, in dynamic cases (as adding new topic/schema version) this can lead to stale parameters
    this.updateNodeDataIfNeeded(node)
    this.generateUUID("fields", "parameters")
  }

  nodeDefinitionByName(node) {
    return this.props.processDefinitionData.nodesToAdd
      .flatMap(c => c.possibleNodes)
      .find(n => n.node.type === node.type && n.label === ProcessUtils.findNodeDefinitionId(node))?.node
  }

  initalizeWithProps(props) {
    const nodeObjectDetails = ProcessUtils.findNodeObjectTypeDefinition(props.node, props.processDefinitionData.processDefinition)
    this.parameterDefinitions = props.dynamicParameterDefinitions ? props.dynamicParameterDefinitions : nodeObjectDetails?.parameters
    const hasNoReturn = nodeObjectDetails == null || nodeObjectDetails.returnType == null
    this.showOutputVar = hasNoReturn === false || hasNoReturn === true && props.node.outputVar
  }

  generateUUID(...properties) {
    properties.forEach((property) => {
      if (_.has(this.state.editedNode, property)) {
        let elements = _.get(this.state.editedNode, property)
        elements.map((el) => el.uuid = el.uuid || uuid4())
      }
    })
  }

  //TODO: get rid of this method as deprecated in React
  componentWillReceiveProps(nextProps) {

    this.initalizeWithProps(nextProps)
    const nextPropsNode = nextProps.node

    if (!_.isEqual(this.props.node, nextPropsNode)) {
      this.setState({editedNode: nextPropsNode, unusedParameters: []})
      //In most cases this is not needed, as parameter definitions should be present in validation response
      //However, in dynamic cases (as adding new topic/schema version) this can lead to stale parameters
      this.updateNodeDataIfNeeded(nextPropsNode)
    }
    if (!_.isEqual(this.props.dynamicParameterDefinitions, nextProps.dynamicParameterDefinitions)) {
      this.adjustStateWithParameters(this.state.editedNode)
    }
  }

  adjustStateWithParameters(nodeToAdjust) {
    const {node, unusedParameters} = adjustParameters(nodeToAdjust, this.parameterDefinitions, this.nodeDefinitionByName(nodeToAdjust))
    this.setState({editedNode: node, unusedParameters: unusedParameters})
  }

  updateNodeDataIfNeeded(currentNode) {
    if (this.props.isEditMode) {
      this.props.actions.updateNodeData(this.props.processId,
        this.props.findAvailableVariables(this.props.originalNodeId),
        this.props.findAvailableBranchVariables(this.props.originalNodeId),
        currentNode,
        this.props.processProperties)
    } else {
      this.setState({editedNode: currentNode, unusedParameters: []})
    }
  }

  componentDidUpdate(prevProps, prevState) {
    if (!_.isEqual(prevProps.node, this.props.node) || !_.isEqual(prevProps.testResults, this.props.testResults)) {
      this.selectTestResults()
    }
    if (!_.isEqual(prevState.editedNode, this.state.editedNode)) {
      this.updateNodeDataIfNeeded(this.state.editedNode)
    }
  }

  findParamDefinitionByName(paramName) {
    return (this.parameterDefinitions || []).find((param) => param.name === paramName)
  }

  removeElement = (property, index) => {
    if (_.has(this.state.editedNode, property)) {
      _.get(this.state.editedNode, property).splice(index, 1)

      this.setState((state, props) => ({editedNode: state.editedNode}), () => {
        this.props.onChange(this.state.editedNode)
      })
    }
  }

  addElement = (property, element) => {
    if (_.has(this.state.editedNode, property)) {
      _.get(this.state.editedNode, property).push(element)

      this.setState((state, props) => ({editedNode: state.editedNode}), () => {
        this.props.onChange(this.state.editedNode)
      })
    }
  }

  idField = () => this.createField("input", "Name", "id", true, [mandatoryValueValidator])

  customNode = (fieldErrors) => {
    const {showValidation, showSwitch, isEditMode, findAvailableVariables} = this.props
    const variableTypes = findAvailableVariables(this.props.originalNodeId)

    switch (NodeUtils.nodeType(this.props.node)) {
      case "Source":
        return this.sourceSinkCommon(null, fieldErrors)
      case "Sink":
        const toAppend = (
          <div>
            {
              //TODO: this is a bit clumsy. we should use some metadata, instead of relying on what comes in diagram
              this.props.node.endResult ? this.createStaticExpressionField(
                "expression",
                "Expression",
                "endResult",
                fieldErrors
              ) : null
            }
            {this.createField("checkbox", "Disabled", "isDisabled")}
          </div>
        )
        return this.sourceSinkCommon(toAppend, fieldErrors)
      case "SubprocessInputDefinition":
        return (
          <SubprocessInputDefinition
            addElement={this.addElement}
            onChange={this.setNodeDataAt}
            node={this.state.editedNode}
            isMarked={this.isMarked}
            readOnly={!this.props.isEditMode}
            removeElement={this.removeElement}
            toogleCloseOnEsc={this.props.toogleCloseOnEsc}
            showValidation={showValidation}
            errors={fieldErrors}
            renderFieldLabel={this.renderFieldLabel}
          />
        )
      case "SubprocessOutputDefinition":
        return (
          <SubprocessOutputDefinition
            renderFieldLabel={this.renderFieldLabel}
            removeElement={this.removeElement}
            onChange={this.setNodeDataAt}
            node={this.state.editedNode}
            addElement={this.addElement}
            isMarked={this.isMarked}
            readOnly={!this.props.isEditMode}
            showValidation={showValidation}
            errors={fieldErrors}
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
            {this.descriptionField()}
          </div>
        )
      case "Enricher":
      case "Processor":
        return (
          <div className="node-table-body">
            {this.idField()}
            {this.state.editedNode.service.parameters.map((param, index) => {
              return (
                <div className="node-block" key={this.props.node.id + param.name + index}>
                  {this.createParameterExpressionField(
                    param,
                    "expression",
                    `service.parameters[${index}]`,
                    fieldErrors
                  )}
                </div>
              )
            })}
            {this.props.node.type === "Enricher" ? this.createField(
              "input",
              "Output",
              "output",
              false,
              [mandatoryValueValidator, errorValidator(fieldErrors, "output")],
            ) : null}
            {this.props.node.type === "Processor" ? this.createField("checkbox", "Disabled", "isDisabled") : null}
            {this.descriptionField()}
          </div>
        )
      case "SubprocessInput":
        return (
          <div className="node-table-body">
            {this.idField()}
            {this.createField("checkbox", "Disabled", "isDisabled")}
            <ParameterList
              processDefinitionData={this.props.processDefinitionData}
              editedNode={this.state.editedNode}
              savedNode={this.state.editedNode}
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
              this.showOutputVar && this.createField(
                "input",
                "Output",
                "outputVar",
                false,
                [mandatoryValueValidator, errorValidator(fieldErrors, "outputVar")],
                "outputVar",
                false,
                null,
              )
            }
            {NodeUtils.nodeIsJoin(this.state.editedNode) && (
              <BranchParameters
                node={this.state.editedNode}
                isMarked={this.isMarked}
                showValidation={showValidation}
                showSwitch={showSwitch}
                isEditMode={isEditMode}
                errors={fieldErrors}
                parameterDefinitions={this.parameterDefinitions}
                setNodeDataAt={this.setNodeDataAt}
                testResultsToShow={this.state.testResultsToShow}
                testResultsToHide={this.state.testResultsToHide}
                toggleTestResult={this.toggleTestResult}
                findAvailableVariables={this.props.findAvailableVariables}
              />
            )}
            {this.state.editedNode.parameters.map((param, index) => {
              return (
                <div className="node-block" key={this.props.node.id + param.name + index}>
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
            varNameLabel={"Variable Name"}
            renderFieldLabel={this.renderFieldLabel}
            removeElement={this.removeElement}
            onChange={this.setNodeDataAt}
            node={this.state.editedNode}
            addElement={this.addElement}
            isMarked={this.isMarked}
            readOnly={!this.props.isEditMode}
            showValidation={showValidation}
            variableTypes={variableTypes}
            errors={fieldErrors}
          />
        )
      case "Variable":
        return (
          <Variable
            renderFieldLabel={this.renderFieldLabel}
            onChange={this.setNodeDataAt}
            node={this.state.editedNode}
            isMarked={this.isMarked}
            readOnly={!this.props.isEditMode}
            showValidation={showValidation}
            variableTypes={variableTypes}
            errors={fieldErrors}
            inferredVariableType={this.props.expressionType && ProcessUtils.humanReadableType(this.props.expressionType)}
          />
        )
      case "Switch":
        return (
          <div className="node-table-body">
            {this.idField()}
            {this.createStaticExpressionField(
              "expression",
              "Expression",
              "expression",
              fieldErrors
            )}
            {this.createField("input", "exprVal", "exprVal", false, [mandatoryValueValidator, errorValidator(fieldErrors, "exprVal")])}
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
        const type = this.props.node.typeSpecificProperties.type
        const commonFields = this.subprocessVersionFields()
        //fixme move this configuration to some better place?
        const fields = type === "StreamMetaData" ? [
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
            "Should split state to disk",
            "typeSpecificProperties.splitStateToDisk",
            false,
            [errorValidator(fieldErrors, "splitStateToDisk")],
            "splitStateToDisk",
            false,
            false,
            "split-state-disk",
          ),
          this.createField(
            "checkbox",
            "Should use async interpretation (lazy variables not allowed)",
            "typeSpecificProperties.useAsyncInterpretation",
            false,
            [errorValidator(fieldErrors, "useAsyncInterpretation")],
            "useAsyncInterpretation",
            false,
            this.props.processDefinitionData.defaultAsyncInterpretation,
            "use-async",
          ),
        ] : [this.createField(
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
        const additionalFields = Object.entries(this.props.additionalPropertiesConfig).map(
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
              isEditMode={!this.props.isEditMode}
              editedNode={this.state.editedNode}
              readOnly={!this.props.isEditMode}
            />
          )
        )
        const hasExceptionHandlerParams = this.state.editedNode.exceptionHandler.parameters.length > 0
        return (
          <div className="node-table-body">
            {_.concat(fields, commonFields, additionalFields)}
            {hasExceptionHandlerParams ?
              (
                <div className="node-row">
                  <div className="node-label">Exception handler:</div>
                  <div className="node-group">
                    {this.state.editedNode.exceptionHandler.parameters.map((param, index) => {
                      return (
                        <div className="node-block" key={this.props.node.id + param.name + index}>
                          {this.createParameterExpressionField(
                            param,
                            "expression",
                            `exceptionHandler.parameters[${index}]`,
                            fieldErrors
                          )}
                        </div>
                      )
                    })}
                  </div>
                </div>
              ) : null
            }
            {this.descriptionField()}
          </div>
        )
      default:
        return (
          <div>
            Node type not known.
            <NodeDetails node={this.props.node}/>
          </div>
        )
    }
  }

  subprocessVersionFields() {
    return [
      //TODO this should be nice looking selectbox
      this.doCreateField(
        "plain-textarea",
        "Subprocess Versions",
        "subprocessVersions",
        JsonUtils.tryStringify(this.state.editedNode.subprocessVersions || {}),
        (newValue) => this.setNodeDataAt("subprocessVersions", JsonUtils.tryParse(newValue)),
        null,
        false,
        "subprocess-versions",
      )]
  }

  sourceSinkCommon(toAppend, fieldErrors) {
    return (
      <div className="node-table-body">
        {this.idField()}
        {this.state.editedNode.ref.parameters.map((param, index) => {
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

  createField = (fieldType, fieldLabel, fieldProperty, autofocus = false, validators = [], fieldName, readonly, defaultValue, key) => {
    return this.doCreateField(
      fieldType,
      fieldLabel,
      fieldName,
      _.get(this.state.editedNode, fieldProperty, null) || defaultValue,
      (newValue) => this.setNodeDataAt(fieldProperty, newValue, defaultValue),
      readonly,
      this.isMarked(fieldProperty),
      key,
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
    const paramDefinition = this.parameterDefinitions.find(p => p.name === parameter.name)
    return this.doCreateExpressionField(parameter.name, parameter.name, `${listFieldPath}.${expressionProperty}`, fieldErrors, paramDefinition)
  }

  doCreateExpressionField = (fieldName, fieldLabel, exprPath, fieldErrors, parameter) => {
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
        parameterDefinition={this.findParamDefinitionByName(fieldName)}
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
    return _.includes(this.props.pathsToMark, path)
  }

  toggleTestResult = (fieldName) => {
    const newTestResultsToHide = _.cloneDeep(this.state.testResultsToHide)
    newTestResultsToHide.has(fieldName) ? newTestResultsToHide.delete(fieldName) : newTestResultsToHide.add(fieldName)
    this.setState({testResultsToHide: newTestResultsToHide})
  }

  doCreateField = (
    fieldType,
    fieldLabel,
    fieldName,
    fieldValue,
    handleChange,
    forceReadonly,
    isMarked,
    key,
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

  setNodeDataAt = (propToMutate, newValue, defaultValue) => {
    const value = newValue == null && defaultValue != undefined ? defaultValue : newValue
    const node = _.cloneDeep(this.state.editedNode)

    _.set(node, propToMutate, value)

    this.setState({editedNode: node})
    this.props.onChange(node)
  }

  descriptionField = () => {
    return this.createField("plain-textarea", "Description", "additionalFields.description")
  }

  selectTestResults = (id, testResults) => {
    const stateForSelect = TestResultUtils.stateForSelectTestResults(id, testResults)
    if (stateForSelect) {
      this.setState(stateForSelect)
    }
  }

  renderFieldLabel = (label) => {
    const parameter = this.findParamDefinitionByName(label)
    return (
      <div className="node-label" title={label}>{label}:
        {parameter ?
          <div className="labelFooter">{ProcessUtils.humanReadableType(parameter.typ)}</div> : null}
      </div>
    )
  }

  fieldErrors = (errors) => {
    return errors.filter(error => error.fieldName &&
        this.availableFields().includes(error.fieldName)) || []
  }

  availableFields = () => {
    if (this.props.dynamicParameterDefinitions) {
      return this.joinFields(this.props.dynamicParameterDefinitions)
    }
    switch (NodeUtils.nodeType(this.state.editedNode)) {
      case "Source": {
        const commonFields = ["id"]
        return _.concat(commonFields, this.state.editedNode.ref.parameters.map(param => param.name))
      }
      case "Sink": {
        const commonFields = ["id", DEFAULT_EXPRESSION_ID]
        return _.concat(commonFields, this.state.editedNode.ref.parameters.map(param => param.name))
      }
      case "SubprocessInputDefinition": {
        return ["id"]
      }
      case "SubprocessOutputDefinition":
        return ["id", "outputName"]
      case "Filter":
        return ["id", DEFAULT_EXPRESSION_ID]
      case "Enricher":
        const commonFields = ["id", "output"]
        const paramFields = this.state.editedNode.service.parameters.map(param => param.name)
        return _.concat(commonFields, paramFields)
      case "Processor": {
        const commonFields = ["id"]
        const paramFields = this.state.editedNode.service.parameters.map(param => param.name)
        return _.concat(commonFields, paramFields)
      }
      case "SubprocessInput": {
        const commonFields = ["id"]
        const paramFields = this.state.editedNode.ref.parameters.map(param => param.name)
        return _.concat(commonFields, paramFields)
      }
      case "Join": {
        return this.joinFields(this.state.editedNode.parameters)
      }
      case "CustomNode": {
        const commonFields = ["id", "outputVar"]
        const paramFields = this.state.editedNode.parameters.map(param => param.name)
        return _.concat(commonFields, paramFields)
      }
      case "VariableBuilder":
        return MapVariable.availableFields(this.state.editedNode)
      case "Variable":
        return Variable.availableFields
      case "Switch":
        return ["id", DEFAULT_EXPRESSION_ID, "exprVal"]
      case "Split":
        return ["id"]
      case "Properties": {
        const commonFields = "subprocessVersions"
        const fields = this.props.node.typeSpecificProperties.type === "StreamMetaData" ?
          ["parallelism", "checkpointIntervalInSeconds", "splitStateToDisk", "useAsyncInterpretation"] : ["path"]
        const additionalFields = Object.entries(this.props.additionalPropertiesConfig).map(([fieldName, fieldConfig]) => fieldName)
        const exceptionHandlerFields = this.state.editedNode.exceptionHandler.parameters.map(param => param.name)
        return _.concat(commonFields, fields, additionalFields, exceptionHandlerFields)
      }
      default:
        return []
    }
  }

  joinFields = (parametersFromDefinition) => {
    const commonFields = ["id", "outputVar"]
    const paramFields = parametersFromDefinition.map(param => param.name)
    const branchParamsFields = this.state?.editedNode?.branchParameters
      .flatMap(branchParam => branchParam.parameters.map(param => branchErrorFieldName(param.name, branchParam.branchId)))
    return _.concat(commonFields, paramFields, branchParamsFields)
  }

  render() {
    const nodeClass = classNames("node-table", {"node-editable": this.props.isEditMode})
    const fieldErrors = this.fieldErrors(this.props.currentErrors || [])
    const otherErrors = this.props.currentErrors ? this.props.currentErrors.filter(error => !fieldErrors.includes(error)) : []
    return (
      <div className={nodeClass}>
        <NodeErrors errors={otherErrors} message={"Node has errors"}/>
        <TestResultsSelect
          results={this.props.testResults}
          resultsIdToShow={this.state.testResultsIdToShow}
          selectResults={this.selectTestResults}
        />
        <TestErrors resultsToShow={this.state.testResultsToShow}/>
        {this.customNode(fieldErrors)}
        <TestResults nodeId={this.props.node.id} resultsToShow={this.state.testResultsToShow}/>

        <NodeAdditionalInfoBox node={this.state.editedNode} processId={this.props.processId}/>
      </div>
    )
  }
}

function mapState(state, props) {
  const processDefinitionData = state.settings.processDefinitionData || {}

  //NOTE: we have to use mainProcess carefully, as we may display details of subprocess node, in this case
  //process is *different* than subprocess itself
  //TODO: in particular we need it for branches, how to handle it for subprocesses?
  const mainProcess = state.graphReducer.processToDisplay
  const findAvailableVariables = ProcessUtils.findAvailableVariables(processDefinitionData, getProcessCategory(state), mainProcess)

  const findAvailableBranchVariables = ProcessUtils.findVariablesForBranches(mainProcess?.validationResult?.nodeResults)
  //see NodeDetailsModal - we pass own state in props.node, so we cannot just rely on props.node.id
  const originalNodeId = props.originalNodeId || props.node?.id
  return {
    additionalPropertiesConfig: processDefinitionData.additionalPropertiesConfig || {},
    processDefinitionData: processDefinitionData,
    processId: mainProcess.id,
    processProperties: mainProcess.properties,
    variableTypes: mainProcess?.validationResult?.nodeResults?.[originalNodeId]?.variableTypes || {},
    findAvailableBranchVariables: findAvailableBranchVariables,
    findAvailableVariables: findAvailableVariables,
    originalNodeId: originalNodeId,
    currentErrors: state.nodeDetails.validationPerformed ? state.nodeDetails.validationErrors : props.nodeErrors,
    dynamicParameterDefinitions: state.nodeDetails.validationPerformed ? state.nodeDetails.parameters :
        state.graphReducer.processToDisplay?.validationResult?.nodeResults?.[originalNodeId]?.parameters,
    expressionType: state.nodeDetails.expressionType,
  }
}

export default connect(mapState, ActionsUtils.mapDispatchWithEspActions)(NodeDetailsContent)

class NodeDetails extends React.Component {
  render() {
    return (
      <div>
        <pre>{JSON.stringify(this.props.node, null, 2)}</pre>
      </div>
    )
  }
}
