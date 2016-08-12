import React, { Component } from 'react';
import { render } from 'react-dom';
import classNames from 'classnames';

import Modal from 'react-modal';
import { Table, Thead, Th, Tr, Td } from 'reactable';

export default class NodeDetailsModal extends Component {
    closeModal = () => {
        this.props.onClose()
    }

    customNode = () => {
        switch (this.props.node.type) {
            case 'Source':
                return (
                  <tbody>
                    <tr>
                      <td className="node-label">Ref:</td>
                      <td>
                        <div className="inner-table">
                          <div className="inner-label"><span>Typ:</span></div>
                          <div className="inner-value">
                            <input type="text" value={this.props.node.ref.typ} className="node-input" />
                          </div>
                          <br />
                          <div className="inner-label"><span>Parameters:</span></div>

                          {this.props.node.ref.parameters.map(function(params, index){
                            return (
                              <pre className="inner-value" key={index}>
                                <span>Name: </span>
                                <input type="text" value={params.name} className="node-input" />
                                <br />
                                <span>Value: </span>
                                <input type="text" value={params.value} className="node-input" />
                              </pre>
                            )
                          })}
                        </div>
                      </td>
                    </tr>
                  </tbody>
                )
            case 'Sink':
                return (
                  <tbody>
                    <tr>
                      <td className="node-label">Ref:</td>
                      <td>
                        <pre>
                          <span>Typ:</span>{this.props.node.ref.typ}<br />
                          <span>Parameters:</span>
                          {this.props.node.ref.parameters.map(function(params, index){
                            return (
                              <pre key={index}>
                                <span>Name: </span> {params.name}
                                <br />
                                <span>Value: </span>{params["value"]}
                              </pre>
                            )
                          })}
                        </pre>
                      </td>
                    </tr>
                  </tbody>
                )
            case 'Filter':
                return (
                  <tbody>
                    <tr>
                      <td className="node-label">Expression:</td>
                      <td><textarea rows="5" cols="50" value={this.props.node.expression.expression} className="node-input" /></td>
                    </tr>
                  </tbody>
                )
            case 'Enricher':
                return (
                  <tbody>
                    <tr>
                      <td className="node-label">Service:</td>
                      <td>
                        <div>
                          <span>Service Id:</span>{this.props.node.service.id}<br />
                          <span>Parameters:</span>
                          {this.props.node.service.parameters.map(function(params, index){
                            return (
                              <pre key={index}>
                                <span>Name:</span> {params.name}
                                <br />
                                <span>Expression:</span> {params.expression.expression}
                              </pre>
                            )
                          })}
                        </div>
                      </td>
                    </tr>
                    <tr>
                      <td className="node-label">Output:</td>
                      <td><input type="text" value={this.props.node.output} className="node-input" /></td>
                    </tr>
                  </tbody>
                )
            case 'VariableBuilder':
                return (
                  <tbody>
                    <tr>
                      <td className="node-label">Variable Name:</td>
                      <td><input type="text" value={this.props.node.varName} className="node-input" /></td>
                    </tr>
                    <tr>
                      <td className="node-label">Fields:</td>
                      <td>
                        {this.props.node.fields.map(function(fields, index){
                          return (
                            <pre key={index}>
                              <span>Name:</span> {fields.name} <br />
                              <span>Expression:</span> {fields.expression.expression}
                            </pre>
                          )
                        })}
                      </td>
                    </tr>

                  </tbody>
                )
            case 'Switch':
                return (
                  <tbody>
                    <tr>
                      <td className="node-label">Expression:</td>
                      <td><input type="text" value={this.props.node.expression.expression} className="node-input" /></td>
                    </tr>
                    <tr>
                      <td className="node-label">exprVal:</td>
                      <td><input type="text" value={this.props.node.exprVal} className="node-input" /></td>
                    </tr>
                  </tbody>
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
            <table>
                <tbody>
                    <tr>
                        <td className="node-label">Type:</td>
                        <td><input type="text" value={this.props.node.type} className="node-input" /></td>
                    </tr>
                    <tr>
                        <td className="node-label">Id:</td>
                        <td><input type="text" value={this.props.node.id} className="node-input" /></td>
                    </tr>
                </tbody>
                {this.customNode()}
            </table>
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
          content : {
            borderRadius: '0',
            padding: '0'
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
