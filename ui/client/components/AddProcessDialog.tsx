/* eslint-disable i18next/no-literal-string */
import React from "react"
import Draggable from "react-draggable"
import Modal from "react-modal"
import {connect} from "react-redux"
import * as DialogMessages from "../common/DialogMessages"
import NkModalStyles from "../common/NkModalStyles"
import {visualizationUrl} from "../common/VisualizationUrl"
import history from "../history"
import HttpService from "../http/HttpService"
import "../stylesheets/visualization.styl"
import {allValid, HandledErrorType, mandatoryValueValidator, Validator, ValidatorType} from "./graph/node-modal/editors/Validators"
import ValidationLabels from "./modals/ValidationLabels"
import {ButtonWithFocus, InputWithFocus, SelectWithFocus} from "./withFocus"

type State = {
  processId: string,
  processCategory: string,
}

//TODO: move this validation to backend to simplify FE code
function getAlreadyExistsValidator(clashedNames): Validator {
  return {
    isValid: (name) => !clashedNames.includes(name),
    message: DialogMessages.valueAlreadyTaken,
    description: DialogMessages.valueAlreadyTakenDescription,
    validatorType: ValidatorType.Frontend,
    handledErrorType: HandledErrorType.AlreadyExists,
  }
}

function prepareNameValidators(clashedNames) {
  const alreadyExistsValidator = getAlreadyExistsValidator(clashedNames)
  return [
    mandatoryValueValidator,
    alreadyExistsValidator,
  ]
}

class AddProcessDialog extends React.Component<Props, State> {
  initialState = () => ({processId: "", processCategory: this.props.categories[0] || ""})

  state = this.initialState()

  closeDialog = () => {
    this.setState(this.initialState())
    this.props.onClose()
  }

  confirm = async () => {
    const {processCategory, processId} = this.state
    await this.createProcess(processId, processCategory)
    this.closeDialog()
  }

  private async createProcess(processId: string, processCategory: string) {
    const {isSubprocess} = this.props
    await HttpService.createProcess(processId, processCategory, isSubprocess)
    history.push(visualizationUrl(processId))
  }

  render() {
    const {message, clashedNames, categories, isOpen} = this.props
    const {processId} = this.state

    const titleStyles = NkModalStyles.headerStyles("#2D8E54", "white")
    const nameValidators = prepareNameValidators(clashedNames)

    return (
      <Modal
        isOpen={isOpen}
        shouldCloseOnOverlayClick={false}
        onRequestClose={this.closeDialog}
      >
        <div className="draggable-container">
          <Draggable bounds="parent" handle=".modal-draggable-handle">
            <div className="espModal">
              <div className="modalHeader">
                <div className="modal-title modal-draggable-handle" style={titleStyles}>
                  <span>{message}</span>
                </div>
              </div>

              <div className="modalContentDark">
                <div className="node-table">
                  <div className="node-table-body">
                    <div className="node-row">
                      <div className="node-label">Name</div>
                      <div className="node-value">
                        <InputWithFocus
                          autoFocus={true}
                          type="text"
                          id="newProcessId"
                          className="node-input"
                          value={processId}
                          onChange={(e) => this.setState({processId: e.target.value})}
                        />
                        <ValidationLabels validators={nameValidators} values={[processId]}/>
                      </div>
                    </div>
                    <div className="node-row">
                      <div className="node-label">Category</div>
                      <div className="node-value">
                        <SelectWithFocus
                          id="processCategory"
                          className="node-input"
                          onChange={(e) => this.setState({processCategory: e.target.value})}
                        >
                          {categories.map((cat, index) => (
                            <option key={index} value={cat}>{cat}</option>))}
                        </SelectWithFocus>
                      </div>
                    </div>
                  </div>
                </div>
              </div>

              <div className="modalFooter">
                <div className="footerButtons">
                  <ButtonWithFocus type="button" title="Cancel" className="modalButton" onClick={this.closeDialog}>Cancel
                  </ButtonWithFocus>
                  <ButtonWithFocus
                    type="button"
                    title="Create"
                    className="modalButton"
                    disabled={!allValid(nameValidators, [processId])}
                    onClick={this.confirm}
                  >Create
                  </ButtonWithFocus>
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

const mapDispatch = {}

type OwnProps = {
  onClose: () => void,
  categories: string[],
  message: string,
  clashedNames?: string[],
  isOpen?: boolean,
  isSubprocess?: boolean,
}

type Props = OwnProps & ReturnType<typeof mapState> & typeof mapDispatch

export default connect(mapState, mapDispatch)(AddProcessDialog)
