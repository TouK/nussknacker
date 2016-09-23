import React from 'react';
import { render } from 'react-dom';
import { Link } from 'react-router';
import Graph from '../components/graph/Graph';
import HttpService from '../http/HttpService'
import _ from 'lodash';
import NodeDetailsModal from '../components/graph/nodeDetailsModal.js';
import { DropdownButton, MenuItem } from 'react-bootstrap';

import '../stylesheets/visualization.styl';

export const Visualization = React.createClass({

  getInitialState: function() {
    return { process: {}, processJsonToShow: 'json', processDetails: {}, clickedProperties: {}, timeoutId: null, intervalId: null, deployedAndCurrentProcessDiffer: false };
  },

  componentDidMount() {
    this.startPollingForUpdates()
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
      .then((processDetails) => {
          this.setState({
            processDetails: processDetails,
            process: _.get(processDetails, this.state.processJsonToShow),
            deployedAndCurrentProcessDiffer: !this.currentAndDeployedProcessEqual(processDetails)
          })
        }
      )
  },

  currentAndDeployedProcessEqual(processDetails){
    return _.isEqual(processDetails.json, processDetails.deployedJson) && !_.isEmpty(processDetails.deployedJson)
  },

  showProperties() {
    this.setState({clickedProperties: this.state.process.properties});
  },

  onDetailsClosed() {
    this.setState({clickedProperties: {}})
  },

  deploy() {
    HttpService.deploy(this.props.params.processId)
  },

  stop() {
    HttpService.stop(this.props.params.processId)
  },

  showDeployedProcess() {
    this.setState({ process: this.state.processDetails.deployedJson, processJsonToShow: 'deployedJson'})
  },

  showCurrentProcess() {
    this.setState({ process: this.state.processDetails.json, processJsonToShow: 'json'})
  },

  isRunning() {
    return this.state.processDetails.isRunning
  },

  render: function() {
    return _.isEmpty(this.state.process) || _.isEmpty(this.state.processDetails) ? null :
    (
        <div className="Page">
            {!_.isEmpty(this.state.clickedProperties) ?
              <NodeDetailsModal
                node={this.state.clickedProperties}
                processId={this.props.params.processId}
                onProcessEdit={this.fetchProcessDetails}
                editUsing={HttpService.editProcessProperties}
                onClose={this.onDetailsClosed}/>
              : null}
            <div>
              <div id="esp-action-panel">
                {this.state.deployedAndCurrentProcessDiffer ? <span title="Current version differs from deployed one" className="glyphicon glyphicon-warning-sign tag-warning"/> : null}
                <DropdownButton bsStyle="default" title="Action" id="actionDropdown">
                  <MenuItem onSelect={this.showProperties}>Properties</MenuItem>
                  <MenuItem disabled={!this.state.deployedAndCurrentProcessDiffer}
                            onSelect={this.showCurrentProcess}>Show current process</MenuItem>
                  <MenuItem disabled={!this.state.deployedAndCurrentProcessDiffer || !this.isRunning() || _.isEmpty(this.state.processDetails.deployedJson)}
                            onSelect={this.showDeployedProcess}>Show deployed process</MenuItem>
                  <MenuItem divider />
                  <MenuItem onSelect={this.deploy}>Deploy</MenuItem>
                  <MenuItem onSelect={this.stop}>Stop</MenuItem>
                </DropdownButton>
                {this.tagsForProcess().map(function (tagi, tagIndex) {
                  return <div key={tagIndex} className="tagsBlockVis">{tagi}</div>
                })}
              </div>
              <Graph data={this.state.process} processDetails={this.state.processDetails} onProcessEdit={this.fetchProcessDetails} editUsing={HttpService.editProcessNode}/>
            </div>
        </div>
    )
  },

  tagsForProcess() {
    var deployedVersionInfo = _.isEqual(this.state.process, this.state.processDetails.deployedJson) && this.isRunning() ? ["DEPLOYED"] : []
    var currentVersionInfo = _.isEqual(this.state.process, this.state.processDetails.json) ? ["CURRENT"] : []
    return _.concat(deployedVersionInfo, currentVersionInfo, this.state.processDetails.tags)
  }
});

Visualization.title = 'Visualization'
Visualization.path = '/visualization/:processId'
Visualization.header = 'Wizualizacja'
