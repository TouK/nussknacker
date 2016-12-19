import React, {Component} from "react";
import {render} from "react-dom";
import classNames from "classnames";
import _ from "lodash";
import {ListGroupItem} from "react-bootstrap";
import Textarea from "react-textarea-autosize";
import NodeUtils from "./NodeUtils";
import ExpressionSuggest from "./ExpressionSuggest";
import TestResultUtils from "../../common/TestResultUtils";

//zastanowic sie czy this.state tutaj nie powinien byc przepychany przez reduxa,
// bo obecnie ten stan moze byc przypadkowo resetowany kiedy parent component dostanie nowe propsy - bo tak mamy
// zaimplementowane `componentDidUpdate` zeby przy wyjsciu z node'a niezapisane zmiany sie cofaly
export default class NodeDetailsContent extends React.Component {

  constructor(props) {
    super(props);
    this.state = {
      editedNode: props.node,
      codeCompletionEnabled: true,
    }
    this.state = {
      ...this.state,
      ...this.selectTestResults()
    }
  }

  componentDidUpdate(prevProps, prevState) {
    if (!_.isEqual(prevProps.node, this.props.node)) {
      this.setState({editedNode: this.props.node})
    }
    if (!_.isEqual(prevProps.node, this.props.node) || !_.isEqual(prevProps.testResults, this.props.testResults)) {
      this.selectTestResults()
    }
  }

  customNode = () => {
    switch (NodeUtils.nodeType(this.props.node)) {
      case 'Source':
        return this.sourceSinkCommon()
      case 'Sink':
        const toAppend = this.createField("textarea", "Expression", "endResult.expression", "expression")
        return this.sourceSinkCommon(toAppend)
      case 'Filter':
        return (
          <div className="node-table-body">
            {this.createField("input", "Id", "id")}
            {this.createField("textarea", "Expression", "expression.expression", "expression")}
            {this.descriptionField()}
          </div>
        )
      case 'Enricher':
      case 'Processor':
        return (
          <div className="node-table-body">
            {this.createField("input", "Id", "id")}
            {this.createField("input", "Service Id", "service.id")}
            {this.state.editedNode.service.parameters.map((params, index) => {
              //TODO: czy tu i w custom node i gdzies jeszcze chcemy te hr??
              return (
                <div className="node-block" key={index}>
                  {this.createListField("textarea", params.name, params, 'expression.expression', `service.parameters[${index}]`, params.name)}
                  <hr/>
                </div>
              )
            })}
            {this.props.node.type == 'Enricher' ? this.createField("input", "Output", "output") : null }
            {this.descriptionField()}
          </div>
        )
      case 'CustomNode':
        return (
          <div className="node-table-body">
            {this.createField("input", "Id", "id")}
            {this.createField("input", "Output", "outputVar")}
            {this.createField("input", "Node type", "nodeType")}
            {this.state.editedNode.parameters.map((params, index) => {
              return (
                <div className="node-block" key={index}>
                  {this.createListField("textarea", params.name, params, 'expression.expression', `parameters[${index}]`, params.name)}
                  <hr />
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
                      <hr />
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
              //TODO: to sie dobrze nie wyswietli...
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
        return (
          <div className="node-table-body">
            {this.createField("input", "Parallelism", "parallelism")}
            <div className="node-row">
              <div className="node-label">Exception handler:</div>
              <div className="node-group">
                {this.state.editedNode.exceptionHandler.parameters.map((params, index) => {
                  return (
                    <div className="node-block" key={index}>
                      {this.createListField("input", params.name, params, "value", `exceptionHandler.parameters[${index}]`)}
                      <hr />
                    </div>
                  )
                })}
              </div>
            </div>
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

  sourceSinkCommon(toAppend) {
    return (
      <div className="node-table-body">
        {this.createField("input", "Id", "id")}
        {this.createField("input", "Ref Type", "ref.typ")}
        {this.state.editedNode.ref.parameters.map((params, index) => {
          return (
            <div className="node-block" key={index}>
              {this.createListField("input", params.name, params, "value", `ref.parameters[${index}]`)}
              <hr />
            </div>
          )
        })}
        {this.descriptionField()}
        {toAppend}
      </div>
    )
  }

  createField = (fieldType, fieldLabel, fieldProperty, fieldName) => {
    return this.doCreateField(fieldType, fieldLabel, fieldName, _.get(this.state.editedNode, fieldProperty, ""), ((newValue) => this.setNodeDataAt(fieldProperty, newValue) ))
  }

  createListField = (fieldType, fieldLabel, obj, fieldProperty, listFieldProperty, fieldName) => {
    return this.doCreateField(fieldType, fieldLabel, fieldName, _.get(obj, fieldProperty), ((newValue) => this.setNodeDataAt(`${listFieldProperty}.${fieldProperty}`, newValue) ))
  }

  wrapWithTestResult = (fieldName, field) => {
    var testValue = fieldName ? (this.state.testResultsToShow && this.state.testResultsToShow.expressionResults[fieldName]) : null

    if (testValue) {
      return (
        <div>
          {field}
          <div className="node-row">
            <div className="node-label">
              Evaluated:
            </div>
            <div className="node-value">
              <input type="text" readOnly="true" className="node-input"
                     value={testValue}/>
            </div>
          </div>
        </div>
      )
    } else {
      return field
    }
  }

  doCreateField = (fieldType, fieldLabel, fieldName, fieldValue, handleChange) => {


    switch (fieldType) {
      case 'input':
        return (
          <div className="node-row">
            <div className="node-label">{fieldLabel}:</div>
            <div className="node-value"><input type="text" className="node-input" value={fieldValue}
                                               onChange={(e) => handleChange(e.target.value)}
                                               readOnly={!this.props.isEditMode}/></div>
          </div>
        )
      case 'plain-textarea':
        return (
          <div className="node-row">
            <div className="node-label">{fieldLabel}:</div>
            <div className="node-value">
              <Textarea rows={1} cols={50} className="node-input" value={fieldValue}
                        onChange={(e) => handleChange(e.target.value)} readOnly={!this.props.isEditMode}/>
            </div>
          </div>
        )
      case 'textarea':
        return this.wrapWithTestResult(fieldName, (
          <div className="node-row">
            <div className="node-label">{fieldLabel}:</div>
            <div className="node-value">
              {this.state.codeCompletionEnabled ?
                <ExpressionSuggest inputProps={{
                  rows: 1, cols: 50, className: "node-input", value: fieldValue,
                  onValueChange: handleChange, readOnly: !this.props.isEditMode
                }}/> :
                <Textarea rows={1} cols={50} className="node-input" value={fieldValue}
                          onChange={(e) => handleChange(e.target.value)} readOnly={!this.props.isEditMode}/>
              }
              {process.env.NODE_ENV == "development" ?
                <div style={{color: "red"}}>
                  <p>ONLY_IN_DEV_MODE</p>
                  <p>{fieldValue}</p>
                </div> : null
              }
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


  stateForSelectTestResults = (id) => {
    if (this.props.testResults) {
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
    this.setState(this.stateForSelectTestResults(id))
  }

  testResultsSelect = () => {
    if (this.props.testResults) {
      return (
        <select className="node-input selectTestResults" onChange={(e) => this.selectTestResults(e.target.value)}
                value={this.state.testResultsIdToShow}>
          { TestResultUtils.availableContexts(this.props.testResults).map((ctx, idx) =>
            //ten toString jest b. slaby w wielu przypadkach - trzeba cos z nim zrobic :|
            (<option key={idx} value={ctx.id}>{ctx.id} ({(ctx.input || "").toString().substring(0, 50)})</option>)
          )}
        </select>
      )
    } else {
      return null;
    }

  }

  testResults = () => {
    if (this.state.testResultsToShow && this.state.testResultsToShow.context) {
      var ctx = this.state.testResultsToShow.context.variables
      return (<div className="node-table-body">
        <div className="node-row">
          <div className="node-label">Variables:</div>
        </div>
        {
          Object.keys(ctx).map((key, ikey) => {
            return (<div className="node-row" key={ikey}>
                <div className="node-label">{key}:</div>
                <div className="node-value"><input type="text" readOnly="true"  className="node-input" value={ctx[key]}/></div>
              </div>
            )
          })
        }
      </div>)
    } else {
      return null;
    }
  }

  render() {
    var nodeClass = classNames('node-table', {'node-editable': this.props.isEditMode})
    return (
      <div className={nodeClass}>
        <label className="code-completion">
          <input type="checkbox" disabled={!this.props.isEditMode} checked={this.state.codeCompletionEnabled}
                 onChange={(e) => {
                   this.setState({codeCompletionEnabled: !this.state.codeCompletionEnabled})
                 }}/> Code completion enabled
        </label>
        {!_.isEmpty(this.props.nodeErrors) ?
          //FIXME: ladniej... i moze bledy dotyczace pol kolo nich?
          <ListGroupItem bsStyle="danger">Node is invalid:
            <ul>
              {this.props.nodeErrors.map((error, index) =>
                (<li key={index}>{error.message + (error.fieldName ? ` (field: ${error.fieldName})` : '')}</li>)
              )}
            </ul>
          </ListGroupItem> : null}
        {!_.isEmpty(this.props.nodeErrors) ? <hr/> : null}
        {this.testResultsSelect()}
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
