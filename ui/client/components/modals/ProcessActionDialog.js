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
import ValidateDeployComment from "../ValidateDeployComment";

class ProcessActionDialog extends React.Component {

  static propTypes = {
    settings: React.PropTypes.object.isRequired,
    processId: React.PropTypes.string,
    processHasWarnings: React.PropTypes.bool,
    message: React.PropTypes.string,
    displayWarnings: React.PropTypes.bool,
    action: React.PropTypes.func
  }

  constructor(props) {
    super(props);
    this.initState = {
      comment: ""
    }
    this.state = this.initState
  }

  deploy = (closeDialog) => {
    const { actions, processId } = this.props
    const comment = this.state.comment

    closeDialog()
    const deploymentPath = window.location.pathname
    return this.props.action(processId, comment)
      .then(resp => {
        const currentPath = window.location.pathname
        if (currentPath.startsWith(deploymentPath)) {
          actions.displayCurrentProcessVersion(processId)
          actions.displayProcessActivity(processId)
        }
      })
  }

  okBtnConfig = () => {
    const validated =
      ValidateDeployComment(this.state.comment, this.props.settings)

    return { disabled: !validated.isValid, title: validated.toolTip }
  }

  onInputChange = (e) => {
    this.setState({comment: e.target.value})
  }

  render() {
    return (
      <GenericModalDialog
        init={() => this.setState(this.initState)}
        type={Dialogs.types.processAction}
        confirm={this.deploy}
        okBtnConfig={this.okBtnConfig()}
      >
        <p>{this.props.message} {this.props.processId}</p>
        <ProcessDialogWarnings processHasWarnings={this.props.processHasWarnings} />
        <CommentInput onChange={this.onInputChange}/>
      </GenericModalDialog>
    );
  }
}

function mapState(state) {

  const config = state.ui.modalDialog
  const processHasNoWarnings = !config.displayWarnings || ProcessUtils.hasNoWarnings(state.graphReducer.processToDisplay || {})
  return {
    settings: Object.assign(
      {},
      _.get(state.settings, "featuresSettings.commentSettings"),
      _.get(state.settings, "featuresSettings.deploySettings")),
    processId: _.get(state.graphReducer, 'fetchedProcessDetails.id'),
    processHasWarnings: !processHasNoWarnings,
    action: config.action,
    message: config.message
  }
}

export default connect(mapState, ActionsUtils.mapDispatchWithEspActions)(ProcessActionDialog)


