import _ from "lodash"
import PropTypes from "prop-types"
import React, {Component} from "react"
import {Scrollbars} from "react-custom-scrollbars"
import {connect} from "react-redux"
import ActionsUtils from "../actions/ActionsUtils"
import * as DialogMessages from "../common/DialogMessages"
import ProcessUtils from "../common/ProcessUtils"
import "../stylesheets/processHistory.styl"
import Date from "./common/Date"
import i18next from "i18next"

export class ProcessHistory_ extends Component {

  static propTypes = {
    processHistory: PropTypes.array.isRequired,
    lastDeployedAction: PropTypes.object,
  }

  constructor(props) {
    super(props)
    this.state = {
      currentProcess: {},
    }
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
        this.doShowProcess(process)
      }, "DISCARD", "NO")
    }
  }

  processVersionOnTimeline(process, index) {
    if (_.isEmpty(this.state.currentProcess)) {
      // eslint-disable-next-line i18next/no-literal-string
      return index == 0 ? "current" : "past"
    } else {
      // eslint-disable-next-line i18next/no-literal-string
      return _.isEqual(process.createDate, this.state.currentProcess.createDate) ? "current" : process.createDate < this.state.currentProcess.createDate ? "past" : "future";
    }
  }

  latestVersionIsNotDeployed = (index, historyEntry) => {
    const deployedProcessVersionId = _.get(this.props.lastDeployedAction, "processVersionId")
    return index === 0 && (_.isUndefined(deployedProcessVersionId) || historyEntry.processVersionId !== deployedProcessVersionId)
  }

  render() {
    return (
      <Scrollbars renderTrackHorizontal={props => <div className="hide"/>} autoHeight autoHeightMax={300} hideTracksWhenNotNeeded={true}>
        <ul id="process-history">
          {this.props.processHistory.map ((historyEntry, index) => {
            return (
              <li key={index} className={this.processVersionOnTimeline(historyEntry, index)} onClick={this.showProcess.bind(this, historyEntry, index)}>
                {`v${historyEntry.processVersionId}`} | {historyEntry.user}
                {this.latestVersionIsNotDeployed(index, historyEntry) ?
                  <small> <span title="Latest version is not deployed" className="glyphicon glyphicon-warning-sign"/></small> :
                  null
                }
                <br/>
                <small><i><Date date={historyEntry.createDate}/></i></small>
                <br/>

                {
                  historyEntry.processVersionId === _.get(this.props.lastDeployedAction, "processVersionId") ?
                    <small key={index}>
                      <i><Date date={this.props.lastDeployedAction.performedAt}/></i>
                      <span className="label label-info">{i18next.t("processHistory.lastDeployed", "Last deployed")}</span>
                    </small> : null
                }
              </li>
            )
          })}
        </ul>
      </Scrollbars>
    )
  }
}

function mapState(state) {
  return {
    processHistory: _.get(state.graphReducer.fetchedProcessDetails, "history", []),
    lastDeployedAction: _.get(state.graphReducer.fetchedProcessDetails, "lastDeployedAction", null),
    nothingToSave: ProcessUtils.nothingToSave(state),
    businessView: state.graphReducer.businessView
  }
}

export default connect(mapState, ActionsUtils.mapDispatchWithEspActions)(ProcessHistory_)
