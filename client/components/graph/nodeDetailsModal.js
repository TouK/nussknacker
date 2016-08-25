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
            <td className="node-value"><input type="text" value={fieldValue} className="node-input" readOnly/></td>
          </tr>
        )
      case 'textarea':
        return (
          <tr>
            <td className="node-label">{fieldLabel}</td>
            <td className="node-value"><textarea rows="5" cols="50" value={fieldValue} className="node-input" readOnly/></td>
          </tr>
        )
      case 'child-input':
        return (
          <div className={fieldType}>
            <div className="node-label">{fieldLabel}</div>
            <div className="node-value">
              <input type="text" value={fieldValue} className="node-input" readOnly/>
            </div>
          </div>
        )
      case 'child-table':
        return (
          <div className={fieldType}>
            {(() => {
              if (fieldLabel) {
                return <div className="node-label">{fieldLabel}</div>
              }
            })()}
            <div className="node-value">
              {fieldValue.map(function(params, index){
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
                      <td className="node-child-group">
                        {this.createField("child-input", "Type:", this.props.node.ref.typ)}
                        <div className="node-label">Parameters:</div>
                        <div className="node-value">
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
                      </td>
                    </tr>
                  </tbody>
                )
            case 'Sink':
                return (
                  <tbody>
                    <tr>
                      <td className="node-label">Ref:</td>

                      <td className="node-child-group">
                        {this.createField("child-input", "Type:", this.props.node.ref.typ)}
                        <div className="node-label">Parameters:</div>
                        <div className="node-value">
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
                      <td className="node-child-group">
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
                      <td className="node-child-group node-variables">
                        {this.createField("child-table", "", this.props.node.fields)}
                      </td>

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
            case 'Aggregate':
              return (
                <tbody>
                  {this.createField("textarea", "Key Expression:", this.props.node.keyExpression.expression)}
                  {this.createField("textarea", "Trigger Expression:", this.props.node.triggerExpression.expression)}
                  {this.createField("input", "Folding function", this.props.node.foldingFunRef)}
                  {this.createField("input", "Duration (ms):", this.props.node.durationInMillis)}
                  {this.createField("input", "Slide (ms):", this.props.node.slideInMillis)}
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
