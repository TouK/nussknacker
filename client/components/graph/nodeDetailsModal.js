import React, {Component} from 'react';
import {render} from 'react-dom';
import classNames from 'classnames';
import Modal from 'react-modal';
import _ from 'lodash';
import LaddaButton from 'react-ladda';
import laddaCss from 'ladda/dist/ladda.min.css'
import { ListGroupItem } from 'react-bootstrap';
import NodeUtils from './NodeUtils';
import NodeDetailsContent from './NodeDetailsContent';


export default class NodeDetailsModal extends React.Component {

  constructor(props) {
    super(props);
    this.state = {
      isEditMode: false,
      editedNode: props.node,
      currentNodeId: props.node.id,
      pendingRequest: false
    };
  }

  closeModal = () => {
    this.setState({isEditMode: true})
    this.props.onClose()
  }

  toggleEdition = () => {
    this.setState({isEditMode: !this.state.isEditMode});
  }

  editNodeData = () => {
    this.toggleEdition();
  }

  updateNodeData = () => {
    this.setState({pendingRequest: true})
    this.props.editUsing(this.props.processId, this.state.currentNodeId, this.state.editedNode).then((resp) => {
      return this.props.onProcessEdit().then(() => {
        this.setState({currentNodeId: this.state.editedNode.id, isEditMode: false})
        if (!_.isEmpty(resp.invalidNodes)) {
          console.error('Errors', resp.invalidNodes)
        }
      })
    }).then(() =>
      this.setState({pendingRequest: false})
    ).catch( (error) => {
      //todo globalna oblsuga bledow
      this.setState({pendingRequest: false})
      console.log(error)
    })
  }

  nodeAttributes = () => {
    var nodeAttributes = require('json!../../assets/json/nodeAttributes.json');
    return nodeAttributes[NodeUtils.nodeType(this.props.node)];
  }

  updateNodeState = (newNodeState) => {
    this.setState( { editedNode: newNodeState})
  }

  render() {
    var isOpen = !(_.isEmpty(this.props.node))
    var modalStyles = {
      overlay: {
        backgroundColor: 'rgba(63, 62, 61, 0.3)'
      },
      content: {
        borderRadius: '0',
        padding: '0',
        left: '20%',
        right: '20%',
        top: '15%',
        bottom: '15%',
        border: 'none'
      }
    };

    var buttonClasses = classNames('modalButton')
    var editButtonClasses = classNames(buttonClasses, 'pull-left', {'hidden': this.state.isEditMode})
    var saveButtonClasses = classNames(buttonClasses, 'pull-left', {'hidden': !this.state.isEditMode})

    var headerStyles = {
      backgroundColor: this.nodeAttributes().styles.fill,
      color: this.nodeAttributes().styles.color
    };

    return (
      <div className="objectModal">
        <Modal isOpen={isOpen} style={modalStyles} onRequestClose={this.closeModal}>
          <div id="modalHeader" style={headerStyles}>
            <span>{NodeUtils.nodeType(this.props.node)}</span>
          </div>
          <div id="modalContent">
            <NodeDetailsContent isEditMode={this.state.isEditMode} node={this.state.editedNode}
                                validationErrors={this.props.validationErrors} onChange={this.updateNodeState}/>
          </div>
          <div id="modalFooter">
            <div>
              <LaddaButton title="Save node details" className={saveButtonClasses} loading={this.state.pendingRequest}
                           buttonStyle='zoom-in' onClick={this.updateNodeData}>Save</LaddaButton>
              <button type="button" title="Edit node details" className={editButtonClasses} onClick={this.editNodeData}>
                Edit
              </button>
              <button type="button" title="Close node details" className={buttonClasses} onClick={this.closeModal}>
                Close
              </button>
            </div>
          </div>
        </Modal>
      </div>
    );
  }
}
