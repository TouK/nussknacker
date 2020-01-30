import React from "react"
import {connect} from "react-redux"
import ActionsUtils from "../../actions/ActionsUtils"
import "../../stylesheets/visualization.styl"
import Dialogs from "./Dialogs"
import GenericModalDialog from "./GenericModalDialog"

class InfoModal extends React.Component {

    render() {
        return (
            <GenericModalDialog init={() => {}}
                                 type={Dialogs.types.infoModal}>
                <p>{this.props.modalDialog.text}</p>
            </GenericModalDialog>
        )
    }
}

function mapState(state) {
    return {
        modalDialog: state.ui.modalDialog || {},
    }
}

export default connect(mapState, ActionsUtils.mapDispatchWithEspActions)(InfoModal)

