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
import Variable from "./node-modal/Variable";

//move state to redux?
// here `componentDidUpdate` is complicated to clear unsaved changes in modal
export class NodeDetailsContent extends React.Component {

  constructor(props) {
    super(props);

    this.state = {
      ...TestRenderUtils.stateForSelectTestResults(null, this.props.testResults),
      editedNode: _.cloneDeep(props.node),
      codeCompletionEnabled: true,
      testResultsToHide: new Set(),
    };

    this.nodeObjectDetails = ProcessUtils.findNodeObjectTypeDefinition(this.props.node, this.props.processDefinitionData.processDefinition);

    let hasNoReturn = _.isNull(this.nodeObjectDetails.returnType)
    this.showOutputVar = hasNoReturn  === false || (hasNoReturn === true && this.state.editedNode.outputVar)

    this.generateUUID("fields");
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

      this.setState((state, props) => {editedNode: state.editedNode}, () => {
        this.props.onChange(this.state.editedNode);
      });
    }
  };

  addElement = (property, element) => {
    if (_.has(this.state.editedNode, property)) {
      _.get(this.state.editedNode, property).push(element);

      this.setState((state, props) => {editedNode: state.editedNode}, () => {
        this.props.onChange(this.state.editedNode);
      });
    }
  };

  setNodeDataAt = (property, value) => {
    _.set(this.state.editedNode, property, value);

    this.setState((state, props) => {editedNode: state.editedNode}, () => {
      this.props.onChange(this.state.editedNode);
    });
  };

  customNode = () => {
    switch (NodeUtils.nodeType(this.props.node)) {
      case 'Source':
        return this.sourceSinkCommon()
      case 'Sink':
        const toAppend =
          <div>
            {this.createExpressionField("expression", "Expression", "endResult")}
            {this.createField("checkbox", "Disabled", "isDisabled")}
          </div>
        return this.sourceSinkCommon(toAppend)
      case 'SubprocessInputDefinition':
        //FIXME: currently there is no way to add new parameters or display them correctly
        return (
          <div className="node-table-body">
            {this.createField("input", "Id", "id")}

            <div className="node-row">
              {this.renderFieldLabel("Parameters")}
              <div className="node-value">
                <Fields fields={this.state.editedNode.parameters || []} fieldCreator={(field, onChange) =>
                  (<input type="text" className="node-input" value={field.typ.refClazzName}
                          onChange={(e) => onChange({typ: {refClazzName: e.target.value}})}/>)}
                  onChange={(fields) => this.setNodeDataAt("parameters", fields)}
                  newValue={{name: "", typ: {refClazzName: ""}}}
                  isMarked={index => this.isMarked(`parameters[${index}].name`) || this.isMarked(`parameters[${index}].typ.refClazzName`)}
                />
              </div>
            </div>
            {this.descriptionField()}
          </div>
        )
      case 'SubprocessOutputDefinition':
        return (
          <div className="node-table-body">
            {this.createField("input", "Id", "id")}
            {this.createField("input", "Output name", "outputName")}
            {this.descriptionField()}
          </div>
        )
      case 'Filter':
        return (
          <div className="node-table-body">
            {this.createField("input", "Id", "id")}
            {this.createExpressionField("expression", "Expression", "expression")}
            {this.createField("checkbox", "Disabled", "isDisabled")}
            {this.descriptionField()}
          </div>
        )
      case 'Enricher':
      case 'Processor':
        return (
          <div className="node-table-body">
            {this.createField("input", "Id", "id")}
            {this.createReadonlyField("input", "Service Id", "service.id")}
            {this.state.editedNode.service.parameters.map((param, index) => {
              return (
                <div className="node-block" key={this.props.node.id + param.name + index}>
                  {this.createExpressionListField(param.name, "expression", `service.parameters[${index}]`)}
                </div>
              )
            })}
            {this.props.node.type === 'Enricher' ? this.createField("input", "Output", "output") : null }
            {this.props.node.type === 'Processor' ? this.createField("checkbox", "Disabled", "isDisabled") : null }
            {this.descriptionField()}
          </div>
        )
      case 'SubprocessInput':
        return (
          <div className="node-table-body">
            {this.createField("input", "Id", "id")}
            {this.createReadonlyField("input", "Subprocess Id", "ref.id")}
            {this.createField("checkbox", "Disabled", "isDisabled")}
            <ParameterList
              processDefinitionData={this.props.processDefinitionData}
              editedNode={this.state.editedNode}
              savedNode={this.state.editedNode}
              setNodeState={newParams => this.setNodeDataAt('ref.parameters', newParams)}
              createListField={(param, index) => this.createExpressionListField(param.name, "expression", `ref.parameters[${index}]`)}
              createReadOnlyField={params => (<div className="node-row">
                {this.renderFieldLabel(params.name)}
                <div className="node-value"><input type="text" className="node-input"
                                                   value={params.expression.expression}
                                                   disabled={true}/></div>
              </div>)}
            />
            {this.descriptionField()}
          </div>
        )

      case 'Join':
      case 'CustomNode':
        return (
          <div className="node-table-body">
            {this.createField("input", "Id", "id")}

            {
              this.showOutputVar && this.createField("input", "Output", "outputVar", "outputVar", false, null)
            }
            {this.createReadonlyField("input", "Node type", "nodeType")}
            {(this.state.editedNode.parameters || this.state.editedNode.ref.parameters).map((param, index) => {
              return (
                <div className="node-block" key={this.props.node.id + param.name + index}>
                  {this.createExpressionListField(param.name, "expression", `parameters[${index}]`)}
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
        />;
      case 'Variable':
        return <Variable
            onChange={this.setNodeDataAt}
            node={this.state.editedNode}
            isMarked={this.isMarked}
        />;
      case 'Switch':
        return (
          <div className="node-table-body">
            {this.createField("input", "Id", "id")}
            {this.createExpressionField("expression", "Expression", "expression")}
            {this.createField("input", "exprVal", "exprVal")}
            {this.descriptionField()}
          </div>
        )
      case 'Split':
        return (
          <div className="node-table-body">
            {this.createField("input", "Id", "id")}
            {this.descriptionField()}
          </div>
        )
      case 'Properties':
        const type = this.props.node.typeSpecificProperties.type;
        const commonFields = this.subprocessVersionFields()
        //fixme move this configuration to some better place?
        const fields = type == "StreamMetaData" ? [
          this.createField("input", "Parallelism", "typeSpecificProperties.parallelism", "parallelism", null, null, uuid4()),
          this.createField("input", "Checkpoint interval in seconds", "typeSpecificProperties.checkpointIntervalInSeconds", "checkpointIntervalInSeconds", null, null, uuid4()),
          this.createField("checkbox", "Should split state to disk", "typeSpecificProperties.splitStateToDisk", "splitStateToDisk", null, null, uuid4()),
          this.createField("checkbox", "Should use async interpretation (lazy variables not allowed)", "typeSpecificProperties.useAsyncInterpretation", "useAsyncInterpretation", null, null, uuid4())
        ] : [this.createField("input", "Query path",  "typeSpecificProperties.path", "path", null, null, uuid4())]
        const additionalFields = Object.entries(this.props.additionalPropertiesConfig).map(
          ([fieldName, fieldConfig]) => this.createAdditionalField(fieldName, fieldConfig)
        );
        const hasExceptionHandlerParams = this.state.editedNode.exceptionHandler.parameters.length > 0
        return (
          <div className="node-table-body">
            { _.concat(fields, commonFields, additionalFields) }
            { hasExceptionHandlerParams ?
              (<div className="node-row">
                <div className="node-label">Exception handler:</div>
                <div className="node-group">
                  {this.state.editedNode.exceptionHandler.parameters.map((param, index) => {
                    return (
                      <div className="node-block" key={this.props.node.id + param.name + index}>
                        {this.createExpressionListField(param.name, "expression", `exceptionHandler.parameters[${index}]`)}
                        <hr />
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

  createAdditionalField(fieldName, fieldConfig, key) {
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
        readOnly={false}
        key={key}
      />;
    } else {
      const fieldType = () => {
        if (fieldConfig.type == "text") return "plain-textarea";
        else return "input";
      };

      return this.createField(fieldType(), fieldConfig.label, `additionalFields.properties.${fieldName}`, fieldName, null, null, key)
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
          uuid4()
      )]
  }

  sourceSinkCommon(toAppend) {
    return (
      <div className="node-table-body">
        {this.createField("input", "Id", "id")}
        {this.createReadonlyField("input", "Ref Type", "ref.typ")}
        {this.state.editedNode.ref.parameters.map((param, index) => {
          return (
            <div className="node-block" key={this.props.node.id + param.name + index}>
              {this.createExpressionListField(param.name, "expression", `ref.parameters[${index}]`)}
            </div>
          )
        })}
        {toAppend}
        {this.descriptionField()}
      </div>
    )
  }

  createReadonlyField = (fieldType, fieldLabel, fieldProperty) => {
    return this.createField(fieldType, fieldLabel, fieldProperty, null, true)
  }

  createField = (fieldType, fieldLabel, fieldProperty, fieldName, readonly, defaultValue, key) => {
    return this.doCreateField(
        fieldType,
        fieldLabel,
        fieldName,
        _.get(this.state.editedNode, fieldProperty, ""),
        ((newValue) => this.setNodeDataAt(fieldProperty, newValue, defaultValue)),
        readonly,
        this.isMarked(fieldProperty),
        key
    )
  }

  createListField = (fieldType, fieldLabel, obj, fieldProperty, listFieldProperty, fieldName) => {
    const path = `${listFieldProperty}.${fieldProperty}`

    return this.doCreateField(
        fieldType,
        fieldLabel,
        fieldName,
        _.get(obj, fieldProperty),
        ((newValue) => this.setNodeDataAt(path, newValue) ),
        null,
        this.isMarked(path)
    )
  }

  createExpressionField = (fieldName, fieldLabel, expressionProperty) =>
    this.doCreateExpressionField(fieldName, fieldLabel, `${expressionProperty}`);

  createExpressionListField = (fieldName, expressionProperty, listFieldPath) =>
    this.doCreateExpressionField(fieldName, fieldName, `${listFieldPath}.${expressionProperty}`);

  doCreateExpressionField = (fieldName, fieldLabel, exprPath) => {
    const exprTextPath = `${exprPath}.expression`;
    const expressionObj = _.get(this.state.editedNode, exprPath);
    const isMarked = this.isMarked(exprTextPath);
    const nodeValueClass = this.nodeValueClass(isMarked);
    const restriction = this.getRestriction(fieldName);

    if (restriction.hasFixedValues)
      return <ExpressionWithFixedValues
        fieldLabel={fieldLabel}
        onChange={(newValue) => this.setNodeDataAt(exprTextPath, newValue)}
        obj={expressionObj}
        renderFieldLabel={this.renderFieldLabel}
        values={restriction.values}
        readOnly={false}
      />;

    return TestRenderUtils.wrapWithTestResult(fieldName, this.state.testResultsToShow, this.state.testResultsToHide, this.toggleTestResult, (
      <div className="node-row">
        {this.renderFieldLabel(fieldLabel)}
        <div className={nodeValueClass}>
          <ExpressionSuggest fieldName={fieldName} inputProps={{
            rows: 1, cols: 50, className: "node-input", value: expressionObj.expression, language: expressionObj.language,
            onValueChange: ((newValue) => this.setNodeDataAt(exprTextPath, newValue)), readOnly: false}}/>
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

  doCreateField = (fieldType, fieldLabel, fieldName, fieldValue, handleChange, forceReadonly, isMarked, key) => {
    const readOnly = !this.props.isEditMode || forceReadonly;
    const nodeValueClass = this.nodeValueClass(isMarked);

    switch (fieldType) {
      case 'input':
        return (
          <div className="node-row" key={key}>
            {this.renderFieldLabel(fieldLabel)}
            <div className={nodeValueClass}>
              <input
                  type="text"
                  className="node-input"
                  value={fieldValue || ""}
                  onChange={(e) => handleChange(e.target.value)}
                  readOnly={readOnly}
              />
            </div>
          </div>
        )
      case 'checkbox': {
        return (
          <div className="node-row" key={key}>
            {this.renderFieldLabel(fieldLabel)}
            <div className={nodeValueClass}>
              <input
                  type="checkbox"
                  checked={fieldValue}
                  onChange={(e) => handleChange(fieldValue ? false : true)}
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
            <div className={nodeValueClass}>
              <Textarea
                  rows={1}
                  cols={50}
                  className="node-input"
                  value={fieldValue || ""}
                  onChange={(e) => handleChange(e.target.value)}
                  readOnly={readOnly}
              />
            </div>
          </div>
        )
      default:
        return (
          <div key={keyg}>
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

  nodeValueClass = (isMarked) => "node-value" + (isMarked ? " marked" : "");

  setNodeDataAt = (propToMutate, newValue, defaultValue) => {
    if (_.isEmpty(newValue) && !_.isUndefined(defaultValue)) {
      newValue = defaultValue;
    }

    var newtempNodeData = _.cloneDeep(this.state.editedNode)
    _.set(newtempNodeData, propToMutate, newValue)
    this.setState({editedNode: newtempNodeData})
    this.props.onChange(newtempNodeData)
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
        {parameter ? <div className="labelFooter">{ProcessUtils.humanReadableType(parameter.typ.refClazzName)}</div> : null}
    </div>)
  }

  render() {
    var nodeClass = classNames('node-table', {'node-editable': this.props.isEditMode})
    return (
      <div className={nodeClass}>
        {ModalRenderUtils.renderErrors(this.props.nodeErrors, 'Node has errors')}
        {TestRenderUtils.testResultsSelect(this.props.testResults, this.state.testResultsIdToShow, this.selectTestResults)}
        {TestRenderUtils.testErrors(this.state.testResultsToShow)}
        {this.customNode()}
        {TestRenderUtils.testResults(this.props.node.id, this.state.testResultsToShow)}
      </div>
    )
  }
}

function mapState(state) {
  return {
    additionalPropertiesConfig: _.get(state.settings, 'processDefinitionData.additionalPropertiesConfig') || {}
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
