import React, {PropTypes, Component} from "react";
import {render} from "react-dom";
import {connect} from "react-redux";
import {bindActionCreators} from "redux";
import {browserHistory} from "react-router";
import Dropzone from "react-dropzone";
import ActionsUtils from "../../actions/ActionsUtils";
import HttpService from "../../http/HttpService";
import DialogMessages from '../../common/DialogMessages';
import ProcessUtils from '../../common/ProcessUtils';
import appConfig from "appConfig";

class ProcessActions extends React.Component {

  static propTypes = {
    graphLayout: React.PropTypes.func.isRequired
  }

  showProperties = () => {
    this.props.actions.displayModalNodeDetails(this.props.processToDisplay.properties)
  }

  save = () => {
    return HttpService.saveProcess(this.processId(), this.props.processToDisplay).then((resp) => {
      this.clearHistory()
      this.fetchProcessDetails()
    })
  }

  deploy = () => {
    this.props.actions.toggleConfirmDialog(true, DialogMessages.deploy(this.processId()), () => {
      return HttpService.deploy(this.processId()).then((resp) => {
        //ten kod wykonuje sie nawet kiedy deploy sie nie uda, bo wyzej robimy catch i w przypadku bledu tutaj dostajemy undefined, pomyslec jak ladnie to rozwiazac
        this.fetchProcessDetails()
      })
    })
  }

  stop = () => {
    this.props.actions.toggleConfirmDialog(true, DialogMessages.stop(this.processId()), () => {
      return HttpService.stop(this.processId())
    })
  }

  clearHistory = () => {
    return this.props.undoRedoActions.clear()
  }

  fetchProcessDetails = () => {
    this.props.actions.displayCurrentProcessVersion(this.processId())
  }

  processId = () => {
    return this.props.processToDisplay.id
  }

  showMetrics = () => {
    browserHistory.push('/metrics/' + this.processId())
  }

  exportProcess = () => {
    window.open(appConfig.API_URL + '/processes/export/' + this.processId() + ".json");
  }

  onDrop = (files) => {
    files.forEach((file)=>
      this.props.actions.importProcess(this.processId(), file)
    );
  }


  render() {
    const buttonClass = "espButton"
    return (
      <div>
        {this.props.loggedUser.canWrite ? (
          <button type="button" className={buttonClass} disabled={this.props.nothingToSave} onClick={this.save}>Save{this.props.nothingToSave? "" : "*"}</button> ) : null}
        <button type="button" className={buttonClass} onClick={this.props.graphLayout}>Layout</button>
        <button type="button" className={buttonClass} onClick={this.showProperties}>Properties</button>
        <hr/>
        <button type="button" className={buttonClass} onClick={this.showMetrics}>Metrics</button>
        <hr/>
        <button disabled={!this.props.processIsLatestVersion} type="button" className={buttonClass} onClick={this.exportProcess}>Export</button>

        {this.props.loggedUser.canWrite ? (
          <Dropzone onDrop={this.onDrop} className="dropZone espButton">
            <div>Import</div>
            <div>drop or click</div>
          </Dropzone>
        ) : null}
        <hr/>
        {this.props.loggedUser.canDeploy ? (
          <button disabled={!this.props.processIsLatestVersion} type="button" className={buttonClass} onClick={this.deploy}>Deploy</button>
        ) : null}
        <button type="button" className={buttonClass} onClick={this.stop}>Stop</button>
      </div>
    )
  }

}

function mapState(state) {
  const fetchedProcessDetails = state.graphReducer.fetchedProcessDetails
  return {
    fetchedProcessDetails: fetchedProcessDetails,
    processToDisplay: state.graphReducer.processToDisplay,
    loggedUser: state.settings.loggedUser,
    nothingToSave: ProcessUtils.nothingToSave(state),
    processIsLatestVersion: _.get(fetchedProcessDetails, 'isLatestVersion', false)
  };
}

export default connect(mapState, ActionsUtils.mapDispatchWithEspActions)(ProcessActions);