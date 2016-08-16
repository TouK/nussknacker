import React, { Component } from 'react';
import { render } from 'react-dom';
import classNames from 'classnames';

import Modal from 'react-modal';
import { Table, Thead, Th, Tr, Td } from 'reactable';

export default class NodeDetailsModal extends Component {
    closeModal = () => {
        this.props.onClose()
    }

    createField = (fieldType, fieldLabel, fieldValue) => {
      switch (fieldType) {
      case 'input':
        return (
          <tr>
            <td className="node-label">{fieldLabel}</td>
            <td className="node-value"><input type="text" value={fieldValue} className="node-input" /></td>
          </tr>
        )
      case 'textarea':
        return (
          <tr>
            <td className="node-label">{fieldLabel}</td>
            <td><textarea rows="5" cols="50" value={fieldValue} className="node-input" /></td>
          </tr>
        )
      case 'child-input':
        return (
          <div className={fieldType}>
            <div className="node-label">{fieldLabel}</div>
            <input type="text" value={fieldValue} className="node-input" />
          </div>
        )
      case 'child-table':
        return (
          <div className={fieldType}>
            <div className="node-label">{fieldLabel}</div>
            {fieldValue.map(function(params, index){
              return (
                <pre key={index}>
                  <span>Name:</span> {params.name} <br />
                  <span>Expression:</span> {params.expression.expression}
                </pre>
              )
            })}
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
                  <tbody>
                    <tr>
                      <td className="node-label">Ref:</td>
                      <td>
                        {this.createField("child-input", "Type:", this.props.node.ref.typ)}
                        <div className="inner-label"><span>Parameters:</span></div>
                        {this.props.node.ref.parameters.map ((params, index) => {
                          return (
                            <pre className="inner-value" key={index}>
                              {this.createField("child-input", "Name:", params.name)}
                              {this.createField("child-input", "Value:", params.value)}
                            </pre>
                          )
                        })}
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
                        <div>
                          {this.createField("child-input", "Type:", this.props.node.ref.typ)}
                          <span>Parameters:</span>
                          {this.props.node.ref.parameters.map(function(params, index){
                            return (
                              <div key={index}>
                                {this.createField("child-input", "Name:", params.name)}
                                {this.createField("child-input", "Value:", params.value)}
                              </div>
                            )
                          })}
                        </div>
                      </td>
                    </tr>
                  </tbody>
                )
            case 'Filter':
                return (
                  <tbody>
                    {this.createField("textarea", "Expression:", this.props.node.expression.expression)}
                  </tbody>
                )
            case 'Enricher':
                return (
                  <tbody>
                    <tr>
                      <td className="node-label">Service:</td>
                      <td>
                        {this.createField("child-input", "Service Id:", this.props.node.service.id)}
                        {this.createField("child-table", "Parameters:", this.props.node.service.parameters)}
                      </td>
                    </tr>
                    {this.createField("input", "Output:", this.props.node.output)}
                  </tbody>
                )
            case 'VariableBuilder':
                return (
                  <tbody>
                    {this.createField("input", "Variable Name:", this.props.node.varName)}
                    <tr>
                      <td className="node-label">Fields:</td>
                      <td>{this.createField("child-table", "", this.props.node.fields)}</td>
                    </tr>
                  </tbody>
                )
            case 'Switch':
                return (
                  <tbody>
                    {this.createField("input", "Expression:", this.props.node.expression.expression)}
                    {this.createField("input", "exprVal:", this.props.node.exprVal)}
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
                  {this.createField("input", "Type:", this.props.node.type)}
                  {this.createField("input",  "Id:", this.props.node.id)}
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
                        <NodeDetails node={this.props.node}/>
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
