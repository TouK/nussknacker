import React, {PropTypes, Component} from "react";
import {render} from "react-dom";
import {connect} from "react-redux";
import {bindActionCreators} from "redux";
import ActionsUtils from "../../actions/ActionsUtils";
import HttpService from "../../http/HttpService";

class ProcessActions extends React.Component {

  static propTypes = {
    graphLayout: React.PropTypes.func.isRequired
  }

  showProperties = () => {
    this.props.actions.displayModalNodeDetails(this.props.processToDisplay.properties)
  }

  save = () => {
    return HttpService.saveProcess(this.processId(), this.props.processToDisplay).then ((resp) => {
      this.clearHistory()
      this.fetchProcessDetails()
    })
  }

  deploy = () => {
    HttpService.deploy(this.processId()).then((resp) => {
      //ten kod wykonuje sie nawet kiedy deploy sie nie uda, bo wyzej robimy catch i w przypadku bledu tutaj dostajemy undefined, pomyslec jak ladnie to rozwiazac
      this.fetchProcessDetails()
    })
  }

  stop = () => {
    HttpService.stop(this.processId())
  }

  clearHistory = () => {
    return this.props.actions.clear()
  }

  fetchProcessDetails = () => {
    this.props.actions.displayCurrentProcessVersion(this.processId())
  }

  processId = () => {
    return this.props.processToDisplay.id
  }

  dataResolved = () => {
    return !_.isEmpty(this.props.fetchedProcessDetails)
  }

  render() {
    const nothingToSave = this.dataResolved() ? _.isEqual(this.props.fetchedProcessDetails.json, this.props.processToDisplay) : true
    const buttonClass = "espButton"
    return (
      <div>
        {this.props.loggedUser.canWrite ? (
          <button type="button" className={buttonClass} disabled={nothingToSave} onClick={this.save}>Save{nothingToSave? "" : "*"}</button>
        ) : null}
        <button type="button" className={buttonClass} onClick={this.props.graphLayout}>Layout</button>
        <button type="button" className={buttonClass} onClick={this.showProperties}>Properties</button>
        <hr/>
        {this.props.loggedUser.canDeploy ? (
          <button type="button" className={buttonClass} onClick={this.deploy}>Deploy</button>
        ) : null}
        <button type="button" className={buttonClass} onClick={this.stop}>Stop</button>
      </div>
    )
  }

}

function mapState(state) {
  return {
    fetchedProcessDetails: state.graphReducer.fetchedProcessDetails,
    processToDisplay: state.graphReducer.processToDisplay,
    loggedUser: state.settings.loggedUser
  };
}

export default connect(mapState, ActionsUtils.mapDispatchWithEspActions)(ProcessActions);