import "ladda/dist/ladda.min.css"
import _ from "lodash"
import PropTypes from "prop-types"
import React from "react"
import Draggable from "react-draggable"
import LaddaButton from "react-ladda"
import Modal from "react-modal"
import {connect} from "react-redux"
import ActionsUtils from "../../../actions/ActionsUtils"
import EspModalStyles from "../../../common/EspModalStyles"
import NodeUtils from "../NodeUtils"
import EdgeDetailsContent from "./EdgeDetailsContent"

//TODO: this is still pretty switch-specific.
class EdgeDetailsModal extends React.Component {

  static propTypes = {
    edgeToDisplay: PropTypes.object.isRequired,
  }

  constructor(props) {
    super(props)
    this.state = {
      pendingRequest: false,
      editedEdge: props.edgeToDisplay,
    }
  }

  componentWillReceiveProps(props) {
    this.setState({
      editedEdge: props.edgeToDisplay,
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
    this.setState( {pendingRequest: true})
    this.props.actions.editEdge(this.props.processToDisplay, this.props.edgeToDisplay, this.state.editedEdge).then (() => {
      this.setState( {pendingRequest: false})
      this.closeModal()
    })
  }

  renderModalButtons() {
    if (!this.props.readOnly) {
      return ([
        <LaddaButton
          key="1"
          title="Apply edge details"
          className="modalButton pull-right modalConfirmButton"
          loading={this.state.pendingRequest}
          data-style="zoom-in"
          onClick={this.performEdgeEdit}
        >
          Apply
        </LaddaButton>,
        <button key="3" type="button" title="Cancel edge details" className="modalButton" onClick={this.closeModal}>
          Cancel
        </button>,
      ] )
    } else {
      return null
    }
  }

  updateEdgeProp = (prop, value) => {
    const editedEdge = _.cloneDeep(this.state.editedEdge)
    const newEdge = _.set(editedEdge, prop, value)
    this.setState({editedEdge: newEdge})
  }

  changeEdgeTypeValue = (edgeTypeValue) => {
    const fromNode = NodeUtils.getNodeById(this.props.edgeToDisplay.from, this.props.processToDisplay)
    const defaultEdgeType = NodeUtils
      .edgesForNode(fromNode, this.props.processDefinitionData).edges.find(e => e.type === edgeTypeValue)
    const newEdge = {
      ...this.state.editedEdge,
      edgeType: defaultEdgeType,
    }
    this.setState({editedEdge: newEdge})
  }

  edgeIsEditable = () => {
    const editableEdges = ["NextSwitch", "SwitchDefault"]
    return this.props.edgeToDisplay.edgeType != null && _.includes(editableEdges, this.props.edgeToDisplay.edgeType.type)
  }

  render() {
    const isOpen = !_.isEmpty(this.props.edgeToDisplay) && this.props.showEdgeDetailsModal && this.edgeIsEditable()
    const titleStyles = EspModalStyles.headerStyles("#2d8e54", "white")
    return (
      <div className="objectModal">
        <Modal isOpen={isOpen}
               shouldCloseOnOverlayClick={false}
               onRequestClose={this.closeModal}>
          <div className="draggable-container">
            <Draggable bounds="parent" handle=".modal-draggable-handle">
              <div className="espModal">
                <div className="modalHeader">
                  <div className="edge-modal-title modal-draggable-handle" style={titleStyles}>
                    <span>edge</span>
                  </div>
                </div>
                <div className="modalContentDark edge-details">
                  <EdgeDetailsContent
                    changeEdgeTypeValue={this.changeEdgeTypeValue}
                    updateEdgeProp={this.updateEdgeProp}
                    readOnly={false}
                    edge={this.state.editedEdge}
                    showValidation={true}
                    showSwitch={true}
                  />
                </div>
                <div className="modalFooter">
                  <div className="footerButtons">
                    {this.renderModalButtons()}
                  </div>
                </div>
              </div>
            </Draggable>
          </div>
        </Modal>
      </div>
    )
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
    showEdgeDetailsModal: state.ui.showEdgeDetailsModal,
  }
}

export default connect(mapState, ActionsUtils.mapDispatchWithEspActions)(EdgeDetailsModal)
