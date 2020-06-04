import _ from "lodash"
import PropTypes from "prop-types"
import React from "react"
import Draggable from "react-draggable"
import Modal from "react-modal"
import {connect} from "react-redux"
import ActionsUtils from "../actions/ActionsUtils"
import EspModalStyles from "../common/EspModalStyles"
import {allValid, mandatoryValueValidator} from "./graph/node-modal/editors/Validators"
import HttpService from "../http/HttpService"
import "../stylesheets/visualization.styl"
import ValidationLabels from "./modals/ValidationLabels"
import * as DialogMessages from "../common/DialogMessages"
import {goToProcess} from "../actions/nk/showProcess"

//TODO: Consider integrating with GenericModalDialog
class AddProcessDialog extends React.Component {

  static propTypes = {
    categories: PropTypes.array.isRequired,
    isOpen: PropTypes.bool.isRequired,
    onClose: PropTypes.func.isRequired,
    isSubprocess: PropTypes.bool,
    visualizationPath: PropTypes.string.isRequired,
    message: PropTypes.string.isRequired,
    clashedNames: PropTypes.array,
  }

  initialState(props) {
    return {processId: "", processCategory: _.head(props.categories) || ""}
  }

  constructor(props) {
    super(props)
    this.state = this.initialState(props)
  }

  closeDialog = () => {
    this.setState(this.initialState(this.props))
    this.props.onClose()
  }

  confirm = () => {
    const processId = this.state.processId
    HttpService.createProcess(this.state.processId, this.state.processCategory, this.props.isSubprocess).then((response) => {
      this.closeDialog()
      goToProcess(processId)
    })
  }

  render() {
    const titleStyles = EspModalStyles.headerStyles("#2D8E54", "white")
    const nameValidators = prepareNameValidators(this.props.clashedNames)

    return (
      <Modal
        isOpen={this.props.isOpen}
        shouldCloseOnOverlayClick={false}
        onRequestClose={this.closeDialog}
      >
        <div className="draggable-container">
          <Draggable bounds="parent" handle=".modal-draggable-handle">
            <div className="espModal">
              <div className="modalHeader">
                <div className="modal-title modal-draggable-handle" style={titleStyles}>
                  <span>{this.props.message}</span>
                </div>
              </div>

              <div className="modalContentDark">
                <div className="node-table">
                  <div className="node-table-body">
                    <div className="node-row">
                      <div className="node-label">Name</div>
                      <div className="node-value">
                        <input
                          autoFocus={true}
                          type="text"
                          id="newProcessId"
                          className="node-input"
                          value={this.state.processId}
                          onChange={(e) => this.setState({processId: e.target.value})}
                        />
                        <ValidationLabels validators={nameValidators} values={[this.state.processId]}/>
                      </div>
                    </div>
                    <div className="node-row">
                      <div className="node-label">Category</div>
                      <div className="node-value">
                        <select
                          id="processCategory"
                          className="node-input"
                          onChange={(e) => this.setState({processCategory: e.target.value})}
                        >
                          {this.props.categories.map((cat, index) => (
                            <option key={index} value={cat}>{cat}</option>))}
                        </select>
                      </div>
                    </div>
                  </div>
                </div>
              </div>

              <div className="modalFooter">
                <div className="footerButtons">
                  <button type="button" title="Cancel" className="modalButton" onClick={this.closeDialog}>Cancel
                  </button>
                  <button
                    type="button"
                    title="Create"
                    className="modalButton"
                    disabled={!allValid(nameValidators, this.state.processId)}
                    onClick={this.confirm}
                  >Create
                  </button>
                </div>
              </div>
            </div>
          </Draggable>
        </div>
      </Modal>
    )
  }
}

function mapState(state) {
  const user = state.settings.loggedUser
  return {
    categories: (user.categories || []).filter(c => user.canWrite(c)),
  }
}

//TODO: move this validation to backend to simplify FE code
const nameAlreadyExists = (clashedNames, name) => {
  return clashedNames.some(processName => processName === name)
}

const prepareNameValidators = (clashedNames) => [
  mandatoryValueValidator,
  {
    isValid: (name) => !nameAlreadyExists(clashedNames, name),
    message: DialogMessages.valueAlreadyTaken,
    description: DialogMessages.valueAlreadyTakenDescription,
  },
]

export default connect(mapState, ActionsUtils.mapDispatchWithEspActions)(AddProcessDialog)

