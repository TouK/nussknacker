import React from "react"
import Modal from "react-modal"
import {connect} from "react-redux"
import ActionsUtils from "../../actions/ActionsUtils"
import ProcessUtils from "../../common/ProcessUtils"
import "../../stylesheets/visualization.styl"
import ProcessDialogWarnings from "./ProcessDialogWarnings"

//TODO: consider extending GenericModalDialog
class ConfirmDialog extends React.Component {

  componentDidMount = () => {
    //is this right place for it?
    window.onbeforeunload = (e) => {
      if (!this.props.nothingToSave) {
        return "" // it causes browser alert on reload/close tab with default message that cannot be changed
      }
    }
  }

  closeDialog = () => {
    this.props.actions.toggleConfirmDialog(false)
  }

  confirm = () => {
    this.props.confirmDialog.onConfirmCallback()
    this.closeDialog()
  }

  render() {
    const confirmDialog = this.props.confirmDialog
    return (
      <Modal isOpen={confirmDialog.isOpen}
             shouldCloseOnOverlayClick={false}
             onRequestClose={this.closeDialog}>
        <div className="draggable-container">
          <div className="espModal confirmationModal modalContentDark">
            <p>{confirmDialog.text}</p>
            <ProcessDialogWarnings processHasWarnings={this.props.processHasWarnings}/>
            <div className="confirmationButtons">
              <button type="button" title={confirmDialog.denyText} className="modalButton"
                      onClick={this.closeDialog}>{confirmDialog.denyText}</button>
              <button type="button" title={confirmDialog.confirmText} className="modalButton"
                      onClick={this.confirm}>{confirmDialog.confirmText}</button>
            </div>
          </div>
        </div>
      </Modal>
    )
  }
}

function mapState(state) {
  const processHasNoWarnings = ProcessUtils.hasNoWarnings(state.graphReducer.processToDisplay || {})
  return {
    confirmDialog: state.ui.confirmDialog,
    nothingToSave: ProcessUtils.nothingToSave(state),
    processHasWarnings: !processHasNoWarnings,
  }
}

export default connect(mapState, ActionsUtils.mapDispatchWithEspActions)(ConfirmDialog)
