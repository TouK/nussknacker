import React, {Component} from 'react';
import {render} from 'react-dom';
import classNames from 'classnames';
import _ from 'lodash';
import {ListGroupItem} from 'react-bootstrap';
import NodeUtils from './NodeUtils'
import ExpressionSuggest from './ExpressionSuggest'

export default class NodeDetailsContent extends React.Component {

  constructor(props) {
    super(props);
    this.state = {
      editedNode: props.node,
      codeCompletionEnabled: true
    }
  }

  componentDidUpdate(prevProps, prevState){
    if (!_.isEqual(prevProps.node, this.props.node)) {
      this.setState({editedNode: this.props.node})
    }
  }

  customNode = () => {
    switch (NodeUtils.nodeType(this.props.node)) {
      case 'Source':
        return this.sourceSinkCommon()
      case 'Sink':
        const toAppend = this.createField("textarea", "Expression:", "endResult.expression")
        return this.sourceSinkCommon(toAppend)
      case 'Filter':
        return (
          <div className="node-table-body">
            {this.createField("input", "Id:", "id")}
            {this.createField("textarea", "Expression:", "expression.expression")}
          </div>
        )
      case 'Enricher':
      case 'Processor':
        return (
          <div className="node-table-body">
            {this.createField("input", "Id:", "id")}
            {this.createField("input", "Service Id:", "service.id")}
            <div className="node-row">
              <div className="node-label">Parameters:</div>
              <div className="node-group">
                {this.state.editedNode.service.parameters.map((params, index) => {
                  return (
                    <div className="node-block" key={index}>
                      {this.createListField("input", "Name:", params, 'name', `service.parameters[${index}]`)}
                      {this.createListField("textarea", "Expression:", params, 'expression.expression', `service.parameters[${index}]`)}
                      <hr />
                    </div>
                  )
                })}
              </div>
            </div>
            {this.props.node.type == 'Enricher' ? this.createField("input", "Output:", "output") : null }
          </div>
        )
      case 'CustomNode':
        return (
          <div className="node-table-body">
            {this.createField("input", "Id:", "id")}
            {this.createField("input", "Output:", "outputVar")}
            {this.createField("input", "Node type:", "nodeType")}
            <div className="node-row">
              <div className="node-label">Parameters:</div>
              <div className="node-group">
                {this.state.editedNode.parameters.map((params, index) => {
                  return (
                    <div className="node-block" key={index}>
                      {this.createListField("input", "Name:", params, "name", `parameters[${index}]`)}
                      {this.createListField("textarea", "Expression:", params, "expression.expression", `parameters[${index}]`)}
                      <hr />
                    </div>
                  )
                })}
              </div>
            </div>

          </div>
        )
      case 'VariableBuilder':
        return (
          <div className="node-table-body">
            {this.createField("input", "Id:", "id")}
            {this.createField("input", "Variable Name:", "varName")}
            <div className="node-row">
              <div className="node-label">Fields:</div>
              <div className="node-group">
                {this.state.editedNode.fields.map((params, index) => {
                  return (
                    <div className="node-block" key={index}>
                      {this.createListField("input", "Name:", params, "name", `fields[${index}]`)}
                      {this.createListField("textarea", "Expression:", params, "expression.expression", `fields[${index}]`)}
                      <hr />
                    </div>
                  )
                })}
              </div>
            </div>
          </div>
        )
      case 'Variable':
        return (
          <div className="node-table-body">
            {this.createField("input", "Id:", "id")}
            {this.createField("input", "Variable Name:", "varName")}
            {this.createField("textarea", "Expression:", "value.expression")}
          </div>
        )
      case 'Switch':
        return (
          <div className="node-table-body">
            {this.createField("input", "Id:", "id")}
            {this.createField("textarea", "Expression:", "expression.expression")}
            {this.createField("input", "exprVal:", "exprVal")}
          </div>
        )
      case 'Aggregate':
        return (
          <div className="node-table-body">
            {this.createField("input", "Id:", "id")}
            {this.createField("textarea", "Key Expression:", "keyExpression.expression")}
            {this.createField("textarea", "Trigger Expression:", "triggerExpression.expression")}
            {this.createField("input", "Folding function", "foldingFunRef")}
            {this.createField("input", "Duration (ms):", "durationInMillis")}
            {this.createField("input", "Slide (ms):", "slideInMillis")}
          </div>
        )
      case 'Split':
        return (
          <div className="node-table-body">
            {this.createField("input", "Id:", "id")}
          </div>
        )
      case 'Properties':
        return (
          <div className="node-table-body">
            {this.createField("input", "Parallelism:", "parallelism")}
            <div className="node-row">
              <div className="node-label">Exception handler:</div>
              <div className="node-group">
                {this.state.editedNode.exceptionHandler.parameters.map((params, index) => {
                  return (
                    <div className="node-block" key={index}>
                      {this.createListField("input", "Name:", params, "name", `exceptionHandler.parameters[${index}]`)}
                      {this.createListField("input", "Value:", params, "value", `exceptionHandler.parameters[${index}]`)}
                      <hr />
                    </div>
                  )
                })}
              </div>
            </div>
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
        {this.createField("input", "Id:", "id")}
        {this.createField("input", "Ref Type:", "ref.typ")}
        <div className="node-row">
          <div className="node-label">Parameters:</div>
          <div className="node-group">
            {this.state.editedNode.ref.parameters.map((params, index) => {
              return (
                <div className="node-block" key={index}>
                  {this.createListField("input", "Name:", params, "name", `ref.parameters[${index}]`)}
                  {this.createListField("input", "Value:", params, "value", `ref.parameters[${index}]`)}
                  <hr />
                </div>
              )
            })}
          </div>
        </div>
        {toAppend}
      </div>
    )
  }

  createField = (fieldType, fieldLabel, fieldProperty) => {
    return this.doCreateField(fieldType, fieldLabel, _.get(this.state.editedNode, fieldProperty), ((newValue) => this.setNodeDataAt(fieldProperty, newValue) ))
  }

  createListField = (fieldType, fieldLabel, obj, fieldProperty, listFieldProperty) => {
    return this.doCreateField(fieldType, fieldLabel, _.get(obj, fieldProperty), ((newValue) => this.setNodeDataAt(`${listFieldProperty}.${fieldProperty}`, newValue) ))
  }

  doCreateField = (fieldType, fieldLabel, fieldValue, handleChange) => {
    switch (fieldType) {
      case 'input':
        return (
          <div className="node-row">
            <div className="node-label">{fieldLabel}</div>
            <div className="node-value"><input type="text" className="node-input" value={fieldValue}
                                               onChange={(e) => handleChange(e.target.value)} readOnly={!this.props.isEditMode}/></div>
          </div>
        )
      case 'textarea':
        return (
          <div className="node-row">
            <div className="node-label">{fieldLabel}</div>
            <div className="node-value">
              {this.state.codeCompletionEnabled ?
                <ExpressionSuggest inputProps={{
                  rows: 5, cols: 50, className: "node-input", value: fieldValue,
                  onValueChange: handleChange, readOnly: !this.props.isEditMode
                }}/> :
                <textarea rows="5" cols="50" className="node-input" value={fieldValue}
                          onChange={(e) => handleChange(e.target.value)} readOnly={!this.props.isEditMode}/>
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


  render() {
    var nodeClass = classNames('node-table', {'node-editable': this.props.isEditMode})
    return (
      <div className={nodeClass}>
        <label className="code-completion">
          <input type="checkbox" disabled={!this.props.isEditMode} checked={this.state.codeCompletionEnabled} onChange={(e) => {
            this.setState({codeCompletionEnabled: !this.state.codeCompletionEnabled})
          }}/> Code completion enabled
        </label>
        {this.customNode()}
        {!_.isEmpty(this.props.nodeErrors) ?
          <ListGroupItem bsStyle="danger">Node is invalid</ListGroupItem> : null}
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
