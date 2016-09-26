import React from 'react';
import { render } from 'react-dom';
import { Link } from 'react-router';
import Graph from '../components/graph/Graph';
import HttpService from '../http/HttpService'
import _ from 'lodash';
import NodeDetailsModal from '../components/graph/nodeDetailsModal.js';
import { DropdownButton, MenuItem } from 'react-bootstrap';
import { connect } from 'react-redux';
import { bindActionCreators } from 'redux';
import * as EspActions from '../actions/actions';
import NodeUtils from '../components/graph/NodeUtils'

import '../stylesheets/visualization.styl';

const Visualization = React.createClass({

  getInitialState: function() {
    return { timeoutId: null, intervalId: null };
  },

  componentDidMount() {
    // this.startPollingForUpdates() fixme pobierac tylko czy job jest uruchomiony
    this.fetchProcessDetails();
  },

  componentWillUnmount() {
    clearTimeout(this.state.timeoutId)
    clearInterval(this.state.intervalId)
  },

  startPollingForUpdates() {
    var timeoutId = setTimeout(() =>
      this.setState({ intervalId: setInterval(this.fetchProcessDetails, 10000) }),
    2000)
    this.setState({timeoutId: timeoutId})
  },

  fetchProcessDetails() {
    return HttpService.fetchProcessDetails(this.props.params.processId)
      .then((processDetails) => this.props.actions.displayProcess(processDetails))
  },

  showProperties() {
    this.props.actions.displayNodeDetails(this.props.processToDisplay.properties)
  },

  deploy() {
    HttpService.deploy(this.props.params.processId)
  },

  stop() {
    HttpService.stop(this.props.params.processId)
  },

  showDeployedProcess() {
    this.props.actions.displayDeployedProcess(this.props.processDetails)
  },

  showCurrentProcess() {
    this.props.actions.displayProcess(this.props.processDetails)
  },

  isRunning() {
    return this.props.processDetails.isRunning
  },

  render: function() {
    return _.isEmpty(this.props.processDetails) ? null :
    (
        <div className="Page">
            {!_.isEmpty(this.props.nodeToDisplay) && NodeUtils.nodeIsProperties(this.props.nodeToDisplay) ?
              <NodeDetailsModal onProcessEdit={this.fetchProcessDetails} editUsing={HttpService.editProcessProperties}/>
              : null}
            <div>
              <div id="esp-action-panel">
                {this.props.deployedAndCurrentProcessDiffer ? <span title="Current version differs from deployed one" className="glyphicon glyphicon-warning-sign tag-warning"/> : null}
                <DropdownButton bsStyle="default" title="Action" id="actionDropdown">
                  <MenuItem onSelect={this.showProperties}>Properties</MenuItem>
                  <MenuItem disabled={!this.props.deployedAndCurrentProcessDiffer}
                            onSelect={this.showCurrentProcess}>Show current process</MenuItem>
                  <MenuItem disabled={!this.props.deployedAndCurrentProcessDiffer || !this.isRunning() || _.isEmpty(this.props.processDetails.deployedJson)}
                            onSelect={this.showDeployedProcess}>Show deployed process</MenuItem>
                  <MenuItem divider />
                  <MenuItem onSelect={this.deploy}>Deploy</MenuItem>
                  <MenuItem onSelect={this.stop}>Stop</MenuItem>
                </DropdownButton>
                {this.tagsForProcess().map(function (tagi, tagIndex) {
                  return <div key={tagIndex} className="tagsBlockVis">{tagi}</div>
                })}
              </div>
              <Graph onProcessEdit={this.fetchProcessDetails} editUsing={HttpService.editProcessNode}/>
            </div>
        </div>
    )
  },

  tagsForProcess() {
    var deployedVersionInfo = _.isEqual(this.props.processToDisplay, this.props.processDetails.deployedJson) && this.isRunning() ? ["DEPLOYED"] : []
    var currentVersionInfo = _.isEqual(this.props.processToDisplay, this.props.processDetails.json) ? ["CURRENT"] : []
    return _.concat(deployedVersionInfo, currentVersionInfo, this.props.processDetails.tags)
  }
});

Visualization.title = 'Visualization'
Visualization.path = '/visualization/:processId'
Visualization.header = 'Wizualizacja'


function mapState(state) {
  const processDetails = state.espReducer.processDetails
  const currentAndDeployedProcessEqual = !_.isEmpty(processDetails) && _.isEqual(processDetails.json, processDetails.deployedJson) && !_.isEmpty(processDetails.deployedJson)
  return {
    nodeToDisplay: state.espReducer.nodeToDisplay,
    processToDisplay: state.espReducer.processToDisplay,
    processDetails: processDetails,
    deployedAndCurrentProcessDiffer: !currentAndDeployedProcessEqual
  };
}

function mapDispatch(dispatch) {
  return {
    actions: bindActionCreators(EspActions, dispatch)
  };
}

export default connect(mapState, mapDispatch)(Visualization);