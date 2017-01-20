import React, {PropTypes, Component} from "react";
import {render} from "react-dom";
import {connect} from "react-redux";
import {bindActionCreators} from "redux";
import {browserHistory} from "react-router";
import Dropzone from "react-dropzone";
import ActionsUtils from "../../actions/ActionsUtils";
import HttpService from "../../http/HttpService";
import DialogMessages from "../../common/DialogMessages";
import ProcessUtils from "../../common/ProcessUtils";

class ProcessActions extends React.Component {

  static propTypes = {
    graphLayout: React.PropTypes.func.isRequired,
    exportGraph: React.PropTypes.func.isRequired,
    isTesting: React.PropTypes.bool.isRequired
  }

  //only check deployment status in db
  isRunning = () => this.props.fetchedProcessDetails && (this.props.fetchedProcessDetails.currentlyDeployedAt || []).length > 0

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
      return HttpService.stop(this.processId()).then((resp) => {
        //ten kod wykonuje sie nawet kiedy deploy sie nie uda, bo wyzej robimy catch i w przypadku bledu tutaj dostajemy undefined, pomyslec jak ladnie to rozwiazac
        this.fetchProcessDetails()
      })
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

  versionId = () => this.props.fetchedProcessDetails.processVersionId

  showMetrics = () => {
    browserHistory.push('/metrics/' + this.processId())
  }

  exportProcess = () => {
    HttpService.exportProcess(this.processId(), this.versionId())
  }

  exportProcessToPdf = () => {
    const data = this.props.exportGraph()
    HttpService.exportProcessToPdf(this.processId(), this.versionId(), data)
  }

  importProcess = (files) => {
    files.forEach((file)=>
      this.props.actions.importProcess(this.processId(), file)
    );
  }

  testProcess = (files) => {
    files.forEach((file)=>
      this.props.actions.testProcessFromFile(this.processId(), file)
    );
  }

  hideTestResults = () => {
    this.props.actions.hideTestResults()
  }

  render() {
    const buttonClass = "espButton"
    return (
      <div>
        {this.props.loggedUser.canWrite ? (
          <button type="button" className={buttonClass} disabled={this.props.nothingToSave && this.props.processIsLatestVersion} onClick={this.save}>
            Save{this.props.nothingToSave ? "" : "*"}</button> ) : null}
        <button type="button" className={buttonClass} onClick={this.props.graphLayout}>Layout</button>
        <button type="button" className={buttonClass} onClick={this.showProperties}>Properties</button>
        <hr/>
        <button type="button" className={buttonClass} onClick={this.showMetrics}>Metrics</button>
        <hr/>
        <button type="button" className={buttonClass} onClick={this.exportProcess}>Export</button>
        <button type="button" className={buttonClass} disabled={!this.props.nothingToSave} onClick={this.exportProcessToPdf}>Export to PDF</button>
        {this.props.loggedUser.canWrite ? (
          <Dropzone onDrop={this.importProcess} className="dropZone espButton">
            <div>Import</div>
          </Dropzone>
        ) : null}
        <hr/>
        {this.props.loggedUser.canDeploy ? (
          <div>
            <button disabled={!this.props.processIsLatestVersion} type="button" className={buttonClass}
                    onClick={this.deploy}>Deploy</button>
            <button type="button" disabled={!this.isRunning()} className={buttonClass} onClick={this.stop}>Stop</button>
            <hr/>
          </div>
        ) : null}
        <Dropzone onDrop={this.testProcess} disableClick={!this.props.processIsLatestVersion || !this.props.nothingToSave}
                  className={"dropZone espButton" + (!this.props.processIsLatestVersion || !this.props.nothingToSave ? " disabled" : "")}>
          <div >Test from file</div>
        </Dropzone>
        <button type="button" disabled={!this.props.isTesting} className={buttonClass} onClick={this.hideTestResults}>
          Hide tests
        </button>

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
    isTesting: !_.isEmpty(state.graphReducer.testResults),
    processIsLatestVersion: _.get(fetchedProcessDetails, 'isLatestVersion', false)
  };
}

export default connect(mapState, ActionsUtils.mapDispatchWithEspActions)(ProcessActions);