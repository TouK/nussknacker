import React from "react"
import _ from "lodash"
import NodeUtils from "../NodeUtils"
import ProcessUtils from '../../../common/ProcessUtils'
import * as JsonUtils from '../../../common/JsonUtils'
import ParameterList from "./ParameterList"
import {v4 as uuid4} from "uuid"
import MapVariable from "./../node-modal/MapVariable"
import BranchParameters from "./../node-modal/BranchParameters"
import Variable from "./../node-modal/Variable"
import JoinDef from "./JoinDef"
import {allValid, errorValidator, notEmptyValidator} from "../../../common/Validators"
import {DEFAULT_EXPRESSION_ID} from "../../../common/graph/constants"
import SubprocessInputDefinition from "./subprocess-input-definition/SubprocessInputDefinition"
import {connect} from "react-redux"
import ActionsUtils from "../../../actions/ActionsUtils"
import classNames from "classnames"
import {branchErrorFieldName} from "./BranchParameters"
import Field from "./editors/field/Field"
import EditableExpression from "./editors/expression/EditableExpression"
import ExpressionField from "./editors/expression/ExpressionField"
import TestResults from "./tests/TestResults"
import TestResultsSelect from "./tests/TestResultsSelect"
import TestErrors from "./tests/TestErrors"
import TestResultUtils from "../../../common/TestResultUtils"
import NodeErrors from "./NodeErrors"

//move state to redux?
// here `componentDidUpdate` is complicated to clear unsaved changes in modal
export class NodeDetailsContent extends React.Component {

  constructor(props) {
    super(props);

    this.nodeObjectDetails = ProcessUtils.findNodeObjectTypeDefinition(this.props.node, this.props.processDefinitionData.processDefinition);

    this.nodeDef = this.prepareNodeDef(props.node, this.nodeObjectDetails, props.processToDisplay)

    this.state = {
      ...TestResultUtils.stateForSelectTestResults(null, this.props.testResults),
      editedNode: this.enrichNodeWithProcessDependentData(_.cloneDeep(props.node)),
      codeCompletionEnabled: true,
      testResultsToHide: new Set(),
    };

    let hasNoReturn = this.nodeObjectDetails == null || this.nodeObjectDetails.returnType == null
    this.showOutputVar = hasNoReturn === false || (hasNoReturn === true && this.state.editedNode.outputVar)

    this.generateUUID("fields", "parameters")
  }

  prepareNodeDef(node, nodeObjectDetails, processToDisplay) {
    if (NodeUtils.nodeType(node) === "Join") {
      return new JoinDef(node, nodeObjectDetails, processToDisplay)
    } else {
      return null
    }
  }

  // should it be here or somewhere else (in the reducer?)
  enrichNodeWithProcessDependentData(node) {
    if (NodeUtils.nodeType(node) === "Join") {
      node.branchParameters = this.nodeDef.incomingEdges.map((edge) => {
        let branchId = edge.from
        let existingBranchParams = node.branchParameters.find(p => p.branchId === branchId)
        let newBranchParams = this.nodeDef.branchParameters.map((branchParamDef) => {
          let existingParamValue = ((existingBranchParams || {}).parameters || []).find(p => p.name === branchParamDef.name)
          let templateParamValue = (node.branchParametersTemplate || []).find(p => p.name === branchParamDef.name);
          return existingParamValue || _.cloneDeep(templateParamValue) ||
              // We need to have this fallback to some template for situation when it is existing node and it has't got
              // defined parameters filled. see note in DefinitionPreparer on backend side TODO: remove it after API refactor
              _.cloneDeep({
                name: branchParamDef.name,
                expression: {
                  expression: `#${branchParamDef.name}`,
                  language: "spel",
                }
              })
        })
        return {
          branchId: branchId,
          parameters: newBranchParams
        }
      })
      delete node["branchParametersTemplate"]
    }
    return node
  }

  generateUUID(...properties) {
    properties.forEach((property) => {
      if (_.has(this.state.editedNode, property)) {
        let elements = _.get(this.state.editedNode, property);
        elements.map((el) => el.uuid = el.uuid || uuid4());
      }
    });
  }

  componentWillReceiveProps(nextProps) {
    if (!_.isEqual(this.props.node, nextProps.node)) {
      this.nodeObjectDetails = ProcessUtils.findNodeObjectTypeDefinition(nextProps.node, nextProps.processDefinitionData.processDefinition)
      this.setState({editedNode: nextProps.node})
    }
  }

  componentDidUpdate(prevProps, prevState) {
    if (!_.isEqual(prevProps.node, this.props.node) || !_.isEqual(prevProps.testResults, this.props.testResults)) {
      this.selectTestResults()
    }
  }

  findParamByName(paramName) {
    return (_.get(this.nodeObjectDetails, "parameters", [])).find((param) => param.name === paramName)
  }

  removeElement = (property, index)  => {
    if (_.has(this.state.editedNode, property)) {
      _.get(this.state.editedNode, property).splice(index, 1);

      this.setState((state, props) => ({editedNode: state.editedNode}), () => {
        this.props.onChange(this.state.editedNode);
      });
    }
  };

  addElement = (property, element) => {
    if (_.has(this.state.editedNode, property)) {
      _.get(this.state.editedNode, property).push(element);

      this.setState((state, props) => ({editedNode: state.editedNode}), () => {
        this.props.onChange(this.state.editedNode);
      });
    }
  };

  setNodeDataAt = (property, value) => {
    _.set(this.state.editedNode, property, value);

    this.setState((state, props) => ({editedNode: state.editedNode}), () => {
      this.props.onChange(this.state.editedNode);
    });
  };

  customNode = (fieldErrors) => {
    const {showValidation, showSwitch} = this.props

    switch (NodeUtils.nodeType(this.props.node)) {
      case 'Source':
        return this.sourceSinkCommon(null, fieldErrors)
      case 'Sink':
        const toAppend =
          <div>
            {
              //TODO: this is a bit clumsy. we should use some metadata, instead of relying on what comes in diagram
              this.props.node.endResult ? this.createExpressionField("expression", "Expression", "endResult", [notEmptyValidator, errorValidator(fieldErrors, DEFAULT_EXPRESSION_ID)]) : null
            }
            {this.createField("checkbox", "Disabled", "isDisabled")}
          </div>
        return this.sourceSinkCommon(toAppend, fieldErrors)
      case 'SubprocessInputDefinition':
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
      case 'SubprocessOutputDefinition':
        return (
          <div className="node-table-body">
            {this.createField("input", "Name", "id", true, [notEmptyValidator, errorValidator(fieldErrors, "id")])}
            {this.createField("input", "Output name", "outputName", false, [notEmptyValidator, errorValidator(fieldErrors, "outputName")])}
            {this.descriptionField()}
          </div>
        )
      case 'Filter':
        return (
          <div className="node-table-body">
            {this.createField("input", "Name", "id", true, [notEmptyValidator, errorValidator(fieldErrors, "id")])}
            {this.createExpressionField("expression", "Expression", "expression", [notEmptyValidator, errorValidator(fieldErrors, DEFAULT_EXPRESSION_ID)])}
            {this.createField("checkbox", "Disabled", "isDisabled")}
            {this.descriptionField()}
          </div>
        )
      case 'Enricher':
      case 'Processor':
        return (
          <div className="node-table-body">
            {this.createField("input", "Name", "id", true, [notEmptyValidator, errorValidator(fieldErrors, "id")])}
            {this.state.editedNode.service.parameters.map((param, index) => {
              return (
                <div className="node-block" key={this.props.node.id + param.name + index}>
                  {this.createExpressionListField(param.name, "expression", `service.parameters[${index}]`, [notEmptyValidator, errorValidator(fieldErrors, param.name)])}
                </div>
              )
            })}
            {this.props.node.type === 'Enricher' ? this.createField("input", "Output", "output", false, [notEmptyValidator, errorValidator(fieldErrors, "output")]) : null}
            {this.props.node.type === 'Processor' ? this.createField("checkbox", "Disabled", "isDisabled") : null}
            {this.descriptionField()}
          </div>
        )
      case 'SubprocessInput':
        return (
          <div className="node-table-body">
            {this.createField("input", "Name", "id", true, [notEmptyValidator, errorValidator(fieldErrors, "id")])}
            {this.createField("checkbox", "Disabled", "isDisabled")}
            <ParameterList
              processDefinitionData={this.props.processDefinitionData}
              editedNode={this.state.editedNode}
              savedNode={this.state.editedNode}
              setNodeState={newParams => this.setNodeDataAt('ref.parameters', newParams)}
              createListField={(param, index) => this.createExpressionListField(param.name, "expression", `ref.parameters[${index}]`, [notEmptyValidator, errorValidator(fieldErrors, param.name)])}
              createReadOnlyField={params => (
                <div className="node-row">{this.renderFieldLabel(params.name)}
                  <div className="node-value">
                    <input type="text"
                           className="node-input"
                           value={params.expression.expression}
                           disabled={true}/>
                  </div>
                </div>)}
            />
            {this.descriptionField()}
          </div>
        )

      case 'Join':
      case 'CustomNode':
        return (
          <div className="node-table-body">
            {this.createField("input", "Name", "id", true, [notEmptyValidator, errorValidator(fieldErrors, "id")])}

            {
              this.showOutputVar && this.createField("input", "Output", "outputVar", false, [notEmptyValidator, errorValidator(fieldErrors, "outputVar")], "outputVar", false, null)
            }
            {NodeUtils.nodeType(this.props.node) === 'Join' &&
            <BranchParameters
              onChange={this.setNodeDataAt}
              node={this.state.editedNode}
              joinDef={this.nodeDef}
              isMarked={this.isMarked}
              showValidation={showValidation}
              showSwitch={showSwitch}
              errors={fieldErrors}
            />
            }
            {(this.state.editedNode.parameters).map((param, index) => {
              return (
                <div className="node-block" key={this.props.node.id + param.name + index}>
                  {this.createExpressionListField(param.name, "expression", `parameters[${index}]`, [notEmptyValidator, errorValidator(fieldErrors, param.name)])}
                </div>
              )
            })}
            {this.descriptionField()}
          </div>
        )
      case 'VariableBuilder':
        return <MapVariable
          renderFieldLabel={this.renderFieldLabel}
          removeElement={this.removeElement}
          onChange={this.setNodeDataAt}
          node={this.state.editedNode}
          addElement={this.addElement}
          isMarked={this.isMarked}
          readOnly={!this.props.isEditMode}
          showValidation={showValidation}
          errors={fieldErrors}
        />;
      case 'Variable':
        return <Variable
          renderFieldLabel={this.renderFieldLabel}
          onChange={this.setNodeDataAt}
          node={this.state.editedNode}
          isMarked={this.isMarked}
          readOnly={!this.props.isEditMode}
          showValidation={showValidation}
          errors={fieldErrors}
        />;
      case 'Switch':
        return (
          <div className="node-table-body">
            {this.createField("input", "Name", "id", true, [notEmptyValidator, errorValidator(fieldErrors, "id")])}
            {this.createExpressionField("expression", "Expression", "expression", [notEmptyValidator, errorValidator(fieldErrors, DEFAULT_EXPRESSION_ID)])}
            {this.createField("input", "exprVal", "exprVal", false, [notEmptyValidator, errorValidator(fieldErrors, "exprVal")])}
            {this.descriptionField()}
          </div>
        )
      case 'Split':
        return (
          <div className="node-table-body">
            {this.createField("input", "Name", "id", true, [notEmptyValidator, errorValidator(fieldErrors, "id")])}
            {this.descriptionField()}
          </div>
        )
      case 'Properties':
        const type = this.props.node.typeSpecificProperties.type;
        const commonFields = this.subprocessVersionFields()
        //fixme move this configuration to some better place?
        const fields = type == "StreamMetaData" ? [
          this.createField("input", "Parallelism", "typeSpecificProperties.parallelism", true, [errorValidator(fieldErrors, "parallelism")], "parallelism", null, null, 'parallelism'),
          this.createField("input", "Checkpoint interval in seconds", "typeSpecificProperties.checkpointIntervalInSeconds", false, [errorValidator(fieldErrors, "checkpointIntervalInSeconds")], "checkpointIntervalInSeconds", null, null, 'interval-seconds'),
          this.createField("checkbox", "Should split state to disk", "typeSpecificProperties.splitStateToDisk", false, [errorValidator(fieldErrors, "splitStateToDisk")], "splitStateToDisk", false, false, 'split-state-disk'),
          this.createField("checkbox", "Should use async interpretation (lazy variables not allowed)", "typeSpecificProperties.useAsyncInterpretation", false, [errorValidator(fieldErrors, "useAsyncInterpretation")], "useAsyncInterpretation", false, false, 'use-async')
        ] : [this.createField("input", "Query path", "typeSpecificProperties.path", false, [errorValidator(fieldErrors, "path")], "path", null, null, 'query-path')]
        const additionalFields = Object.entries(this.props.additionalPropertiesConfig).map(
          ([fieldName, fieldConfig]) => this.createAdditionalField(fieldName, fieldConfig, fieldName, fieldErrors)
        );
        const hasExceptionHandlerParams = this.state.editedNode.exceptionHandler.parameters.length > 0
        return (
          <div className="node-table-body">
            {_.concat(fields, commonFields, additionalFields)}
            {hasExceptionHandlerParams ?
              (<div className="node-row">
                <div className="node-label">Exception handler:</div>
                <div className="node-group">
                  {this.state.editedNode.exceptionHandler.parameters.map((param, index) => {
                    return (
                      <div className="node-block" key={this.props.node.id + param.name + index}>
                        {this.createExpressionListField(param.name, "expression", `exceptionHandler.parameters[${index}]`, [notEmptyValidator, errorValidator(fieldErrors, param.name)], "String")}
                      </div>
                    )
                  })}
                </div>
              </div>) : null
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
  };

  createAdditionalField(fieldName, fieldConfig, key, fieldErrors) {
    const readOnly = !this.props.isEditMode
    const {showSwitch, showValidation} = this.props
    if (fieldConfig.type === "select") {
      const values = _.map(fieldConfig.values, v => ({expression: v, label: v}))
      const current = _.get(this.state.editedNode, `additionalFields.properties.${fieldName}`)
      const obj = {expression: current, value: current}

      return (
        <EditableExpression
          fieldType={"expressionWithFixedValues"}
          fieldLabel={fieldConfig.label}
          onValueChange={(newValue) => this.setNodeDataAt(`additionalFields.properties.${fieldName}`, newValue)}
          expressionObj={obj}
          renderFieldLabel={this.renderFieldLabel}
          values={values}
          readOnly={readOnly}
          key={key}
          showSwitch={showSwitch}
          showValidation={showValidation}
        />
      )
    } else {
      const fieldType = () => {
        if (fieldConfig.type == "text") return "plain-textarea";
        else return "input";
      };

      return this.createField(fieldType(), fieldConfig.label, `additionalFields.properties.${fieldName}`, false, [errorValidator(fieldErrors, fieldName)], fieldName, null, null, key)
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
        'subprocess-versions'
      )]
  }

  sourceSinkCommon(toAppend, fieldErrors) {
    return (
      <div className="node-table-body">
        {this.createField("input", "Name", "id", true, [notEmptyValidator, errorValidator(fieldErrors, "Id")])}
        {this.state.editedNode.ref.parameters.map((param, index) => {
          return (
            <div className="node-block" key={this.props.node.id + param.name + index}>
              {this.createExpressionListField(param.name, "expression", `ref.parameters[${index}]`, [notEmptyValidator, errorValidator(fieldErrors, param.name)])}
            </div>
          )
        })}
        {toAppend}
        {this.descriptionField()}
      </div>
    )
  }

  createReadonlyField = (fieldType, fieldLabel, fieldProperty) => {
    return this.createField(fieldType, fieldLabel, fieldProperty, false, [], null, true)
  }

  createField = (fieldType, fieldLabel, fieldProperty, autofocus = false, validators = [], fieldName, readonly, defaultValue, key) => {
    return this.doCreateField(
      fieldType,
      fieldLabel,
      fieldName,
      _.get(this.state.editedNode, fieldProperty, ""),
      ((newValue) => this.setNodeDataAt(fieldProperty, newValue, defaultValue)),
      readonly,
      this.isMarked(fieldProperty),
      key,
      autofocus,
      validators
    )
  }

  createListField = (fieldType, fieldLabel, obj, fieldProperty, listFieldProperty, fieldName) => {
    const path = `${listFieldProperty}.${fieldProperty}`

    return this.doCreateField(
      fieldType,
      fieldLabel,
      fieldName,
      _.get(obj, fieldProperty),
      ((newValue) => this.setNodeDataAt(path, newValue)),
      null,
      this.isMarked(path)
    )
  }

  createExpressionField = (fieldName, fieldLabel, expressionProperty, validators) =>
    this.doCreateExpressionField(fieldName, fieldLabel, `${expressionProperty}`, validators);

  createExpressionListField = (fieldName, expressionProperty, listFieldPath, validators, fieldType) =>
    this.doCreateExpressionField(fieldName, fieldName, `${listFieldPath}.${expressionProperty}`, validators, fieldType);

  doCreateExpressionField = (fieldName, fieldLabel, exprPath, validators, fieldType) => {
    const {showValidation, showSwitch, isEditMode} = this.props
    return (
      <ExpressionField
        fieldName={fieldName}
        fieldLabel={fieldLabel}
        fieldType={fieldType}
        exprPath={exprPath}
        validators={validators}
        isEditMode={isEditMode}
        editedNode={this.state.editedNode}
        isMarked={this.isMarked}
        showValidation={showValidation}
        showSwitch={showSwitch}
        nodeObjectDetails={this.nodeObjectDetails}
        setNodeDataAt={this.setNodeDataAt}
        testResultsToShow={this.state.testResultsToShow}
        testResultsToHide={this.state.testResultsToHide}
        toggleTestResult={this.toggleTestResult}
        renderFieldLabel={this.renderFieldLabel}
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

  doCreateField = (fieldType, fieldLabel, fieldName, fieldValue, handleChange, forceReadonly, isMarked, key, autofocus = false, validators = []) => {
    const readOnly = !this.props.isEditMode || forceReadonly
    const showValidation = this.props.showValidation

    return <Field fieldType={fieldType}
                  renderFieldLabel={() => this.renderFieldLabel(fieldLabel)}
                  isMarked={isMarked}
                  readOnly={readOnly}
                  value={fieldValue || ""}
                  autofocus={autofocus}
                  showValidation={showValidation}
                  validators={validators}
                  onChange={handleChange}
                  className={(!showValidation || allValid(validators, [fieldValue])) ? "node-input" : "node-input node-input-with-error"}
    />
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
    const parameter = this.findParamByName(label)
    return (
      <div className="node-label" title={label}>{label}:
        {parameter ?
          <div className="labelFooter">{ProcessUtils.humanReadableType(parameter.typ.refClazzName)}</div> : null}
      </div>)
  }

  fieldErrors = (errors) => {
    return errors.filter(error => error.fieldName &&
      this.availableFields().includes(error.fieldName)) || []
  }

  availableFields = () => {
    switch (NodeUtils.nodeType(this.state.editedNode)) {
      case 'Source': {
        const commonFields = ["id"]
        return _.concat(commonFields, this.state.editedNode.ref.parameters.map(param => param.name))
      }
      case 'Sink': {
        const commonFields = ["id", DEFAULT_EXPRESSION_ID]
        return _.concat(commonFields, this.state.editedNode.ref.parameters.map(param => param.name))
      }
      case 'SubprocessInputDefinition': {
        return ["id"]
      }
      case 'SubprocessOutputDefinition':
        return ["id", "outputName"]
      case 'Filter':
        return ["id", DEFAULT_EXPRESSION_ID]
      case 'Enricher':
        const commonFields = ["id", "output"]
        const paramFields = this.state.editedNode.service.parameters.map(param => param.name);
        return _.concat(commonFields, paramFields)
      case 'Processor': {
        const commonFields = ["id"]
        const paramFields = this.state.editedNode.service.parameters.map(param => param.name);
        return _.concat(commonFields, paramFields)
      }
      case 'SubprocessInput': {
        const commonFields = ["id"]
        const paramFields = this.state.editedNode.ref.parameters.map(param => param.name);
        return _.concat(commonFields, paramFields)
      }
      case 'Join': {
        const commonFields = ["id", "outputVar"]
        const paramFields = this.state.editedNode.parameters.map(param => param.name)
        const branchParamsFields = this.state.editedNode.branchParameters
          .flatMap(branchParam => branchParam.parameters.map(param => branchErrorFieldName(param.name, branchParam.branchId)))
        return _.concat(commonFields, paramFields, branchParamsFields)
      }
      case 'CustomNode': {
        const commonFields = ["id", "outputVar"]
        const paramFields = this.state.editedNode.parameters.map(param => param.name)
        return _.concat(commonFields, paramFields)
      }
      case 'VariableBuilder':
        return MapVariable.availableFields(this.state.editedNode)
      case 'Variable':
        return Variable.availableFields
      case 'Switch':
        return ["id", DEFAULT_EXPRESSION_ID, "exprVal"]
      case 'Split':
        return ["id"]
      case 'Properties': {
        const commonFields = "subprocessVersions"
        const fields = this.props.node.typeSpecificProperties.type === "StreamMetaData" ?
          ["parallelism", "checkpointIntervalInSeconds", "splitStateToDisk", "useAsyncInterpretation"] : ["path"]
        const additionalFields = Object.entries(this.props.additionalPropertiesConfig).map(([fieldName, fieldConfig]) => fieldName);
        const exceptionHandlerFields = this.state.editedNode.exceptionHandler.parameters.map(param => param.name)
        return _.concat(commonFields, fields, additionalFields, exceptionHandlerFields)
      }
      default:
        return []
    }
  }

  render() {
    const nodeClass = classNames('node-table', {'node-editable': this.props.isEditMode})
    const fieldErrors = this.fieldErrors(this.props.nodeErrors || [])
    const otherErrors = this.props.nodeErrors ? this.props.nodeErrors.filter(error => !fieldErrors.includes(error)) : []
    return (
      <div className={nodeClass}>
        <NodeErrors errors={otherErrors} message={'Node has errors'}/>
        <TestResultsSelect
          results={this.props.testResults}
          resultsIdToShow={this.state.testResultsIdToShow}
          selectResults={this.selectTestResults}
        />
        <TestErrors resultsToShow={this.state.testResultsToShow}/>
        {this.customNode(fieldErrors)}
        <TestResults nodeId={this.props.node.id} resultsToShow={this.state.testResultsToShow}/>
      </div>
    )
  }
}

function mapState(state) {
  return {
    additionalPropertiesConfig: _.get(state.settings, 'processDefinitionData.additionalPropertiesConfig') || {},
    processDefinitionData: state.settings.processDefinitionData || {},
    processToDisplay: state.graphReducer.processToDisplay
  }
}

export default connect(mapState, ActionsUtils.mapDispatchWithEspActions)(NodeDetailsContent)


class NodeDetails extends React.Component {
  render() {
    return (
      <div>
        <pre>{JSON.stringify(this.props.node, null, 2)}</pre>
      </div>
    );
  }
}
