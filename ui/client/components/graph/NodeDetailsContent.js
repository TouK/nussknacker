import React, {Component} from "react";
import {render} from "react-dom";
import classNames from "classnames";
import _ from "lodash";
import Textarea from "react-textarea-autosize";
import NodeUtils from "./NodeUtils";
import ExpressionSuggest from "./ExpressionSuggest";
import ModalRenderUtils from "./ModalRenderUtils";
import TestResultUtils from "../../common/TestResultUtils";
import ProcessUtils from '../../common/ProcessUtils';

//move state to redux?
// here `componentDidUpdate` is complicated to clear unsaved changes in modal
export default class NodeDetailsContent extends React.Component {

  constructor(props) {
    super(props);
    this.state = {
      editedNode: props.node,
      codeCompletionEnabled: true,
      testResultsToHide: new Set()
    }
    this.state = {
      ...this.state,
      ...this.stateForSelectTestResults()
    }
    this.nodeObjectDetails = ProcessUtils.findNodeObjectTypeDefinition(this.props.node, this.props.processDefinitionData.processDefinition)
  }

  componentWillReceiveProps(nextProps) {
    if (!_.isEqual(this.props.node, nextProps.node)) {
      this.setState({editedNode: nextProps.node})
    }
  }

  componentDidUpdate(prevProps, prevState) {
    if (!_.isEqual(prevProps.node, this.props.node) || !_.isEqual(prevProps.testResults, this.props.testResults)) {
      this.selectTestResults()
    }
  }

  findParamByName(paramName) {
    return (_.get(this.nodeObjectDetails, 'parameters', [])).find((param) => param.name === paramName)
  }

  customNode = () => {
    switch (NodeUtils.nodeType(this.props.node)) {
      case 'Source':
        return this.sourceSinkCommon()
      case 'Sink':
        const toAppend = this.createField("textarea", "Expression", "endResult.expression", "expression")
        return this.sourceSinkCommon(toAppend)
      case 'SubprocessInputDefinition':
        //FIXME: currently there is no way to add new parameters or display them correctly
        return (
          <div className="node-table-body">
            {this.createField("input", "Id", "id")}

            {this.doCreateField("textarea", "Parameters", "parameters", JSON.stringify(this.state.editedNode.parameters || []), (newValue) => this.setNodeDataAt("parameters", JSON.parse(newValue)))}
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
            {this.createField("textarea", "Expression", "expression.expression", "expression")}
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
                <div className="node-block" key={index}>
                  {this.createListField("textarea", param.name, param, 'expression.expression', `service.parameters[${index}]`, param.name)}
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
            {this.state.editedNode.ref.parameters.map((params, index) => {
              return (
                <div className="node-block" key={index}>
                  {this.createListField("textarea", params.name, params, 'expression.expression', `ref.parameters[${index}]`, params.name)}
                </div>
              )
            })}
            {this.descriptionField()}
          </div>
        )

      case 'CustomNode':
        return (
          <div className="node-table-body">
            {this.createField("input", "Id", "id")}
            {_.has(this.state.editedNode, 'outputVar') ? this.createField("input", "Output", "outputVar") : null}
            {this.createReadonlyField("input", "Node type", "nodeType")}
            {this.state.editedNode.parameters.map((param, index) => {
              return (
                <div className="node-block" key={index}>
                  {this.createListField("textarea", param.name, param, 'expression.expression', `parameters[${index}]`, param.name)}
                </div>
              )
            })}
            {this.descriptionField()}
          </div>
        )
      case 'VariableBuilder':
        return (
          <div className="node-table-body">
            {this.createField("input", "Id", "id")}
            {this.createField("input", "Variable Name", "varName")}
            <div className="node-row">
              <div className="node-label">Fields:</div>
              <div className="node-group">
                {this.state.editedNode.fields.map((params, index) => {
                  return (
                    <div className="node-block" key={index}>
                      {this.createListField("input", "Name", params, "name", `fields[${index}]`)}
                      {this.createListField("textarea", "Expression", params, "expression.expression", `fields[${index}]`, "expression")}
                    </div>
                  )
                })}
              </div>
            </div>
            {this.descriptionField()}
          </div>
        )
      case 'Variable':
        return (
          <div className="node-table-body">
            {this.createField("input", "Id", "id")}
            {this.createField("input", "Variable Name", "varName")}
            {
              this.createField("textarea", "Expression", "value.expression", "expression")}
            {this.descriptionField()}
          </div>
        )
      case 'Switch':
        return (
          <div className="node-table-body">
            {this.createField("input", "Id", "id")}
            {this.createField("textarea", "Expression", "expression.expression", "expression")}
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
          this.createField("input", "Parallelism", "typeSpecificProperties.parallelism", "parallelism"),
          this.createField("input", "Checkpoint interval in seconds", "typeSpecificProperties.checkpointIntervalInSeconds", "checkpointIntervalInSeconds"),
          this.createField("checkbox", "Should split state to disk", "typeSpecificProperties.splitStateToDisk", "splitStateToDisk"),
          this.createField("checkbox", "Should use async interpretation (lazy variables not allowed)", "typeSpecificProperties.useAsyncInterpretation", "useAsyncInterpretation")

        ] : [this.createField("input", "Query path",  "typeSpecificProperties.path", "path")]
        const hasExceptionHandlerParams = this.state.editedNode.exceptionHandler.parameters.length > 0
        return (
          <div className="node-table-body">
            {_.concat(fields, commonFields)}
            { hasExceptionHandlerParams ?
              (<div className="node-row">
                <div className="node-label">Exception handler:</div>
                <div className="node-group">
                  {this.state.editedNode.exceptionHandler.parameters.map((params, index) => {
                    return (
                      <div className="node-block" key={index}>
                        {this.createListField("textarea", params.name, params, 'expression.expression', `exceptionHandler.parameters[${index}]`, params.name)}
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
  }

  subprocessVersionFields() {
    return [
      //TODO this should be nice looking selectbox
      this.doCreateField("textarea", "Subprocess Versions", "subprocessVersions", JSON.stringify(this.state.editedNode.subprocessVersions || {}), (newValue) => this.setNodeDataAt("subprocessVersions", JSON.parse(newValue)))
    ]
  }

  sourceSinkCommon(toAppend) {
    return (
      <div className="node-table-body">
        {this.createField("input", "Id", "id")}
        {this.createReadonlyField("input", "Ref Type", "ref.typ")}
        {this.state.editedNode.ref.parameters.map((params, index) => {
          return (
            <div className="node-block" key={index}>
              {this.createListField("textarea", params.name, params, 'expression.expression', `ref.parameters[${index}]`, params.name)}
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

  createField = (fieldType, fieldLabel, fieldProperty, fieldName, readonly) => {
    return this.doCreateField(fieldType, fieldLabel, fieldName, _.get(this.state.editedNode, fieldProperty, ""), ((newValue) => this.setNodeDataAt(fieldProperty, newValue) ), readonly)
  }

  createListField = (fieldType, fieldLabel, obj, fieldProperty, listFieldProperty, fieldName) => {
    return this.doCreateField(fieldType, fieldLabel, fieldName, _.get(obj, fieldProperty),
      ((newValue) => this.setNodeDataAt(`${listFieldProperty}.${fieldProperty}`, newValue) ))
  }

  wrapWithTestResult = (fieldName, field) => {
    var testValue = fieldName ? (this.state.testResultsToShow && this.state.testResultsToShow.expressionResults[fieldName]) : null
    const shouldHideTestResults = this.state.testResultsToHide.has(fieldName)
    const hiddenClassPart = (shouldHideTestResults ? " partly-hidden" : "")
    const showIconClass = shouldHideTestResults ? "glyphicon glyphicon-eye-close" : "glyphicon glyphicon-eye-open"
    if (testValue) {
      return (
        <div >
          {field}
            <div className="node-row node-test-results">
              <div className="node-label">{ModalRenderUtils.renderInfo('Value evaluated in test case')}
                {testValue.pretty ? <span className={showIconClass} onClick={this.toggleTestResult.bind(this, fieldName)}/> : null}
              </div>
              <div className={"node-value" + hiddenClassPart}>
                {testValue.original ? <Textarea className="node-input" readOnly={true} value={testValue.original}/> : null}
                <Textarea rows={1} cols={50} className="node-input" value={testValue.pretty || testValue} readOnly={true}/>
                {shouldHideTestResults ? <div className="fadeout"></div> : null}
              </div>
            </div>
        </div>
      )
    } else {
      return field
    }
  }

  toggleTestResult = (fieldName) => {
    const newTestResultsToHide = _.cloneDeep(this.state.testResultsToHide)
    newTestResultsToHide.has(fieldName) ? newTestResultsToHide.delete(fieldName) : newTestResultsToHide.add(fieldName)
    this.setState({testResultsToHide: newTestResultsToHide})
  }

  doCreateField = (fieldType, fieldLabel, fieldName, fieldValue, handleChange, forceReadonly) => {
    const readOnly = !this.props.isEditMode || forceReadonly
    const restriction = (this.findParamByName(fieldName) || {}).restriction

    if (restriction && restriction.type === 'StringValues') {
      const values = restriction.values
      return (
        <div className="node-row">
          {this.renderFieldLabel(fieldLabel)}
          <div className="node-value">
            <select className="node-input" value={fieldValue} onChange={(e) => handleChange(e.target.value)} readOnly={readOnly}>
              {values.map((value, index) => (<option key={index} value={`'${value}'`}>{value}</option>))}
            </select>
          </div>
        </div>
      )
    }

    switch (fieldType) {
      case 'input':
        return (
          <div className="node-row">
            {this.renderFieldLabel(fieldLabel)}
            <div className="node-value"><input type="text" className="node-input" value={fieldValue}
                                               onChange={(e) => handleChange(e.target.value)}
                                               readOnly={readOnly}/></div>
          </div>
        )
      case 'checkbox': {
        return (
          <div className="node-row">
            {this.renderFieldLabel(fieldLabel)}
            <div className="node-value"><input type="checkbox" checked={fieldValue}
                                               onChange={(e) => handleChange(fieldValue ? false : true)}
                                               disabled={readOnly ? 'disabled' : ''}/></div>
          </div>
        )
      }
      case 'plain-textarea':
        return (
          <div className="node-row">
            {this.renderFieldLabel(fieldLabel)}
            <div className="node-value">
              <Textarea rows={1} cols={50} className="node-input" value={fieldValue}
                        onChange={(e) => handleChange(e.target.value)} readOnly={readOnly}/>
            </div>
          </div>
        )
      case 'textarea':
        return this.wrapWithTestResult(fieldName, (
          <div className="node-row">
            {this.renderFieldLabel(fieldLabel)}
            <div className="node-value">
              <ExpressionSuggest inputProps={{
                rows: 1, cols: 50, className: "node-input", value: fieldValue,
                onValueChange: handleChange, readOnly: readOnly}}/>
            </div>
          </div>)
        )
      default:
        return (
          <div>
            Field type not known...
          </div>
        )
    }
  }


  setNodeDataAt = (propToMutate, newValue) => {
    var newtempNodeData = _.cloneDeep(this.state.editedNode)
    _.set(newtempNodeData, propToMutate, newValue)
    this.setState({editedNode: newtempNodeData})
    this.props.onChange(newtempNodeData)
  }

  descriptionField = () => {
    return this.createField("plain-textarea", "Description", "additionalFields.description")
  }

  hasTestResults = () => this.props.testResults && TestResultUtils.availableContexts(this.props.testResults).length > 0

  stateForSelectTestResults = (id) => {
    if (this.hasTestResults()) {
      const chosenId = id || _.get(_.head(TestResultUtils.availableContexts(this.props.testResults)), "id")
      return {
        testResultsToShow: TestResultUtils.nodeResultsForContext(this.props.testResults, chosenId),
        testResultsIdToShow: chosenId
      }
    } else {
      return null;
    }
  }

  selectTestResults = (id) => {
    const stateForSelect = this.stateForSelectTestResults(id)
    if (stateForSelect) {
      this.setState(stateForSelect)
    }
  }

  testResultsSelect = () => {
    if (this.hasTestResults()) {
      return (
        <div className="node-row">
          <div className="node-label">Test case:</div>
          <div className="node-value">
            <select className="node-input selectTestResults" onChange={(e) => this.selectTestResults(e.target.value)}
                    value={this.state.testResultsIdToShow}>
              { TestResultUtils.availableContexts(this.props.testResults).map((ctx, idx) =>
                //toString is lame in some cases
                (<option key={idx} value={ctx.id}>{ctx.id} ({ctx.display})</option>)
              )}
            </select>
          </div>
        </div>
      )
    } else {
      return null;
    }

  }

  testResults = () => {
    if (this.state.testResultsToShow && !_.isEmpty(this.state.testResultsToShow.context.variables)) {
      var ctx = this.state.testResultsToShow.context.variables
      return (

        <div className="node-table-body node-test-results">
        <div className="node-row">
          <div className="node-label">{ModalRenderUtils.renderInfo('Variables in test case')}</div>
        </div>
        {
          Object.keys(ctx).map((key, ikey) => {
            return (<div className="node-row" key={ikey}>
                <div className="node-label">{key}:</div>
                <div className="node-value">
                  {ctx[key].original ? <Textarea className="node-input" readOnly={true} value={ctx[key].original}/> : null}
                  <Textarea className="node-input" readOnly={true} value={ctx[key].pretty || ctx[key]}/>
                </div>
              </div>
            )
          })
        }
          {this.state.testResultsToShow && !_.isEmpty(this.state.testResultsToShow.mockedResultsForCurrentContext) ?
            (this.state.testResultsToShow.mockedResultsForCurrentContext).map((mockedValue, index) =>
              <span className="testResultDownload">
              <a download={this.props.node.id + "-single-input"} key={index} href={this.downloadableHref(mockedValue.value)}>
                <span className="glyphicon glyphicon-download"/> Results for this input</a></span>
            ) : null
          }
          {this.state.testResultsToShow && !_.isEmpty(this.state.testResultsToShow.mockedResultsForEveryContext) ?
            <span className="testResultDownload">
              <a download={this.props.node.id + "-all-inputs"}
               href={this.downloadableHref(this.mergedMockedResults(this.state.testResultsToShow.mockedResultsForEveryContext))}>
              <span className="glyphicon glyphicon-download"/> Results for all inputs</a></span>
            : null
          }
        </div>)
    } else {
      return null;
    }
  }

  renderFieldLabel = (label) => {
    const parameter = this.findParamByName(label)
    return (
      <div className="node-label" title={label}>{label}:
        {parameter ? <div className="labelFooter">{ProcessUtils.humanReadableType(parameter.typ.refClazzName)}</div> : null}
    </div>)
  }

  mergedMockedResults = (mockedResults) => {
    return _.join(mockedResults.map((mockedValue) => mockedValue.value), "\n\n")
  }

  downloadableHref = (content) => {
    return "data:application/octet-stream;charset=utf-8," + encodeURIComponent(content)
  }

  testErrors = () => {
    if (this.state.testResultsToShow && this.state.testResultsToShow.error) {
      var error = this.state.testResultsToShow.error

      return (
          <div className="node-table-body">
            <div className="node-row">
              <div className="node-label">{ ModalRenderUtils.renderWarning('Test case error')} </div>
              <div className="node-value">
                <div className="node-error">{`${error.message} (${error.class})`}</div>
              </div>
            </div>
          </div>
      );
    } else {
      return null;
    }
  }

  render() {
    var nodeClass = classNames('node-table', {'node-editable': this.props.isEditMode})
    return (
      <div className={nodeClass}>
        {ModalRenderUtils.renderErrors(this.props.nodeErrors, 'Node has errors')}
        {this.testResultsSelect()}
        {this.testErrors()}
        {this.customNode()}
        {this.testResults()}
      </div>
    )
  }
}

class NodeDetails extends React.Component {
  render() {
    return (
      <div>
        <pre>{JSON.stringify(this.props.node, null, 2)}</pre>
      </div>
    );
  }
}
