import "ladda/dist/ladda.min.css"
import React from "react"
import PropTypes from "prop-types"
import {connect} from "react-redux"
import Modal from "react-modal"
import _ from "lodash"
import LaddaButton from "react-ladda"
import Draggable from "react-draggable"
import ActionsUtils from "../../../actions/ActionsUtils"
import {isEdgeEditable} from "../../../common/EdgeUtils"
import NkModalStyles from "../../../common/NkModalStyles"
import {getEdgeToDisplay} from "../../../reducers/selectors/graph"
import {isEdgeDetailsModalVisible} from "../../../reducers/selectors/ui"
import {ButtonWithFocus} from "../../withFocus"
import NodeUtils from "../NodeUtils"
import EdgeDetailsContent from "./EdgeDetailsContent"
import ProcessUtils from "../../../common/ProcessUtils"

//TODO: this is still pretty switch-specific.
class EdgeDetailsModal extends React.Component {

  static propTypes = {
    edge: PropTypes.object.isRequired,
  }

  constructor(props) {
    super(props)
    this.state = {
      pendingRequest: false,
      editedEdge: props.edge,
    }
  }

  // uncommenting this function makes switch restore prop condition rather that restore one in state
  // componentWillReceiveProps(props) {
  //   let newState = {
  //     editedEdge: {
  //       from: props.edge.from,
  //       to: props.edge.to,
  //       edgeType: {
  //         type: this.state.editedEdge.edgeType.type,
  //         condition: props.edge.edgeType.condition,
  //       }
  //     },
  //   }
  //   this.setState(newState)
  // }

  componentDidUpdate(prevProps) {
    const {edge} = this.props
    if (!_.isEqual(prevProps.edge, edge)) {
      this.setState({editedEdge: edge})
    }
  }

  closeModal = () => {
    this.props.actions.closeModals()
  }

  performEdgeEdit = () => {
    this.setState({pendingRequest: true})
    this.props.actions.editEdge(this.props.processToDisplay, this.props.edge, this.state.editedEdge).then(() => {
      this.setState({pendingRequest: false})
      this.closeModal()
    })
  }

  renderModalButtons() {
    return [
      <ButtonWithFocus key="2" type="button" title="Cancel node details" className="modalButton" onClick={this.closeModal}>
        Cancel
      </ButtonWithFocus>,
      !this.props.readOnly ? (
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
      ) : null,
    ]
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
      edgeType: {
        type: defaultEdgeType.type,
        //we want to preserve previously edited (in state) value of expression condition
        condition: this.state.editedEdge.edgeType.condition,
      },
    }
    this.setState({editedEdge: newEdge})
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
  const nodeId = edge.from
  const errors = _.get(state.graphReducer.processToDisplay, `validationResult.errors.invalidNodes[${nodeId}]`, [])
  const processCategory = state.graphReducer.fetchedProcessDetails.processCategory
  const variableTypes = ProcessUtils.findAvailableVariables(state.settings.processDefinitionData,
    processCategory,
    state.graphReducer.processToDisplay)(nodeId)
  return {
    edge,
    processToDisplay: state.graphReducer.processToDisplay,
    edgeErrors: errors,
    readOnly: !state.settings.loggedUser.canWrite(processCategory) ||
      _.get(state, "graphReducer.fetchedProcessDetails.isArchived"),
    processDefinitionData: state.settings.processDefinitionData,
    showModal: isEdgeDetailsModalVisible(state),
    variableTypes: variableTypes,
  }
}

export default connect(mapState, ActionsUtils.mapDispatchWithEspActions)(EdgeDetailsModal)
