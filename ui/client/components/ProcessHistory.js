import React, {Component} from "react";
import PropTypes from "prop-types";
import {Scrollbars} from "react-custom-scrollbars";
import {connect} from "react-redux";
import _ from "lodash"
import ActionsUtils from "../actions/ActionsUtils";
import DialogMessages from "../common/DialogMessages";
import "../stylesheets/processHistory.styl"
import ProcessUtils from "../common/ProcessUtils"
import Date from "./common/Date"

export class ProcessHistory_ extends Component {

  static propTypes = {
    processHistory: PropTypes.array.isRequired
  }

  constructor(props) {
    super(props);
    this.state = {
      currentProcess: {}
    };
  }

  componentWillReceiveProps(nextProps) {
    if (!_.isEqual(nextProps.processHistory, this.props.processHistory)) {
      this.resetState()
    }
  }

  resetState() {
    this.setState({currentProcess: {}})
  }

  doShowProcess(process) {
    this.setState({currentProcess: process})
    return this.props.actions.fetchProcessToDisplay(process.processId, process.processVersionId, this.props.businessView)
  }

  showProcess(process, index) {
    if (this.props.nothingToSave) {
      this.doShowProcess(process)
    } else {
      this.props.actions.toggleConfirmDialog(true, DialogMessages.unsavedProcessChanges(), () => {
        console.log("Inside Toggle")
        this.doShowProcess(process)
      }, "DISCARD", "NO")
    }
  }

  processVersionOnTimeline(process, index) {
    if (_.isEmpty(this.state.currentProcess)) {
      return index == 0 ? "current" : "past"
    } else {
      return _.isEqual(process.createDate, this.state.currentProcess.createDate) ? "current" : process.createDate < this.state.currentProcess.createDate ? "past" : "future";
    }
  }

  latestVersionIsNotDeployed = (index, historyEntry) => {
    return _.isEqual(index, 0) && _.isEmpty(historyEntry.deployments)
  }

  render() {
    return (
      <Scrollbars renderTrackHorizontal={props => <div className="hide"/>} autoHeight autoHeightMax={300} hideTracksWhenNotNeeded={true}>
        <ul id="process-history">
          {this.props.processHistory.map ((historyEntry, index) => {
            return (
              <li key={index} className={this.processVersionOnTimeline(historyEntry, index)}
                  onClick={this.showProcess.bind(this, historyEntry, index)}>
                {`v${historyEntry.processVersionId}`} | {historyEntry.user}
                {this.latestVersionIsNotDeployed(index, historyEntry) ?
                  <small> <span title="Latest version is not deployed" className="glyphicon glyphicon-warning-sign"/></small> :
                  null
                }
                <br/>
                <small><i><Date date={historyEntry.createDate}/></i></small>
                <br/>
                {historyEntry.deployments.map((deployment, index) =>
                  <small key={index}><i><Date date={deployment.deployedAt}/></i> <span className="label label-info">{deployment.environment}</span></small>
                )}
              </li>
            )
          })}
        </ul>
      </Scrollbars>
    );
  }
}

function mapState(state) {
  return {
    processHistory: _.get(state.graphReducer.fetchedProcessDetails, "history", []),
    nothingToSave: ProcessUtils.nothingToSave(state),
    businessView: state.graphReducer.businessView
  };
}

export default connect(mapState, ActionsUtils.mapDispatchWithEspActions)(ProcessHistory_);