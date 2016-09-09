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
        switch (this.props.node.type) {
            case 'Source':
              return (
                <div className="node-table-body">
                  <div className="node-row">
                    <div className="node-label">Ref:</div>
                    <div className="node-group">
                      {this.createField("input", "Type:", this.state.tempNodeData.ref.typ, ((event) => {
                        var newtempNodeData = _.cloneDeep(this.state.tempNodeData)
                        newtempNodeData.ref.typ = event.target.value
                        this.setState( { tempNodeData: newtempNodeData} ) })
                      )}
                      <div className="node-row">
                        <div className="node-label">Parameters:</div>
                        <div className="node-group child-group">
                          {this.state.tempNodeData.ref.parameters.map ((params, index) => {
                            return (
                              <div className="node-block" key={index}>
                                {this.createField("input", "Name:", params.name, ((event) => {
                                  var newtempNodeData = _.cloneDeep(this.state.tempNodeData)
                                  newtempNodeData.ref.parameters[index].name = event.target.value
                                  this.setState( { tempNodeData: newtempNodeData} ) })
                                )}
                                {this.createField("input", "Value:", params.value, ((event) => {
                                  var newtempNodeData = _.cloneDeep(this.state.tempNodeData)
                                  newtempNodeData.ref.parameters[index].value = event.target.value
                                  this.setState( { tempNodeData: newtempNodeData} ) })
                                )}
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
                    <div className="node-row">
                      <div className="node-label">Ref:</div>
                      <div className="node-group">
                        {this.createField("input", "Type:", this.state.tempNodeData.ref.typ, ((event) => {
                          var newtempNodeData = _.cloneDeep(this.state.tempNodeData)
                          newtempNodeData.ref.typ = event.target.value
                          this.setState( { tempNodeData: newtempNodeData} ) })
                        )}
                        <div className="node-row">
                          <div className="node-label">Parameters:</div>
                          <div className="node-group child-group">
                            {this.state.tempNodeData.ref.parameters.map((params, index) => {
                              return (
                                <div className="node-block" key={index}>
                                  {this.createField("input", "Name:", params.name, ((event) => {
                                    var newtempNodeData = _.cloneDeep(this.state.tempNodeData)
                                    newtempNodeData.ref.parameters[index].name = event.target.value
                                    this.setState( { tempNodeData: newtempNodeData} ) })
                                  )}
                                  {this.createField("input", "Value:", params.value, ((event) => {
                                    var newtempNodeData = _.cloneDeep(this.state.tempNodeData)
                                    newtempNodeData.ref.parameters[index].value = event.target.value
                                    this.setState( { tempNodeData: newtempNodeData} ) })
                                  )}
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
                  {this.createField("textarea", "Expression:",this.state.tempNodeData.expression.expression, ((event) => {
                    var newtempNodeData = _.cloneDeep(this.state.tempNodeData)
                    newtempNodeData.expression.expression.value = event.target.value
                    this.setState( { tempNodeData: newtempNodeData} ) })
                  )}
                </div>
              )
              case 'Enricher':
              case 'Processor':
                return (
                  <div className="node-table-body">
                    <div className="node-row">
                      <div className="node-label">Service:</div>
                      <div className="node-group">
                        {this.createField("input", "Service Id:", this.state.tempNodeData.service.id, ((event) => {
                          var newtempNodeData = _.cloneDeep(this.state.tempNodeData)
                          newtempNodeData.service.id = event.target.value
                          this.setState( { tempNodeData: newtempNodeData} ) })
                        )}
                        <div className="node-row">
                          <div className="node-label">Parameters:</div>
                          <div className="node-group child-group">
                            {this.state.tempNodeData.service.parameters.map ((params, index) => {
                              return (
                                <div className="node-block" key={index}>
                                  {this.createField("input", "Name:", params.name, ((event) => {
                                    var newtempNodeData = _.cloneDeep(this.state.tempNodeData)
                                    newtempNodeData.service.parameters[index].name = event.target.value
                                    this.setState( { tempNodeData: newtempNodeData} ) })
                                  )}
                                  {this.createField("input", "Expression:", params.expression.expression, ((event) => {
                                    var newtempNodeData = _.cloneDeep(this.state.tempNodeData)
                                    newtempNodeData.service.parameters[index].expression.expression = event.target.value
                                    this.setState( { tempNodeData: newtempNodeData} ) })
                                  )}
                                </div>
                              )
                            })}
                          </div>
                        </div>
                      </div>
                    </div>
                    {this.props.node.type == 'Enricher' ? this.createField("input", "Output:", this.state.tempNodeData.output, ((event) => {
                      var newtempNodeData = _.cloneDeep(this.state.tempNodeData)
                      newtempNodeData.output = event.target.value
                      this.setState( { tempNodeData: newtempNodeData} ) }))
                    : null }
                  </div>
                )
            case 'VariableBuilder':
                return (
                  <div className="node-table-body">
                    {this.createField("input", "Variable Name:", this.state.tempNodeData.varName, ((event) => {
                      var newtempNodeData = _.cloneDeep(this.state.tempNodeData)
                      newtempNodeData.varName = event.target.value
                      this.setState( { tempNodeData: newtempNodeData} ) })
                    )}
                    <div className="node-row">
                      <div className="node-label">Fields:</div>
                      <div className="node-group child-group">
                        {this.state.tempNodeData.fields.map ((params, index) => {
                          return (
                            <div className="node-block" key={index}>
                              {this.createField("input", "Name:", params.name, ((event) => {
                                var newtempNodeData = _.cloneDeep(this.state.tempNodeData)
                                newtempNodeData.fields[index].name = event.target.value
                                this.setState( { tempNodeData: newtempNodeData} ) })
                              )}
                              {this.createField("input", "Expression:", params.expression.expression, ((event) => {
                                var newtempNodeData = _.cloneDeep(this.state.tempNodeData)
                                newtempNodeData.fields[index].expression.expression = event.target.value
                                this.setState( { tempNodeData: newtempNodeData} ) })
                              )}
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
                  {this.createField("input", "Expression:", this.state.tempNodeData.expression.expression, ((event) => {
                    var newtempNodeData = _.cloneDeep(this.state.tempNodeData)
                    newtempNodeData.expression.expression = event.target.value
                    this.setState( { tempNodeData: newtempNodeData} ) })
                  )}
                  {this.createField("input", "exprVal:", this.props.node.exprVal, ((event) => {
                    var newtempNodeData = _.cloneDeep(this.state.tempNodeData)
                    newtempNodeData.exprVal = event.target.value
                    this.setState( { tempNodeData: newtempNodeData} ) })
                  )}
                </div>
              )
            case 'Aggregate':
              return (
                <div className="node-table-body">
                  {this.createField("textarea", "Key Expression:", this.state.tempNodeData.keyExpression.expression, ((event) => {
                    var newtempNodeData = _.cloneDeep(this.state.tempNodeData)
                    newtempNodeData.keyExpression.expression = event.target.value
                    this.setState( { tempNodeData: newtempNodeData} ) })
                  )}
                  {this.createField("textarea", "Trigger Expression:", this.state.tempNodeData.triggerExpression.expression, ((event) => {
                    var newtempNodeData = _.cloneDeep(this.state.tempNodeData)
                    newtempNodeData.triggerExpression.expression = event.target.value
                    this.setState( { tempNodeData: newtempNodeData} ) })
                  )}
                  {this.createField("input", "Folding function", this.state.tempNodeData.foldingFunRef, ((event) => {
                    var newtempNodeData = _.cloneDeep(this.state.tempNodeData)
                    newtempNodeData.foldingFunRef = event.target.value
                    this.setState( { tempNodeData: newtempNodeData} ) })
                  )}
                  {this.createField("input", "Duration (ms):", this.state.tempNodeData.durationInMillis, ((event) => {
                    var newtempNodeData = _.cloneDeep(this.state.tempNodeData)
                    newtempNodeData.durationInMillis = event.target.value
                    this.setState( { tempNodeData: newtempNodeData} ) })
                  )}
                  {this.createField("input", "Slide (ms):", this.state.tempNodeData.slideInMillis, ((event) => {
                    var newtempNodeData = _.cloneDeep(this.state.tempNodeData)
                    newtempNodeData.slideInMillis = event.target.value
                    this.setState( { tempNodeData: newtempNodeData} ) })
                  )}
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

    contentForNode = () => {
        if (!_.isEmpty(this.props.node)) {
          var nodeClass = classNames(
            'node-table',
            {
            'node-editable': !this.state.readOnly
          })
          return (
            <div className={nodeClass}>
              <div className="node-table-body">
                {this.createField("input",  "Id:", this.state.tempNodeData.id, ((event) => {
                  var newtempNodeData = _.cloneDeep(this.state.tempNodeData)
                  newtempNodeData.id = event.target.value
                  this.setState( { tempNodeData: newtempNodeData} ) })
                )}
              </div>
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
      return nodeAttributes[this.props.node.type];
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
                        <span>{this.props.node.type}</span>
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
