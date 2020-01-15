import _ from "lodash"
import PropTypes from "prop-types"
import React, {Component} from "react"
import {Scrollbars} from "react-custom-scrollbars"
import {connect} from "react-redux"
import ActionsUtils from "../actions/ActionsUtils"
import DialogMessages from "../common/DialogMessages"
import ProcessUtils from "../common/ProcessUtils"
import "../stylesheets/processHistory.styl"
import Date from "./common/Date"
import ProcessStateUtils from "../common/ProcessStateUtils"

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
      return index == 0 ? "current" : "past"
    } else {
      return _.isEqual(process.createDate, this.state.currentProcess.createDate) ? "current" : process.createDate < this.state.currentProcess.createDate ? "past" : "future"
    }
  }

  latestVersionIsNotDeployed = (index, historyEntry) => {
    return this.isLastVersion(historyEntry) && !_.eq(this.props.lastDeployedAction, _.head(historyEntry.deployments))
  }

  isLastVersion = (historyEntry) => _.eq(_.head(this.props.processHistory), historyEntry)

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
                  historyEntry.deployments.map((deployment, index) =>
                  this.props.lastDeployedAction != null && _.eq(this.props.lastDeployedAction, deployment) ?
                    <small key={index}><i><Date date={deployment.deployedAt}/></i>
                      <span className="label label-info">{deployment.environment}</span>
                    </small>
                    : null,
                )}
              </li>
            )
          })}
        </ul>
      </Scrollbars>
    )
  }
}

function mapState(state) {
  const history = _.get(state.graphReducer.fetchedProcessDetails, "history", [])
  const deployActions = _.reverse(_.sortBy(_.flatMap(history, hist => hist.deployments), ["deployedAt"])) //make sure that we have good ordered actions
  const deployedActions = _.filter(deployActions, da => ProcessStateUtils.isDeployedAction(da))
  const lastDeployedAction = _.head(deployedActions)

  return {
    processHistory: history,
    lastDeployedAction: lastDeployedAction,
    nothingToSave: ProcessUtils.nothingToSave(state),
    businessView: state.graphReducer.businessView,
  }
}

export default connect(mapState, ActionsUtils.mapDispatchWithEspActions)(ProcessHistory_)
