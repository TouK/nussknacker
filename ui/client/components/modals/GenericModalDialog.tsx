import "ladda/dist/ladda.min.css"
import React, {PropsWithChildren} from "react"
import Draggable from "react-draggable"
import LaddaButton from "react-ladda"
import Modal from "react-modal"
import {connect} from "react-redux"
import ActionsUtils from "../../actions/ActionsUtils"
import {RootState} from "../../reducers"
import {getOpenDialog} from "../../reducers/selectors/ui"
import "../../stylesheets/visualization.styl"
import {ButtonWithFocus} from "../withFocus"
import {DialogType} from "./DialogsTypes"

type OwnProps = {
  okBtnConfig?: $TodoType,
  style?: string,
  header?: string,
  confirm?: (close: () => void) => PromiseLike<void>,
  type: DialogType,
  init?: () => void,
}

type State = {
  pendingRequest: boolean,
}

class GenericModalDialog extends React.Component<Props, State> {
  state = {
    pendingRequest: false,
  }

  closeDialog = () => {
    this.props.init()
    this.removePending()
    this.props.actions.toggleModalDialog(null)
  }

  removePending = () => {
    this.setState({pendingRequest: false})
  }

  onOk = () => {
    this.setState({pendingRequest: true})
    this.props.confirm(this.closeDialog).then(
      this.closeDialog,
      this.removePending,
    )
  }

  renderOkBtn = () => {
    const STYLE = "zoom-in"
    return (
      <LaddaButton
        key="1"
        title="OK"
        className="modalButton modalConfirmButton"
        data-style={STYLE}
        loading={this.state.pendingRequest}
        onClick={() => this.onOk()}
        {...this.props.okBtnConfig}
      >OK</LaddaButton>
    )
  }

  render() {
    return (
      <Modal
        isOpen={this.props.isOpen}
        shouldCloseOnOverlayClick={false}
        onRequestClose={this.closeDialog}
      >
        <div className="draggable-container">
          <Draggable bounds="parent" handle=".modal-draggable-handle">
            <div className={`espModal ${this.props.style || "confirmationModal"}`} data-testid="modal">
              {this.props.header ?
                (
                  <div className="modal-title modal-draggable-handle" style={{color: "white", backgroundColor: "#70C6CE"}}>
                    <span>{this.props.header}</span>
                  </div>
                ) :
                null}
              <div className="modalContentDark">
                {this.props.children}
                <div className="confirmationButtons">
                  <ButtonWithFocus
                    type="button"
                    title="CANCEL"
                    className="modalButton"
                    onClick={this.closeDialog}
                  >CANCEL</ButtonWithFocus>
                  {this.props.confirm ? this.renderOkBtn() : null}
                </div>
              </div>
            </div>
          </Draggable>
        </div>
      </Modal>
    )
  }
}

const mapState = (state: RootState, props: OwnProps) => ({
  isOpen: getOpenDialog(state) === props.type,
})

const mapDispatch = ActionsUtils.mapDispatchWithEspActions

type Props = PropsWithChildren<OwnProps> & ReturnType<typeof mapState> & ReturnType<typeof mapDispatch>

export default connect(mapState, mapDispatch)(GenericModalDialog)

