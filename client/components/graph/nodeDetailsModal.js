import React, { Component } from 'react';
import { render } from 'react-dom';
import classNames from 'classnames';
import Modal from 'react-modal';

export default class NodeDetailsModal extends Component {
    closeModal = () => {
        this.props.onClose()
    }

    createField = (fieldType, fieldLabel, fieldValue) => {
      switch (fieldType) {
      case 'input':
        return (
          <div className="node-row">
            <div className="node-label">{fieldLabel}</div>
            <div className="node-value"><input type="text" value={fieldValue} className="node-input" readOnly/></div>
          </div>
        )
      case 'textarea':
        return (
          <div className="node-row">
            <div className="node-label">{fieldLabel}</div>
            <div className="node-value"><textarea rows="5" cols="50" value={fieldValue} className="node-input" readOnly/></div>
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
                      {this.createField("input", "Type:", this.props.node.ref.typ)}
                      <div className="node-row">
                        <div className="node-label">Parameters:</div>
                        <div className="node-group child-group">
                          {this.props.node.ref.parameters.map ((params, index) => {
                            return (
                              <div className="node-block" key={index}>
                                <div className="node-label">Name:</div>
                                <div className="node-value"><input type="text" value={params.name} className="node-input" readOnly/></div>
                                <div className="node-label">Value:</div>
                                <div className="node-value"><input type="text" value={params.value} className="node-input" readOnly/></div>
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
                        {this.createField("input", "Type:", this.props.node.ref.typ)}
                        <div className="node-row">
                          <div className="node-label">Parameters:</div>
                          <div className="node-group child-group">
                            {this.props.node.ref.parameters.map(function(params, index){
                              return (
                                <div className="node-block" key={index}>
                                  <div className="node-label">Name:</div>
                                  <div className="node-value"><input type="text" value={params.name} className="node-input" readOnly/></div>
                                  <div className="node-label">Value:</div>
                                  <div className="node-value"><input type="text" value={params.value} className="node-input" readOnly/></div>
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
                  {this.createField("textarea", "Expression:", this.props.node.expression.expression)}
                </div>
              )
              case 'Enricher':
              case 'Processor':
                return (
                  <div className="node-table-body">
                    <div className="node-row">
                      <div className="node-label">Service:</div>
                      <div className="node-group">
                        {this.createField("input", "Service Id:", this.props.node.service.id)}
                        <div className="node-row">
                          <div className="node-label">Parameters:</div>
                          <div className="node-group child-group">
                            {this.props.node.service.parameters.map ((params, index) => {
                              return (
                                <div className="node-block" key={index}>
                                  <div className="node-label">Name:</div>
                                  <div className="node-value"><input type="text" value={params.name} className="node-input" readOnly/></div>

                                  <div className="node-label">Expression:</div>
                                  <div className="node-value"><input type="text" value={params.expression.expression} className="node-input" readOnly/></div>
                                </div>
                              )
                            })}
                          </div>
                        </div>
                      </div>
                    </div>
                    {this.props.node.type == 'Enricher' ? this.createField("input", "Output:", this.props.node.output) : null}
                  </div>
                )
            case 'VariableBuilder':
                return (
                  <div className="node-table-body">
                    {this.createField("input", "Variable Name:", this.props.node.varName)}
                    <div className="node-row">
                      <div className="node-label">Fields:</div>
                      <div className="node-group child-group">
                        {this.props.node.fields.map ((params, index) => {
                          return (
                            <div className="node-block" key={index}>
                              <div className="node-label">Name:</div>
                              <div className="node-value"><input type="text" value={params.name} className="node-input" readOnly/></div>

                              <div className="node-label">Expression:</div>
                              <div className="node-value"><input type="text" value={params.expression.expression} className="node-input" readOnly/></div>
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
                  {this.createField("input", "Expression:", this.props.node.expression.expression)}
                  {this.createField("input", "exprVal:", this.props.node.exprVal)}
                </div>
              )
            case 'Aggregate':
              return (
                <div className="node-table-body">
                  {this.createField("textarea", "Key Expression:", this.props.node.keyExpression.expression)}
                  {this.createField("textarea", "Trigger Expression:", this.props.node.triggerExpression.expression)}
                  {this.createField("input", "Folding function", this.props.node.foldingFunRef)}
                  {this.createField("input", "Duration (ms):", this.props.node.durationInMillis)}
                  {this.createField("input", "Slide (ms):", this.props.node.slideInMillis)}
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
          return (
            <div className="node-table">
              <div className="node-table-body">
                {this.createField("input", "Type:", this.props.node.type)}
                {this.createField("input",  "Id:", this.props.node.id)}
              </div>
              {this.customNode()}
            </div>
          )
        }
    }

    nodeAttributes = () => {
      var nodeAttributes = require('json!../../assets/json/nodeAttributes.json');
      return nodeAttributes[this.props.node.type];
    };

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
        );

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
                        <button type="button" title="Not yet available" className={buttonClasses} onClick={null} disabled>Edit</button>
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
