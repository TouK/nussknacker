import React, { Component } from 'react';
import { render } from 'react-dom';
import classNames from 'classnames';
import Modal from 'react-modal';
import _ from 'lodash';

export default class NodeDetailsModal extends React.Component {

    constructor(props) {
      super(props);
      this.state = {
          readOnly: true,
          tempNodeData: ''
      };
    }

    componentWillReceiveProps(nextProps) {
      this.setState({tempNodeData: nextProps.node})
      this.forceUpdate()
    }

    closeModal = () => {
        this.setState({readOnly: true})
        this.props.onClose()
    }

    createField = (fieldType, fieldLabel, fieldValue, handleChange) => {
      switch (fieldType) {
      case 'input':
        return (
          <div className="node-row">
            <div className="node-label">{fieldLabel}</div>
            <div className="node-value"><input type="text" className="node-input" value={fieldValue} onChange={handleChange} readOnly={this.state.readOnly}/></div>
          </div>
        )
      case 'textarea':
        return (
          <div className="node-row">
            <div className="node-label">{fieldLabel}</div>
            <div className="node-value"><textarea rows="5" cols="50" className="node-input" value={fieldValue} onChange={handleChange}  readOnly={this.state.readOnly}/></div>
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

    customNode = () => {
        switch (this.nodeType()) {
            case 'Source':
              return (
                <div className="node-table-body">
                  {this.createField("input",  "Id:", this.state.tempNodeData.id, ((event) => this.setNodeDataAt('id', event.target.value) ))}
                  <div className="node-row">
                    <div className="node-label">Ref:</div>
                    <div className="node-group">
                      {this.createField("input", "Type:", this.state.tempNodeData.ref.typ, ((event) => this.setNodeDataAt('ref.typ', event.target.value) ))}
                      <div className="node-row">
                        <div className="node-label">Parameters:</div>
                        <div className="node-group child-group">
                          {this.state.tempNodeData.ref.parameters.map ((params, index) => {
                            return (
                              <div className="node-block" key={index}>
                                {this.createField("input", "Name:", params.name, ((event) => this.setNodeDataAt(`ref.parameters[${index}].name`, event.target.value)))}
                                {this.createField("input", "Value:", params.value, ((event) => this.setNodeDataAt(`ref.parameters[${index}].value`, event.target.value)))}
                              </div>
                            )
                          })}
                        </div>
                      </div>
                    </div>
                  </div>
                </div>
              )
            case 'Sink':
                return (
                  <div className="node-table-body">
                    {this.createField("input",  "Id:", this.state.tempNodeData.id, ((event) => this.setNodeDataAt('id', event.target.value) ))}
                    <div className="node-row">
                      <div className="node-label">Ref:</div>
                      <div className="node-group">
                        {this.createField("input", "Type:", this.state.tempNodeData.ref.typ, ((event) => this.setNodeDataAt('ref.typ', event.target.value) ))}
                        <div className="node-row">
                          <div className="node-label">Parameters:</div>
                          <div className="node-group child-group">
                            {this.state.tempNodeData.ref.parameters.map((params, index) => {
                              return (
                                <div className="node-block" key={index}>
                                  {this.createField("input", "Name:", params.name, ((event) => this.setNodeDataAt(`ref.parameters[${index}].name`, event.target.value) ))}
                                  {this.createField("input", "Value:", params.value, ((event) => this.setNodeDataAt(`ref.parameters[${index}].value`, event.target.value) ))}
                                </div>
                              )
                            })}
                          </div>
                        </div>
                      </div>
                    </div>
                  </div>
                )
            case 'Filter':
              return (
                <div className="node-table-body">
                  {this.createField("input",  "Id:", this.state.tempNodeData.id, ((event) => this.setNodeDataAt('id', event.target.value) ))}
                  {this.createField("textarea", "Expression:", this.state.tempNodeData.expression.expression, ((event) => this.setNodeDataAt('expression.expression', event.target.value) ))}
                </div>
              )
              case 'Enricher':
              case 'Processor':
                return (
                  <div className="node-table-body">
                    {this.createField("input",  "Id:", this.state.tempNodeData.id, ((event) => this.setNodeDataAt('id', event.target.value) ))}
                    <div className="node-row">
                      <div className="node-label">Service:</div>
                      <div className="node-group">
                        {this.createField("input", "Service Id:", this.state.tempNodeData.service.id, ((event) => this.setNodeDataAt('service.id', event.target.value) ))}
                        <div className="node-row">
                          <div className="node-label">Parameters:</div>
                          <div className="node-group child-group">
                            {this.state.tempNodeData.service.parameters.map ((params, index) => {
                              return (
                                <div className="node-block" key={index}>
                                  {this.createField("input", "Name:", params.name, ((event) => this.setNodeDataAt(`service.parameters[${index}].name`, event.target.value) ))}
                                  {this.createField("input", "Expression:", params.expression.expression, ((event) => this.setNodeDataAt(`service.parameters[${index}].expression.expression`, event.target.value) ))}
                                </div>
                              )
                            })}
                          </div>
                        </div>
                      </div>
                    </div>
                    {this.props.node.type == 'Enricher' ? this.createField("input", "Output:", this.state.tempNodeData.output, ((event) => this.setNodeDataAt('output', event.target.value) )) : null }
                  </div>
                )
            case 'VariableBuilder':
                return (
                  <div className="node-table-body">
                    {this.createField("input",  "Id:", this.state.tempNodeData.id, ((event) => this.setNodeDataAt('id', event.target.value) ))}
                    {this.createField("input", "Variable Name:", this.state.tempNodeData.varName, ((event) => this.setNodeDataAt('varName', event.target.value) ))}
                    <div className="node-row">
                      <div className="node-label">Fields:</div>
                      <div className="node-group child-group">
                        {this.state.tempNodeData.fields.map ((params, index) => {
                          return (
                            <div className="node-block" key={index}>
                              {this.createField("input", "Name:", params.name, ((event) => this.setNodeDataAt(`fields[${index}].name`, event.target.value) ))}
                              {this.createField("input", "Expression:", params.expression.expression, ((event) => this.setNodeDataAt(`fields[${index}].expression.expression`, event.target.value) ))}
                            </div>
                          )
                        })}
                      </div>
                    </div>
                  </div>
                )
            case 'Switch':
              return (
                <div className="node-table-body">
                  {this.createField("input",  "Id:", this.state.tempNodeData.id, ((event) => this.setNodeDataAt('id', event.target.value) ))}
                  {this.createField("input", "Expression:", this.state.tempNodeData.expression.expression, ((event) => this.setNodeDataAt('expression.expression', event.target.value) ))}
                  {this.createField("input", "exprVal:", this.props.node.exprVal, ((event) => this.setNodeDataAt('exprVal', event.target.value) ))}
                </div>
              )
            case 'Aggregate':
              return (
                <div className="node-table-body">
                  {this.createField("input",  "Id:", this.state.tempNodeData.id, ((event) => this.setNodeDataAt('id', event.target.value) ))}
                  {this.createField("textarea", "Key Expression:", this.state.tempNodeData.keyExpression.expression, ((event) => this.setNodeDataAt('keyExpression.expression', event.target.value) ))}
                  {this.createField("textarea", "Trigger Expression:", this.state.tempNodeData.triggerExpression.expression, ((event) => this.setNodeDataAt('triggerExpression.expression', event.target.value) ))}
                  {this.createField("input", "Folding function", this.state.tempNodeData.foldingFunRef, ((event) => this.setNodeDataAt('foldingFunRef', event.target.value) ))}
                  {this.createField("input", "Duration (ms):", this.state.tempNodeData.durationInMillis, ((event) => this.setNodeDataAt('durationInMillis', event.target.value) ))}
                  {this.createField("input", "Slide (ms):", this.state.tempNodeData.slideInMillis, ((event) => this.setNodeDataAt('slideInMillis', event.target.value) ))}
                </div>
              )
            case 'Properties':
              return (
                <div className="node-table-body">
                  {this.createField("input", "Variable Name:", this.state.tempNodeData.parallelism, ((event) => this.setNodeDataAt('parallelism', event.target.value) ))}
                  <div className="node-row">
                    <div className="node-label">Exception handler:</div>
                    <div className="node-group child-group">
                      {this.state.tempNodeData.exceptionHandler.parameters.map((params, index) => {
                        return (
                            <div className="node-block" key={index}>
                              {this.createField("input", "Name:", params.name, ((event) => this.setNodeDataAt(`exceptionHandler.parameters[${index}].name`, event.target.value) ))}
                              {this.createField("input", "Value:", params.value, ((event) => this.setNodeDataAt(`exceptionHandler.parameters[${index}].value`, event.target.value) ))}
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

    setNodeDataAt = (propToMutate, newValue) => {
      var newtempNodeData = _.cloneDeep(this.state.tempNodeData)
      _.set(newtempNodeData, propToMutate, newValue)
      this.setState( { tempNodeData: newtempNodeData} )
    }

    contentForNode = () => {
        if (!_.isEmpty(this.props.node)) {
          var nodeClass = classNames(
            'node-table',
            {
            'node-editable': !this.state.readOnly
          })
          return (
            <div className={nodeClass}>
              {this.customNode()}
            </div>
          )
        }
    }

    toggleReadOnly = () => {
      this.setState({readOnly: !this.state.readOnly});
    }

    editNodeData = () => {
      this.toggleReadOnly();
    }

    updateNodeData = () => {
      // FIXME - dopisac funckje zapisu roboczego grafu
      this.toggleReadOnly();
    }

    nodeAttributes = () => {
      var nodeAttributes = require('json!../../assets/json/nodeAttributes.json');
      return nodeAttributes[this.nodeType()];
    }

    nodeType = () => {
      return this.props.node.type ? this.props.node.type : "Properties";
    }

    render () {
        var isOpen = !(_.isEmpty(this.props.node))
        var modalStyles = {
          overlay: {
            backgroundColor: 'rgba(63, 62, 61, 0.3)'
          },
          content : {
            borderRadius: '0',
            padding: '0',
            left: '20%',
            right: '20%',
            top: '15%',
            bottom: '15%',
            border: 'none'
          }
        };

        var buttonClasses = classNames(
          'modalButton'
        )
        var editButtonClasses = classNames(
          buttonClasses,
          'pull-left',
          {
          'hidden': !this.state.readOnly
        })
        var saveButtonClasses = classNames(
          buttonClasses,
          'pull-left',
          {
          'hidden': this.state.readOnly
        })

        if (!_.isEmpty(this.props.node)) {
          var headerStyles = {
            backgroundColor: this.nodeAttributes().styles.fill,
            color: this.nodeAttributes().styles.color
          };
        };

        return (
            <div className="objectModal">
                <Modal isOpen={isOpen} style={modalStyles} onRequestClose={this.closeModal}>
                    <div id="modalHeader" style={headerStyles}>
                        <span>{this.nodeType()}</span>
                    </div>
                    <div id="modalContent">
                        {this.contentForNode()}
                    </div>
                    <div id="modalFooter">
                      <div>
                        <button type="button" title="Save node details" className={saveButtonClasses} onClick={this.updateNodeData}>Save</button>
                        <button type="button" title="Edit node details" className={editButtonClasses} onClick={this.editNodeData}>Edit</button>
                        <button type="button" title="Close node details" className={buttonClasses} onClick={this.closeModal}>Close</button>
                      </div>
                    </div>
                </Modal>
            </div>
        );
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
