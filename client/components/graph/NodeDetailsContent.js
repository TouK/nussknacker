import React, {Component} from "react";
import {render} from "react-dom";
import classNames from "classnames";
import _ from "lodash";
import {ListGroupItem} from "react-bootstrap";
import Textarea from 'react-textarea-autosize';
import NodeUtils from "./NodeUtils";
import ExpressionSuggest from "./ExpressionSuggest";


export default class NodeDetailsContent extends React.Component {

  constructor(props) {
    super(props);
    this.state = {
      editedNode: props.node,
      codeCompletionEnabled: true
    }
  }

  componentDidUpdate(prevProps, prevState) {
    if (!_.isEqual(prevProps.node, this.props.node)) {
      this.setState({editedNode: this.props.node})
    }
  }

  customNode = () => {
    switch (NodeUtils.nodeType(this.props.node)) {
      case 'Source':
        return this.sourceSinkCommon()
      case 'Sink':
        const toAppend = this.createField("textarea", "Expression", "endResult.expression")
        return this.sourceSinkCommon(toAppend)
      case 'Filter':
        return (
          <div className="node-table-body">
            {this.createField("input", "Id", "id")}
            {this.createField("textarea", "Expression", "expression.expression")}
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
                  {this.createListField("textarea", params.name, params, 'expression.expression', `service.parameters[${index}]`)}
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
                  {this.createListField("textarea", params.name, params, 'expression.expression', `parameters[${index}]`)}
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
                      {this.createListField("textarea", "Expression", params, "expression.expression", `fields[${index}]`)}
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
            {this.createField("textarea", "Expression", "value.expression")}
            {this.descriptionField()}
          </div>
        )
      case 'Switch':
        return (
          <div className="node-table-body">
            {this.createField("input", "Id", "id")}
            {this.createField("textarea", "Expression", "expression.expression")}
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

  createField = (fieldType, fieldLabel, fieldProperty) => {
    return this.doCreateField(fieldType, fieldLabel, fieldProperty, _.get(this.state.editedNode, fieldProperty, ""), ((newValue) => this.setNodeDataAt(fieldProperty, newValue) ))
  }

  createListField = (fieldType, fieldLabel, obj, fieldProperty, listFieldProperty) => {
    return this.doCreateField(fieldType, fieldLabel, fieldProperty, _.get(obj, fieldProperty), ((newValue) => this.setNodeDataAt(`${listFieldProperty}.${fieldProperty}`, newValue) ))
  }

  doCreateField = (fieldType, fieldLabel, fieldProperty, fieldValue, handleChange) => {
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
      case 'textarea':
        return (
          <div className="node-row">
            <div className="node-label">{fieldLabel}:</div>
            <div className="node-value">
              {this.state.codeCompletionEnabled ?
                <ExpressionSuggest inputProps={{
                  rows: 1, cols: 50, className: "node-input", value: fieldValue,
                  onValueChange: handleChange, readOnly: !this.props.isEditMode
                }}/> :
                <Textarea rows="1" cols="50" className="node-input" value={fieldValue} onChange={(e) => handleChange(e.target.value)} readOnly={!this.props.isEditMode}/>
              }
              {process.env.NODE_ENV == "development" ?
                <div style={{color: "red"}}>
                  <p>ONLY_IN_DEV_MODE</p>
                  <p>{fieldValue}</p>
                </div> : null
              }
            </div>
          </div>
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
    return this.createField("textarea", "Description", "additionalFields.description")
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
              {this.props.nodeErrors.map(error =>
                (<li>{error.message + (error.fieldName ? ` (field: ${error.fieldName})` : '')}</li>)
              )}
            </ul>
          </ListGroupItem> : null}
        {!_.isEmpty(this.props.nodeErrors) ? <hr/> : null}
        {this.customNode()}
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
