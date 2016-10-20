import React, {Component} from 'react';
import {render} from 'react-dom';
import { connect } from 'react-redux';
import { bindActionCreators } from 'redux';
import classNames from 'classnames';
import Modal from 'react-modal';
import _ from 'lodash';
import LaddaButton from 'react-ladda';
import laddaCss from 'ladda/dist/ladda.min.css'
import * as EspActions from '../../actions/actions';
import { ListGroupItem } from 'react-bootstrap';
import NodeUtils from './NodeUtils';
import NodeDetailsContent from './NodeDetailsContent';


class NodeDetailsModal extends React.Component {

  static propTypes = {
    nodeToDisplay: React.PropTypes.object.isRequired,
    processId: React.PropTypes.string.isRequired,
    nodeErrors: React.PropTypes.array.isRequired
  }

  constructor(props) {
    super(props);
    this.state = {
      isEditMode: false,
      editedNode: props.nodeToDisplay,
      currentNodeId: props.nodeToDisplay.id,
      pendingRequest: false
    };
  }

  componentDidUpdate(prevProps, prevState){
    //fixme na razie tak, bo undo w devtoolsach nie uderza do backendu
    if (!_.isEqual(prevProps.nodeToDisplay, this.props.nodeToDisplay)) {
      this.setState({editedNode: this.props.nodeToDisplay})
    }
  }

  closeModal = () => {
    this.setState({isEditMode: true})
    this.props.actions.closeNodeDetails()
  }

  toggleEdition = () => {
    this.setState({isEditMode: !this.state.isEditMode});
  }

  editNodeData = () => {
    this.toggleEdition();
  }

  performNodeEdit = () => {
    this.setState( { pendingRequest: true})
    this.props.actions.editNode(this.props.processToDisplay, this.props.nodeToDisplay, this.state.editedNode).then (() =>
      this.setState( { pendingRequest: false, isEditMode: false})
    )
  }

  nodeAttributes = () => {
    var nodeAttributes = require('json!../../assets/json/nodeAttributes.json');
    return nodeAttributes[NodeUtils.nodeType(this.props.nodeToDisplay)];
  }

  updateNodeState = (newNodeState) => {
    this.setState( { editedNode: newNodeState})
  }

  renderEditButtons(buttonClasses) {
    var editButtonClasses = classNames(buttonClasses, 'pull-left', {'hidden': this.state.isEditMode})
    var saveButtonClasses = classNames(buttonClasses, 'pull-left', {'hidden': !this.state.isEditMode})
    if (this.props.loggedUser.canWrite) {
      return ([
        <LaddaButton key="1" title="Save node details" className={saveButtonClasses}
                                   loading={this.state.pendingRequest}
                                   buttonStyle='zoom-in' onClick={this.performNodeEdit}>Save</LaddaButton>,
        <button key="2" type="button" title="Edit node details" className={editButtonClasses} onClick={this.editNodeData}>
          Edit
        </button>
      ] );
    } else {
      return null;
    }
  }

  render() {
    var isOpen = !(_.isEmpty(this.props.nodeToDisplay))
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
    var headerStyles = {
      backgroundColor: this.nodeAttributes().styles.fill,
      color: this.nodeAttributes().styles.color
    };

    return (
      <div className="objectModal">
        <Modal isOpen={isOpen} style={modalStyles} onRequestClose={this.closeModal}>
          <div id="modalHeader" style={headerStyles}>
            <span>{NodeUtils.nodeType(this.props.nodeToDisplay)}</span>
          </div>
          <div id="modalContent">
            <NodeDetailsContent isEditMode={this.state.isEditMode} node={this.state.editedNode}
                                nodeErrors={this.props.nodeErrors} onChange={this.updateNodeState}/>
          </div>
          <div id="modalFooter">
            <div>
              {this.renderEditButtons(buttonClasses)}
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


function mapState(state) {
  return {
    nodeToDisplay: state.graphReducer.nodeToDisplay,
    processId: state.graphReducer.processToDisplay.id,
    nodeErrors: _.get(state.graphReducer.processToDisplay, `validationResult.invalidNodes[${state.graphReducer.nodeToDisplay.id}]`, []),
    processToDisplay: state.graphReducer.processToDisplay,
    loggedUser: state.settings.loggedUser
  };
}

function mapDispatch(dispatch) {
  return {
    actions: bindActionCreators(EspActions, dispatch)
  };
}

export default connect(mapState, mapDispatch)(NodeDetailsModal);