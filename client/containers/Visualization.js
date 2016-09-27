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
    window.onkeydown = (event) => {
      if (event.ctrlKey && !event.shiftKey && event.key.toLowerCase() == "z") {
        this.undo()
      }
      if (event.ctrlKey && event.shiftKey && event.key.toLowerCase() == "z") {
        this.redo()
      }
    }
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
    this.props.actions.displayDeployedProcess(this.props.fetchedProcessDetails)
  },

  showCurrentProcess() {
    this.props.actions.displayProcess(this.props.fetchedProcessDetails)
  },

  isRunning() {
    return this.props.fetchedProcessDetails.isRunning
  },

  undo() {
    return this.props.actions.undo()
  },

  redo() {
    return this.props.actions.redo()
  },

  save() {
    return HttpService.saveProcess(this.props.params.processId, this.props.processToDisplay).then ((resp) => {
      this.fetchProcessDetails()
    })
  },

  render: function() {
    if (_.isEmpty(this.props.fetchedProcessDetails)) {
      return null
    } else {
      const deployedVersionIsDisplayed = _.isEqual(this.props.processToDisplay, this.props.fetchedProcessDetails.deployedJson) //fixme blokowanie edycji dla zdeplojowanej wersji
      const nothingToSave = _.isEqual(this.props.fetchedProcessDetails.json, this.props.processToDisplay) || deployedVersionIsDisplayed
      return (
        <div className="Page">
          <div>
            <div id="esp-action-panel">
              <button type="button" className="btn btn-default" disabled={nothingToSave} onClick={this.save}>SAVE{nothingToSave? "" : "*"}</button>
              {this.props.deployedAndCurrentProcessDiffer ? <span title="Current version differs from deployed one" className="glyphicon glyphicon-warning-sign tag-warning"/> : null}
              <DropdownButton bsStyle="default" title="Action" id="actionDropdown">
                <MenuItem onSelect={this.showProperties}>Properties</MenuItem>
                <MenuItem disabled={!this.props.deployedAndCurrentProcessDiffer}
                          onSelect={this.showCurrentProcess}>Show current process</MenuItem>
                <MenuItem disabled={!this.props.deployedAndCurrentProcessDiffer || !this.isRunning() || _.isEmpty(this.props.fetchedProcessDetails.deployedJson)}
                          onSelect={this.showDeployedProcess}>Show deployed process</MenuItem>
                <MenuItem divider />
                <MenuItem onSelect={this.deploy}>Deploy</MenuItem>
                <MenuItem onSelect={this.stop}>Stop</MenuItem>
              </DropdownButton>
              {this.tagsForProcess().map(function (tagi, tagIndex) {
                return <div key={tagIndex} className="tagsBlockVis">{tagi}</div>
              })}
            </div>
            <Graph/>
          </div>
        </div>
      )
    }
  },

  tagsForProcess() {
    var deployedVersionInfo = _.isEqual(this.props.processToDisplay, this.props.fetchedProcessDetails.deployedJson) && this.isRunning() ? ["DEPLOYED"] : []
    var currentVersionInfo = _.isEqual(this.props.processToDisplay, this.props.fetchedProcessDetails.json) ? ["CURRENT"] : []
    return _.concat(deployedVersionInfo, currentVersionInfo, this.props.fetchedProcessDetails.tags)
  }
});

Visualization.title = 'Visualization'
Visualization.path = '/visualization/:processId'
Visualization.header = 'Wizualizacja'


function mapState(state) {
  const processDetails = state.espReducer.fetchedProcessDetails
  const currentAndDeployedProcessEqual = !_.isEmpty(processDetails) && _.isEqual(processDetails.json, processDetails.deployedJson) && !_.isEmpty(processDetails.deployedJson)
  return {
    nodeToDisplay: state.espReducer.nodeToDisplay,
    processToDisplay: state.espReducer.processToDisplay,
    fetchedProcessDetails: processDetails,
    deployedAndCurrentProcessDiffer: !currentAndDeployedProcessEqual,
    history: state.espReducer.history
  };
}

function mapDispatch(dispatch) {
  return {
    actions: bindActionCreators(EspActions, dispatch)
  };
}

export default connect(mapState, mapDispatch)(Visualization);