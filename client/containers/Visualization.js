import React from 'react';
import { render } from 'react-dom';
import { Link } from 'react-router';
import Graph from '../components/graph/Graph';
import HttpService from '../http/HttpService'
import _ from 'lodash';
import { DropdownButton, MenuItem } from 'react-bootstrap';
import { connect } from 'react-redux';
import { bindActionCreators } from 'redux';
import * as EspActions from '../actions/actions';
import LoaderSpinner from '../components/Spinner.js';
import '../stylesheets/visualization.styl';

const Visualization = React.createClass({

  getInitialState: function() {
    return { timeoutId: null, intervalId: null, status: {}};
  },

  componentDidMount() {
    // this.startPollingForUpdates() fixme pobierac tylko czy job jest uruchomiony
    this.fetchProcessDetails();
    this.fetchProcessStatus();
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
    this.props.actions.clearProcess()
  },

  startPollingForUpdates() {
    var timeoutId = setTimeout(() =>
      this.setState({ intervalId: setInterval(this.fetchProcessDetails, 10000) }),
    2000)
    this.setState({timeoutId: timeoutId})
  },

  fetchProcessDetails() {
    this.props.actions.displayCurrentProcessVersion(this.props.params.processId)
  },

  fetchProcessStatus() {
    HttpService.fetchSingleProcessStatus(this.props.params.processId).then ((status) => {
      this.setState({status: status})
    })
  },

  showProperties() {
    this.props.actions.displayNodeDetails(this.props.processToDisplay.properties)
  },

  deploy() {
    HttpService.deploy(this.props.params.processId).then((resp) => {
      this.fetchProcessDetails()
    })
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
    return _.get(this.state.status, 'isRunning', false)
  },

  undo() {
    return this.props.actions.undo()
  },

  redo() {
    return this.props.actions.redo()
  },

  clearHistory() {
    return this.props.actions.clear()
  },

  save() {
    return HttpService.saveProcess(this.props.params.processId, this.props.processToDisplay).then ((resp) => {
      this.clearHistory()
      this.fetchProcessDetails()
    })
  },

  render: function() {
    if (_.isEmpty(this.props.fetchedProcessDetails) || this.props.graphLoading) {
      return (<LoaderSpinner show={true}/>)
    } else {
      const deployedVersionIsDisplayed = _.isEqual(this.props.processToDisplay, this.props.fetchedProcessDetails.deployedJson) //fixme blokowanie edycji dla zdeplojowanej wersji
      const nothingToSave = _.isEqual(this.props.fetchedProcessDetails.json, this.props.processToDisplay) || deployedVersionIsDisplayed
      const deployButtons = this.props.loggedUser.canDeploy ? ([
                      <MenuItem divider key="0"/>,
                      <MenuItem onSelect={this.deploy} key="1">Deploy</MenuItem>,
                      <MenuItem onSelect={this.stop} key="2">Stop</MenuItem>
     ]) : null;
      const saveButton = this.props.loggedUser.canWrite ? (
        <button type="button" className="btn btn-default" disabled={nothingToSave} onClick={this.save}>SAVE{nothingToSave? "" : "*"}</button>

      ) : null;

      const editButtons = this.props.loggedUser.canWrite ? ([
          <MenuItem key="3" onSelect={() => getGraph().directedLayout()}>Layout</MenuItem>,
          <MenuItem key="4" onSelect={() => getGraph().addFilter()}>Add filter</MenuItem>
      ]) : null;

      //niestety tak musi byc, bo graph jest reduxowym komponentem
      var getGraph = () => this.refs.graph.getWrappedInstance();

      return (
        <div className="Page">

          <div>
            <div id="esp-action-panel">
              {saveButton}
              {this.props.deployedAndCurrentProcessDiffer ? <span title="Current version differs from deployed one" className="glyphicon glyphicon-warning-sign tag-warning"/> : null}
              <DropdownButton bsStyle="default" pullRight title="Action" id="actionDropdown">
                {editButtons}
                <MenuItem key="6" onSelect={this.showProperties}>Properties</MenuItem>
                <MenuItem key="7" disabled={!this.props.deployedAndCurrentProcessDiffer}
                          onSelect={this.showCurrentProcess}>Show current process</MenuItem>
                <MenuItem key="8" disabled={!this.props.deployedAndCurrentProcessDiffer || !this.isRunning() || _.isEmpty(this.props.fetchedProcessDetails.deployedJson)}
                          onSelect={this.showDeployedProcess}>Show deployed process</MenuItem>

                {deployButtons}

              </DropdownButton>
              {this.tagsForProcess().map(function (tagi, tagIndex) {
                return <div key={tagIndex} className="tagsBlockVis">{tagi}</div>
              })}
            </div>
            <Graph ref="graph"/>
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
    graphLoading: state.espReducer.graphLoading,
    history: state.espReducer.history,
    loggedUser: state.espReducer.loggedUser
  };
}

function mapDispatch(dispatch) {
  return {
    actions: bindActionCreators(EspActions, dispatch)
  };
}

export default connect(mapState, mapDispatch)(Visualization);