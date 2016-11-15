import React from 'react';
import { render } from 'react-dom';
import { Link } from 'react-router';
import Graph from '../components/graph/Graph';
import UserRightPanel from '../components/right-panel/UserRightPanel';
import UserLeftPanel from '../components/UserLeftPanel';
import HttpService from '../http/HttpService'
import _ from 'lodash';
import { DropdownButton, MenuItem } from 'react-bootstrap';
import { connect } from 'react-redux';
import ActionsUtils from '../actions/ActionsUtils';
import LoaderSpinner from '../components/Spinner.js';
import '../stylesheets/visualization.styl';

const Visualization = React.createClass({

  getInitialState: function() {
    return { timeoutId: null, intervalId: null, status: {}};
  },

  componentDidMount() {
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
    this.props.actions.toggleLeftPanel(true)
  },

  componentWillUnmount() {
    clearTimeout(this.state.timeoutId)
    clearInterval(this.state.intervalId)
    this.props.actions.clearProcess()
    this.props.actions.toggleLeftPanel(false)
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

  isRunning() {
    return _.get(this.state.status, 'isRunning', false)
  },

  undo() {
    //ten if moze powinien byc blisko reducera, tylko jak to ladnie zrobic?
    if (this.props.undoRedoAvailable) {
      this.props.actions.undo()
    }
  },

  redo() {
    if (this.props.undoRedoAvailable) {
      this.props.actions.redo()
    }
  },

  render: function() {
    //niestety tak musi byc, bo graph jest reduxowym komponentem
    var getGraph = () => this.refs.graph.getWrappedInstance().getDecoratedComponentInstance();
    const graphLayoutFun = () => {
      return !_.isEmpty(this.refs.graph) ? getGraph().directedLayout() : () => null
    }
    return (
      <div className="Page">
        <div>
          <UserLeftPanel isOpened={this.props.leftPanelIsOpened}/>
          <UserRightPanel isOpened={true} graphLayout={graphLayoutFun}/>
          {(_.isEmpty(this.props.fetchedProcessDetails) || this.props.graphLoading) ? <LoaderSpinner show={true}/> : <Graph ref="graph"/> }

        </div>
      </div>
    )
  },

});

Visualization.title = 'Visualization'
Visualization.path = '/visualization/:processId'
Visualization.header = 'Wizualizacja'


function mapState(state) {
  return {
    fetchedProcessDetails: state.graphReducer.fetchedProcessDetails,
    graphLoading: state.graphReducer.graphLoading,
    leftPanelIsOpened: state.ui.leftPanelIsOpened,
    undoRedoAvailable: !state.ui.showNodeDetailsModal
  };
}
export default connect(mapState, ActionsUtils.mapDispatchWithEspActions)(Visualization);