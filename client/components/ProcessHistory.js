import React, { PropTypes, Component } from 'react';
import { render } from 'react-dom';
import { Scrollbars } from 'react-custom-scrollbars';
import { connect } from 'react-redux';
import _ from 'lodash'
import ActionsUtils from '../actions/ActionsUtils';
import DialogMessages from '../common/DialogMessages';
import '../stylesheets/processHistory.styl'
import DateUtils from '../common/DateUtils'
import ProcessUtils from '../common/ProcessUtils'

export class ProcessHistory_ extends Component {

  static propTypes = {
    processHistory: React.PropTypes.array.isRequired
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
    return this.props.actions.fetchProcessToDisplay(process.processId, process.processVersionId)
  }

  showProcess(process, index) {
    if (this.props.nothingToSave) {
      this.doShowProcess(process)
    } else {
      this.props.actions.toggleConfirmDialog(true, DialogMessages.unsavedProcessChanges(), () => {
        this.doShowProcess(process)
      })
    }
  }

  processVersionOnTimeline(process, index) {
    if (_.isEmpty(this.state.currentProcess)) {
      return index == 0 ? "current" : "past"
    } else {
      return _.isEqual(process.createDate, this.state.currentProcess.createDate) ? "current" : process.createDate < this.state.currentProcess.createDate ? "past" : "";
    }
  }

  processDisplayName(historyEntry) {
    return `${historyEntry.processName}:v${historyEntry.processVersionId}`
  }

  render() {
    return (
      <Scrollbars renderTrackHorizontal={props => <div className="hide"/>} autoHeight autoHeightMax={300} hideTracksWhenNotNeeded={true}>
        <ul id="process-history">
          {this.props.processHistory.map ((historyEntry, index) => {
            return (
              <li key={index} className={this.processVersionOnTimeline(historyEntry, index)}
                  onClick={this.showProcess.bind(this, historyEntry, index)}>
                {this.processDisplayName(historyEntry)} {historyEntry.user}
                <br/>
                <small><i>{DateUtils.format(historyEntry.createDate)}</i></small>
                <br/>
                {historyEntry.deployments.map((deployment, index) =>
                  <small key={index}>{DateUtils.format(deployment.deployedAt)} <span className="label label-info">{deployment.environment}</span></small>
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
    processHistory: _.get(state.graphReducer.fetchedProcessDetails, 'history', []),
    nothingToSave: ProcessUtils.nothingToSave(state)
  };
}

export default connect(mapState, ActionsUtils.mapDispatchWithEspActions)(ProcessHistory_);