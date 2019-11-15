import React from "react";
import {connect} from "react-redux";
import classNames from "classnames";
import _ from "lodash";
import ActionsUtils from "../../actions/ActionsUtils";
import Textarea from "react-textarea-autosize";
import NodeUtils from "./NodeUtils";
import ExpressionSuggest from "./ExpressionSuggest";
import ModalRenderUtils from "./ModalRenderUtils";
import * as TestRenderUtils from "./TestRenderUtils";
import ProcessUtils from '../../common/ProcessUtils';
import * as JsonUtils from '../../common/JsonUtils';
import Fields from "../Fields";
import ParameterList from "./ParameterList";
import ExpressionWithFixedValues from "./ExpressionWithFixedValues";
import {v4 as uuid4} from "uuid";
import MapVariable from "./node-modal/MapVariable";
import BranchParameters, {branchErrorFieldName} from "./node-modal/BranchParameters";
import Variable from "./node-modal/Variable";
import JoinDef from "./node-modal/JoinDef"
import {allValid, errorValidator, notEmptyValidator} from "../../common/Validators";
import ValidationLabels from "../modals/ValidationLabels";

//move state to redux?
// here `componentDidUpdate` is complicated to clear unsaved changes in modal
export class NodeDetailsContent extends React.Component {

  constructor(props) {
    super(props);

    this.nodeObjectDetails = ProcessUtils.findNodeObjectTypeDefinition(this.props.node, this.props.processDefinitionData.processDefinition);

    this.nodeDef = this.prepareNodeDef(props.node, this.nodeObjectDetails, props.processToDisplay)

    this.state = {
      ...TestRenderUtils.stateForSelectTestResults(null, this.props.testResults),
      editedNode: this.enrichNodeWithProcessDependentData(_.cloneDeep(props.node)),
      codeCompletionEnabled: true,
      testResultsToHide: new Set(),
    };

    let hasNoReturn = this.nodeObjectDetails == null || this.nodeObjectDetails.returnType == null
    this.showOutputVar = hasNoReturn === false || (hasNoReturn === true && this.state.editedNode.outputVar)

    this.generateUUID("fields");
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
    const showValidation = this.props.showValidation
    switch (NodeUtils.nodeType(this.props.node)) {
      case 'Source':
        return this.sourceSinkCommon(null, fieldErrors)
      case 'Sink':
        const toAppend =
          <div>
            {
              //TODO: this is a bit clumsy. we should use some metadata, instead of relying on what comes in diagram
              this.props.node.endResult ? this.createExpressionField("expression", "Expression", "endResult", [notEmptyValidator, errorValidator(fieldErrors, "expression")]) : null
            }
            {this.createField("checkbox", "Disabled", "isDisabled")}
          </div>
        return this.sourceSinkCommon(toAppend, fieldErrors)
      case 'SubprocessInputDefinition':
        //FIXME: currently there is no way to add new parameters or display them correctly
        return (
          <div className="node-table-body">
            {this.createField("input", "Id", "id", true, [notEmptyValidator, errorValidator(fieldErrors, "Id")])}
            <div className="node-row">
              {this.renderFieldLabel("Parameters")}
              <div className="node-value">
                <Fields
                  fields={this.state.editedNode.parameters || []}
                  fieldCreator={(field, onChange) => {
                    const validators = [notEmptyValidator]
                    return (<React.Fragment>
                      <input type="text"
                             className={(showValidation || allValid(validators, [field.typ.refClazzName]) ? "node-input" : "node-input node-input-with-error")}
                             value={field.typ.refClazzName}
                             onChange={(e) => onChange({typ: {refClazzName: e.target.value}})}/>
                        {!showValidation && <ValidationLabels validators={validators} values={[field.typ.refClazzName]}/>}
                    </React.Fragment>)
                  }}
                  onChange={(fields) => this.setNodeDataAt("parameters", fields)}
                  newValue={{name: "", typ: {refClazzName: ""}}}
                  isMarked={index => this.isMarked(`parameters[${index}].name`) || this.isMarked(`parameters[${index}].typ.refClazzName`)}
                  showValidation={showValidation}
                />
              </div>
            </div>
            {this.descriptionField()}
          </div>
        )
      case 'SubprocessOutputDefinition':
        return (
          <div className="node-table-body">
            {this.createField("input", "Id", "id", true, [notEmptyValidator, errorValidator(fieldErrors, "id")])}
            {this.createField("input", "Output name", "outputName", false, [notEmptyValidator, errorValidator(fieldErrors, "outputName")])}
            {this.descriptionField()}
          </div>
        )
      case 'Filter':
        return (
          <div className="node-table-body">
            {this.createField("input", "Id", "id", true, [notEmptyValidator, errorValidator(fieldErrors, "id")])}
            {this.createExpressionField("expression", "Expression", "expression", [notEmptyValidator, errorValidator(fieldErrors, "expression")])}
            {this.createField("checkbox", "Disabled", "isDisabled")}
            {this.descriptionField()}
          </div>
        )
      case 'Enricher':
      case 'Processor':
        return (
          <div className="node-table-body">
            {this.createField("input", "Id", "id", true, [notEmptyValidator, errorValidator(fieldErrors, "id")])}
            {this.createReadonlyField("input", "Service Id", "service.id")}
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
            {this.createField("input", "Id", "id", true, [notEmptyValidator, errorValidator(fieldErrors, "id")])}
            {this.createReadonlyField("input", "Subprocess Id", "ref.id")}
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
            {this.createField("input", "Id", "id", true, [notEmptyValidator, errorValidator(fieldErrors, "id")])}

            {
              this.showOutputVar && this.createField("input", "Output", "outputVar", false, [notEmptyValidator, errorValidator(fieldErrors, "outputVar")], "outputVar", false, null)
            }
            {this.createReadonlyField("input", "Node type", "nodeType")}
            {NodeUtils.nodeType(this.props.node) === 'Join' &&
            <BranchParameters
              onChange={this.setNodeDataAt}
              node={this.state.editedNode}
              joinDef={this.nodeDef}
              isMarked={this.isMarked}
              showValidation={showValidation}
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
            {this.createField("input", "Id", "id", true, [notEmptyValidator, errorValidator(fieldErrors, "id")])}
            {this.createExpressionField("expression", "Expression", "expression", [notEmptyValidator, errorValidator(fieldErrors, "expression")])}
            {this.createField("input", "exprVal", "exprVal", false, [notEmptyValidator, errorValidator(fieldErrors, "exprVal")])}
            {this.descriptionField()}
          </div>
        )
      case 'Split':
        return (
          <div className="node-table-body">
            {this.createField("input", "Id", "id", true, [notEmptyValidator, errorValidator(fieldErrors, "id")])}
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
                        {this.createExpressionListField(param.name, "expression", `exceptionHandler.parameters[${index}]`, [notEmptyValidator, errorValidator(fieldErrors, param.name)])}
                        <hr/>
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
    const readOnly = !this.props.isEditMode;
    if (fieldConfig.type === "select") {
      const values = _.map(fieldConfig.values, v => ({expression: v, label: v}));
      const current = _.get(this.state.editedNode, `additionalFields.properties.${fieldName}`);
      const obj = {expression: current, value: current};

      return <ExpressionWithFixedValues
        fieldLabel={fieldConfig.label}
        onChange={(newValue) => this.setNodeDataAt(`additionalFields.properties.${fieldName}`, newValue)}
        obj={obj}
        renderFieldLabel={this.renderFieldLabel}
        values={values}
        readOnly={readOnly}
        key={key}
      />;
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
        {this.createField("input", "Id", "id", true, [notEmptyValidator, errorValidator(fieldErrors, "Id")])}
        {this.createReadonlyField("input", "Ref Type", "ref.typ")}
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

  createExpressionListField = (fieldName, expressionProperty, listFieldPath, validators) =>
    this.doCreateExpressionField(fieldName, fieldName, `${listFieldPath}.${expressionProperty}`, validators);

  doCreateExpressionField = (fieldName, fieldLabel, exprPath, validators) => {
    const readOnly = !this.props.isEditMode;
    const exprTextPath = `${exprPath}.expression`;
    const expressionObj = _.get(this.state.editedNode, exprPath);
    const isMarked = this.isMarked(exprTextPath);
    const restriction = this.getRestriction(fieldName);
    const showValidation = this.props.showValidation

    if (restriction.hasFixedValues)
      return <ExpressionWithFixedValues
        fieldLabel={fieldLabel}
        onChange={(newValue) => this.setNodeDataAt(exprTextPath, newValue)}
        obj={expressionObj}
        renderFieldLabel={this.renderFieldLabel}
        values={restriction.values}
        readOnly={readOnly}
      />;

    return TestRenderUtils.wrapWithTestResult(fieldName, this.state.testResultsToShow, this.state.testResultsToHide, this.toggleTestResult, (
      <div className="node-row">
        {this.renderFieldLabel(fieldLabel)}
        <div className={"node-value"}>
          <ExpressionSuggest
            fieldName={fieldName}
            inputProps={{
              rows: 1, cols: 50,
              className: "node-input",
              value: expressionObj.expression,
              language: expressionObj.language,
              onValueChange: ((newValue) => this.setNodeDataAt(exprTextPath, newValue)), readOnly: readOnly}}
            validators={validators}
            isMarked={isMarked}
            showValidation={showValidation}
          />
        </div>
      </div>)
    )
  };

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
    switch (fieldType) {
      case 'input':
        return (
          <div className="node-row" key={key}>
            {this.renderFieldLabel(fieldLabel)}
            <div className={"node-value"}>
              <div className={isMarked ? " marked" : ""}>
                {
                  readOnly ?
                    <div className="read-only" title={fieldValue}>{fieldValue}</div> :
                    <input
                      autoFocus={autofocus}
                      type="text"
                      className={showValidation || allValid(validators, [fieldValue]) ? "node-input" : "node-input node-input-with-error"}
                      value={fieldValue || ""}
                      onChange={(e) => handleChange(e.target.value)}
                    />
                }
              </div>
              {!showValidation && <ValidationLabels validators={validators} values={[fieldValue]}/>}
            </div>
          </div>
        )
      case 'checkbox': {
        return (
          <div className="node-row" key={key}>
            {this.renderFieldLabel(fieldLabel)}
            <div className={"node-value" + (isMarked ? " marked" : "")}>
              <input
                  autoFocus={autofocus}
                  type="checkbox"
                  checked={fieldValue || false}
                  onChange={(e) => handleChange(e.target.checked)}
                  disabled={readOnly ? 'disabled' : ''}
              />
            </div>
          </div>
        )
      }
      case 'plain-textarea':
        return (
          <div className="node-row" key={key}>
            {this.renderFieldLabel(fieldLabel)}
            <div className={"node-value" + (isMarked ? " marked" : "")}>
              <Textarea
                autoFocus={autofocus}
                rows={1}
                cols={50}
                className={(showValidation || allValid(validators, [fieldValue])) ? "node-input" : "node-input node-input-with-error"}
                value={fieldValue || ""}
                onChange={(e) => handleChange(e.target.value)}
                readOnly={readOnly}
              />
            </div>
            {!showValidation && <ValidationLabels validators={validators} values={[fieldValue]}/>}
          </div>
        )
      default:
        return (
          <div key={key}>
            Field type not known...
          </div>
        )
    }
  }

  getRestriction = (fieldName) => {
    const restriction = (this.findParamByName(fieldName) || {}).restriction;
    return {
      hasFixedValues: restriction && restriction.type === "FixedExpressionValues",
      values: restriction && restriction.values
    }
  };

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
    const stateForSelect = TestRenderUtils.stateForSelectTestResults(id, testResults)
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
        const commonFields = ["id", "expression"]
        return _.concat(commonFields, this.state.editedNode.ref.parameters.map(param => param.name))
      }
      case 'SubprocessInputDefinition': {
        return ["id"]
      }
      case 'SubprocessOutputDefinition':
        return ["id", "outputName"]
      case 'Filter':
        return ["id", "expression"]
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
        return ["id", "expression", "exprVal"]
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
    const nodeClass = classNames('node-table', {'node-editable': this.props.isEditMode});
    const fieldErrors = this.fieldErrors(this.props.nodeErrors || [])
    const otherErrors = this.props.nodeErrors ? this.props.nodeErrors.filter(error => !fieldErrors.includes(error)) : []
    return (
      <div className={nodeClass}>
        {ModalRenderUtils.renderOtherErrors(otherErrors, 'Node has errors')}
        {TestRenderUtils.testResultsSelect(this.props.testResults, this.state.testResultsIdToShow, this.selectTestResults)}
        {TestRenderUtils.testErrors(this.state.testResultsToShow)}
        {this.customNode(fieldErrors)}
        {TestRenderUtils.testResults(this.props.node.id, this.state.testResultsToShow)}
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
