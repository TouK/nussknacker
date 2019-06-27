import React from 'react';
import PropTypes from 'prop-types';
import {connect} from 'react-redux';
import Modal from 'react-modal';
import _ from 'lodash';
import LaddaButton from "react-ladda"
import "ladda/dist/ladda.min.css"
import ActionsUtils from '../../actions/ActionsUtils';
import NodeUtils from './NodeUtils';
import ExpressionSuggest from './ExpressionSuggest'
import ModalRenderUtils from "./ModalRenderUtils"
import EspModalStyles from '../../common/EspModalStyles'

//TODO: this is still pretty switch-specific. 
class EdgeDetailsModal extends React.Component {

  static propTypes = {
    edgeToDisplay: PropTypes.object.isRequired
  }

  constructor(props) {
    super(props);
    this.state = {
      pendingRequest: false,
      editedEdge: props.edgeToDisplay
    };
  }

  componentWillReceiveProps(props) {
    this.setState({
      editedEdge: props.edgeToDisplay
    })
  }

  componentDidUpdate(prevProps, prevState){
    if (!_.isEqual(prevProps.edgeToDisplay, this.props.edgeToDisplay)) {
      this.setState({editedEdge: this.props.edgeToDisplay})
    }
  }

  closeModal = () => {
    this.props.actions.closeModals()
  }

  performEdgeEdit = () => {
    this.setState( { pendingRequest: true});
    this.props.actions.editEdge(this.props.processToDisplay, this.props.edgeToDisplay, this.state.editedEdge).then (() => {
      this.setState( { pendingRequest: false});
      this.closeModal()
    })
  }

  renderModalButtons() {
    if (!this.props.readOnly) {
      return ([
        <LaddaButton
            key="1"
            title="Save edge details"
            className='modalButton pull-right modalConfirmButton'
            loading={this.state.pendingRequest}
            data-style='zoom-in'
            onClick={this.performEdgeEdit}
        >
          Save
        </LaddaButton>,
        <button key="3" type="button" title="Close edge details" className='modalButton' onClick={this.closeModal}>
          Close
        </button>
      ] );
    } else {
      return null;
    }
  }

  updateEdgeProp = (prop, value) => {
    const editedEdge = _.cloneDeep(this.state.editedEdge)
    const newEdge = _.set(editedEdge, prop, value)
    this.setState( { editedEdge: newEdge})
  }

  changeEdgeTypeValue = (edgeTypeValue) => {
    const fromNode = NodeUtils.getNodeById(this.props.edgeToDisplay.from, this.props.processToDisplay)
    const defaultEdgeType = NodeUtils
      .edgesForNode(fromNode, this.props.processDefinitionData).edges.find(e => e.type === edgeTypeValue)
    const newEdge = {
      ...this.state.editedEdge,
      edgeType: defaultEdgeType
    }
    this.setState( { editedEdge: newEdge})
  }

  renderModalContent = () => {
    const edge = this.state.editedEdge
    const baseModalContent = (toAppend) => {
      return (
      <div className="node-table">
        {ModalRenderUtils.renderErrors(this.props.edgeErrors, "Edge has errors")}
        <div className="node-table-body">
          <div className="node-row">
            <div className="node-label">From</div>
            <div className="node-value"><input readOnly={true} type="text" className="node-input" value={edge.from}/></div>
          </div>
          <div className="node-row">
            <div className="node-label">To</div>
            <div className="node-value"><input readOnly={true} type="text" className="node-input" value={edge.to}/></div>
          </div>
          <div className="node-row">
            <div className="node-label">Type</div>
            <div className="node-value">
              <select id="processCategory" className="node-input" value={edge.edgeType.type} onChange={(e) => this.changeEdgeTypeValue(e.target.value)}>
                <option value={"SwitchDefault"}>Default</option>
                <option value={"NextSwitch"}>Condition</option>
              </select>
            </div>
          </div>
          {toAppend}
        </div>
      </div>
      )
    }

    switch (_.get(edge.edgeType, 'type')) {
      case "SwitchDefault": {
        return baseModalContent()
      }
      case "NextSwitch": {
        return baseModalContent(
          <div className="node-row">
            <div className="node-label">Expression</div>
            <div className="node-value">
              <ExpressionSuggest inputProps={{
                rows: 1, cols: 50, className: "node-input", value: edge.edgeType.condition.expression,
                onValueChange: (newValue) => this.updateEdgeProp("edgeType.condition.expression", newValue),
                language: edge.edgeType.condition.language
              }}/>
            </div>
          </div>
        )
      }
      default:
        return ''
    }
  }

  edgeIsEditable = () => {
    const editableEdges = ["NextSwitch", "SwitchDefault"]
    return this.props.edgeToDisplay.edgeType != null && _.includes(editableEdges, this.props.edgeToDisplay.edgeType.type)
  }

  render() {
    const isOpen = !_.isEmpty(this.props.edgeToDisplay) && this.props.showEdgeDetailsModal && this.edgeIsEditable()
    const headerStyles = EspModalStyles.headerStyles("#2d8e54", "white")
    return (
      <div className="objectModal">
        <Modal isOpen={isOpen} className="espModal" shouldCloseOnOverlayClick={false} onRequestClose={this.closeModal}>
          <div className="modalHeader" style={headerStyles}><span>edge</span></div>
          <div className="modalContentDark">
            {this.renderModalContent()}
          </div>
          <div className="modalFooter">
            <div className="footerButtons">
              {this.renderModalButtons()}
            </div>
          </div>
        </Modal>
      </div>
    );
  }
}

function mapState(state) {
  const nodeId = state.graphReducer.edgeToDisplay.from
  const errors = _.get(state.graphReducer.processToDisplay, `validationResult.errors.invalidNodes[${nodeId}]`, [])
  const processCategory = state.graphReducer.fetchedProcessDetails.processCategory
  return {
    edgeToDisplay: state.graphReducer.edgeToDisplay,
    processToDisplay: state.graphReducer.processToDisplay,
    edgeErrors: errors,
    readOnly: !state.settings.loggedUser.canWrite(processCategory),
    processDefinitionData: state.settings.processDefinitionData,
    showEdgeDetailsModal: state.ui.showEdgeDetailsModal
  };
}

export default connect(mapState, ActionsUtils.mapDispatchWithEspActions)(EdgeDetailsModal);
