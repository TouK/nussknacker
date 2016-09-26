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
    editUsing: React.PropTypes.func.isRequired,
    onProcessEdit: React.PropTypes.func.isRequired,
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

  updateNodeData = () => {
    this.setState({pendingRequest: true})
    this.props.editUsing(this.props.processId, this.state.currentNodeId, this.state.editedNode).then((resp) => {
      return this.props.onProcessEdit().then(() => {
        this.setState({currentNodeId: this.state.editedNode.id, isEditMode: false})
        if (!_.isEmpty(resp.invalidNodes)) { console.error('Errors', resp.invalidNodes) }
        this.props.actions.nodeChangePersisted(this.props.nodeToDisplay, this.state.editedNode)
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
    return nodeAttributes[NodeUtils.nodeType(this.props.nodeToDisplay)];
  }

  updateNodeState = (newNodeState) => {
    this.setState( { editedNode: newNodeState})
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
            <span>{NodeUtils.nodeType(this.props.nodeToDisplay)}</span>
          </div>
          <div id="modalContent">
            <NodeDetailsContent isEditMode={this.state.isEditMode} node={this.state.editedNode}
                                validationErrors={this.props.nodeErrors} onChange={this.updateNodeState}/>
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


function mapState(state) {
  return {
    nodeToDisplay: state.espReducer.nodeToDisplay,
    processId: state.espReducer.processToDisplay.id,
    nodeErrors: _.get(state.espReducer.processToDisplay, `validationErrors.invalidNodes[${state.espReducer.nodeToDisplay.id}]`) || []
  };
}

function mapDispatch(dispatch) {
  return {
    actions: bindActionCreators(EspActions, dispatch)
  };
}

export default connect(mapState, mapDispatch)(NodeDetailsModal);