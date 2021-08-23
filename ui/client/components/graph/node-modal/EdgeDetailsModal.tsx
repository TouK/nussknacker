/* eslint-disable i18next/no-literal-string */
import "ladda/dist/ladda.min.css"
import _ from "lodash"
import React from "react"
import Draggable from "react-draggable"
import LaddaButton from "react-ladda"
import Modal from "react-modal"
import {connect} from "react-redux"
import ActionsUtils from "../../../actions/ActionsUtils"
import {isEdgeEditable} from "../../../common/EdgeUtils"
import NkModalStyles from "../../../common/NkModalStyles"
import ProcessUtils from "../../../common/ProcessUtils"
import {getEdgeToDisplay, getProcessCategory, getProcessToDisplay} from "../../../reducers/selectors/graph"
import {getCapabilities} from "../../../reducers/selectors/other"
import {getProcessDefinitionData} from "../../../reducers/selectors/settings"
import {isEdgeDetailsModalVisible} from "../../../reducers/selectors/ui"
import {ButtonWithFocus} from "../../withFocus"
import NodeUtils from "../NodeUtils"
import EdgeDetailsContent from "./EdgeDetailsContent"

//TODO: this is still pretty switch-specific.
class EdgeDetailsModal extends React.Component<StateProps, {editedEdge, pendingRequest: boolean}> {
  closeModal = () => {
    this.props.actions.closeModals()
  }
  performEdgeEdit = async () => {
    this.setState({pendingRequest: true})
    await this.props.actions.editEdge(this.props.processToDisplay, this.props.edge, this.state.editedEdge)
    this.setState({pendingRequest: false})
    this.closeModal()
  }
  updateEdgeProp = (prop, value) => {
    const editedEdge = _.cloneDeep(this.state.editedEdge)
    const newEdge = _.set(editedEdge, prop, value)
    this.setState({editedEdge: newEdge})
  }
  changeEdgeTypeValue = (edgeTypeValue) => {
    const fromNode = NodeUtils.getNodeById(this.props.edge.from, this.props.processToDisplay)
    const defaultEdgeType = NodeUtils
      .edgesForNode(fromNode, this.props.processDefinitionData).edges.find(e => e.type === edgeTypeValue)
    const newEdge = {
      ...this.state.editedEdge,
      edgeType: defaultEdgeType,
    }
    this.setState({editedEdge: newEdge})
  }

  constructor(props) {
    super(props)
    this.state = {
      pendingRequest: false,
      editedEdge: props.edge,
    }
  }

  componentDidUpdate(prevProps) {
    const {edge} = this.props
    if (!_.isEqual(prevProps.edge, edge)) {
      this.setState({editedEdge: edge})
    }
  }

  renderModalButtons() {
    return [
      <ButtonWithFocus key="2" type="button" title="Cancel node details" className="modalButton" onClick={this.closeModal}>
        Cancel
      </ButtonWithFocus>,
      !this.props.readOnly ?
        (
          <LaddaButton
            key="1"
            title="Apply edge details"
            className="modalButton pull-right modalConfirmButton"
            loading={this.state.pendingRequest}
            data-style="zoom-in"
            onClick={this.performEdgeEdit}
          >
            Apply
          </LaddaButton>
        ) :
        null,
    ]
  }

  render() {
    const {edge, showModal, readOnly} = this.props
    const isOpen = isEdgeEditable(edge) && showModal
    const titleStyles = NkModalStyles.headerStyles("#2D8E54", "white")
    return (
      <div className="objectModal">
        <Modal
          isOpen={isOpen}
          shouldCloseOnOverlayClick={false}
          onRequestClose={this.closeModal}
        >
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
                    readOnly={readOnly}
                    edge={this.state.editedEdge}
                    showValidation={true}
                    showSwitch={true}
                    variableTypes={this.props.variableTypes}
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
  const edge = getEdgeToDisplay(state)
  const processToDisplay = getProcessToDisplay(state)
  const processDefinitionData = getProcessDefinitionData(state)
  const processCategory = getProcessCategory(state)

  const nodeId = edge.from
  const errors = _.get(processToDisplay, `validationResult.errors.invalidNodes[${nodeId}]`, [])
  const variableTypes = ProcessUtils.findAvailableVariables(
    processDefinitionData,
    processCategory,
    processToDisplay,
  )(nodeId, undefined)
  return {
    edge,
    processToDisplay,
    processDefinitionData,
    variableTypes,
    edgeErrors: errors,
    readOnly: !getCapabilities(state).write,
    showModal: isEdgeDetailsModalVisible(state),
  }
}

type StateProps = ReturnType<(typeof ActionsUtils.mapDispatchWithEspActions)> & ReturnType<typeof mapState>

export default connect(mapState, ActionsUtils.mapDispatchWithEspActions)(EdgeDetailsModal)
