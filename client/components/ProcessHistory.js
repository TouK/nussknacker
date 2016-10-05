import React, { PropTypes, Component } from 'react';
import { render } from 'react-dom';
import { Scrollbars } from 'react-custom-scrollbars';
import { connect } from 'react-redux';
import { bindActionCreators } from 'redux';
import _ from 'lodash'
import * as EspActions from '../actions/actions';
import HttpService from '../http/HttpService'
import '../stylesheets/processHistory.styl'

class ProcessHistory extends Component {

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

  showProcess(process, index) {
    this.setState({currentProcess: process})
    this.props.actions.fetchProcessToDisplay(process.processId, process.processVersionId)
  }

  processVersionOnTimeline(process, index) {
    if (_.isEmpty(this.state.currentProcess)) {
      return index == 0 ? "current" : "past"
    } else {
      return _.isEqual(process.createDate, this.state.currentProcess.createDate) ? "current" : process.createDate < this.state.currentProcess.createDate ? "past" : "";
    }
  }

  render() {
    return (
      <Scrollbars style={{ height: 300 }}>
        <ul id="process-history">
          {this.props.processHistory.map ((historyEntry, index) => {
            return (
              <li key={index} className={this.processVersionOnTimeline(historyEntry, index)}
                  onClick={this.showProcess.bind(this, historyEntry, index)}>{historyEntry.processName}:v{historyEntry.processVersionId} {historyEntry.user}
                <br/>
                <small><i>{historyEntry.createDate}</i></small>
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
    processHistory: _.get(state.espReducer.fetchedProcessDetails, 'history', []),
  };
}

function mapDispatch(dispatch) {
  return {
    actions: bindActionCreators(EspActions, dispatch)
  };
}

export default connect(mapState, mapDispatch)(ProcessHistory);