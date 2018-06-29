import React from "react";
import {connect} from "react-redux";
import _ from "lodash";
import ActionsUtils from "../../actions/ActionsUtils";
import "../../stylesheets/visualization.styl";
import GenericModalDialog from "./GenericModalDialog";
import Dialogs from "./Dialogs"
import CommentInput from "../CommentInput";
import HttpService from "../../http/HttpService";
import ProcessUtils from "../../common/ProcessUtils";
import ProcessDialogWarnings from "./ProcessDialogWarnings";

class DeployProcessDialog extends React.Component {

  constructor(props) {
    super(props);
    this.initState = {
      comment: ""
    }
    this.state = this.initState
  }

  deploy = (closeDialog) => {
    const { actions, processId, processVersionId } = this.props
    const comment = this.state.comment

    closeDialog()

    return HttpService.deploy(processId)
      .then(resp => {
        if (resp.isSuccess) {
          actions.addComment(processId, processVersionId, comment)
        }
        actions.displayCurrentProcessVersion(processId)
      })
  }

  okBtnConfig = () => {
    const commentIsRequired = this.props.deploySettings.requireComment
    const isDeployDisabled = commentIsRequired && _.isEmpty(this.state.comment)
    const toolTip = "Comment is required."

    return isDeployDisabled ? {disabled: true, title: toolTip} : {}
  }

  onInputChange = (e) => {
    this.setState({comment: e.target.value})
  }

  render() {
    return (
      <GenericModalDialog
        init={() => this.setState(this.initState)}
        type={Dialogs.types.deployProcess}
        confirm={this.deploy}
        okBtnConfig={this.okBtnConfig()}
      >
        <p>Deploy process {this.props.processId}</p>
        <ProcessDialogWarnings processHasWarnings={this.props.processHasWarnings} />
        <CommentInput onChange={this.onInputChange}/>
      </GenericModalDialog>
    );
  }
}

function mapState(state) {
  const processHasNoWarnings = ProcessUtils.hasNoWarnings(state.graphReducer.processToDisplay || {})

  return {
    deploySettings: _.get(state.settings, "featuresSettings.deploySettings") || {},
    processId: _.get(state.graphReducer, 'fetchedProcessDetails.id'),
    processVersionId: _.get(state.graphReducer, "fetchedProcessDetails.processVersionId"),
    processHasWarnings: !processHasNoWarnings
  }
}

export default connect(mapState, ActionsUtils.mapDispatchWithEspActions)(DeployProcessDialog)


