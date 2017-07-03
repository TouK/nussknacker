import React from "react";
import {render} from "react-dom";
import Modal from "react-modal";
import {connect} from "react-redux";
import _ from "lodash";
import ActionsUtils from "../../actions/ActionsUtils";
import ProcessUtils from '../../common/ProcessUtils';
import "../../stylesheets/visualization.styl";
import InlinedSvgs from '../../assets/icons/InlinedSvgs'


//TODO: uwspolnic z innymi modals
class ConfirmDialog extends React.Component {

  componentDidMount = () => {
    //gdzie to powinno byc?
    window.onbeforeunload = (e) => {
      if (!this.props.nothingToSave) {
        return "" // powoduje przegladarkowy alert przy reloadzie i zamknieciu taba, z defaultowa wiadomoscia, ktorej nie da sie zmienic
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
    return (
      <Modal isOpen={this.props.confirmDialog.isOpen}
             shouldCloseOnOverlayClick={false}
             className="espModal confirmationModal" onRequestClose={this.closeDialog}>
        <div className="modalContent">
          <p>{this.props.confirmDialog.text}</p>
          {this.props.processHasSomeWarnings ?
            <div className="warning">
              <div className="icon" title="Warning" dangerouslySetInnerHTML={{__html: InlinedSvgs.tipsWarning}} />
              <p>Warnings found - please look at left panel to see details. Proceed with caution</p>
            </div> :
            null
          }
          <div className="confirmationButtons">
            <button type="button" title="Cancel" className='modalButton' onClick={this.closeDialog}>No</button>
            <button type="button" title="Yes" className='modalButton' onClick={this.confirm}>Yes</button>
          </div>
        </div>
      </Modal>
    );
  }
}

function mapState(state) {
  const processHasNoWarnings = ProcessUtils.hasNoWarnings(state.graphReducer.processToDisplay || {})
  return {
    confirmDialog: state.ui.confirmDialog,
    nothingToSave: ProcessUtils.nothingToSave(state),
    processHasSomeWarnings: !processHasNoWarnings
  }
}

export default connect(mapState, ActionsUtils.mapDispatchWithEspActions)(ConfirmDialog);

