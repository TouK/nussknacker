import React from 'react';
import { render } from 'react-dom';
import { Link, withRouter } from 'react-router';
import Graph from '../components/graph/Graph';
import UserRightPanel from '../components/right-panel/UserRightPanel';
import UserLeftPanel from '../components/UserLeftPanel';
import HttpService from '../http/HttpService'
import _ from 'lodash';
import { DropdownButton, MenuItem } from 'react-bootstrap';
import { connect } from 'react-redux';
import ActionsUtils from '../actions/ActionsUtils';
import ProcessUtils from '../common/ProcessUtils';
import DialogMessages from '../common/DialogMessages';
import LoaderSpinner from '../components/Spinner.js';
import '../stylesheets/visualization.styl';
import NodeUtils from '../components/graph/NodeUtils';

const Visualization = withRouter(React.createClass({

  getInitialState: function() {
    return { timeoutId: null, intervalId: null, status: {}};
  },

  componentDidMount() {
    this.fetchProcessDetails().then((details) => {
      this.props.actions.fetchProcessDefinition(
        details.fetchedProcessDetails.processingType, _.get(details, "fetchedProcessDetails.json.properties.isSubprocess")
      ).then(() => {
        this.showModalDetailsIfNeeded(details.fetchedProcessDetails.json)
      })
    })
    this.fetchProcessStatus()
    this.bindKeyboardActions()
    this.props.actions.toggleLeftPanel(true)
    this.bindUnsavedProcessChangesDialog()
  },

  showModalDetailsIfNeeded(process) {
    const urlNodeId = this.props.location.query.nodeId
    const urlEdgeId = this.props.location.query.edgeId
    if (!_.isEmpty(urlNodeId)) {
      this.props.actions.displayModalNodeDetails(NodeUtils.getNodeById(urlNodeId, process))
    }
    if (!_.isEmpty(urlEdgeId)) {
      this.props.actions.displayModalEdgeDetails(NodeUtils.getEdgeById(urlEdgeId, process))
    }
  },

  bindUnsavedProcessChangesDialog() {
    this.props.router.setRouteLeaveHook(this.props.route, (route) => {
      if (!this.props.nothingToSave) { //it should be modal instead of alert here, but how to do this in react-router?
        return DialogMessages.unsavedProcessChanges()
      }
    })
  },

  bindKeyboardActions() {
    window.onkeydown = (event) => {
      if (event.ctrlKey && !event.shiftKey && event.key.toLowerCase() == "z") {
        this.undo()
      }
      if (event.ctrlKey && event.shiftKey && event.key.toLowerCase() == "z") {
        this.redo()
      }
      const deleteKeyCode = 46
      if (event.keyCode == deleteKeyCode && this.props.currentNodeId && this.props.canDelete) {
        this.deleteNode(this.props.currentNodeId)
      }
    }
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
    const details = this.props.actions.displayCurrentProcessVersion(this.props.params.processId)
    this.props.actions.displayProcessActivity(this.props.params.processId)
    return details
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
    //this `if` should be closer to reducer?
    if (this.props.undoRedoAvailable) {
      this.props.undoRedoActions.undo()
    }
  },

  redo() {
    if (this.props.undoRedoAvailable) {
      this.props.undoRedoActions.redo()
    }
  },

  deleteNode(id) {
    if (this.props.canDelete) {
      this.props.actions.deleteNode(id)
    }
  },

  render: function() {
    //it has to be that way, because graph is redux component
    var getGraph = () => this.refs.graph.getWrappedInstance().getDecoratedComponentInstance();
    const graphFun = (fun) => (() => !_.isEmpty(this.refs.graph) ? fun(getGraph()) : () => null)

    const graphLayoutFun = graphFun(graph => graph.directedLayout())
    const exportGraphFun = graphFun(graph => graph.exportGraph())
    const zoomInFun = graphFun(graph => graph.zoomIn())
    const zoomOutFun = graphFun(graph => graph.zoomOut())

    return (
      <div className="Page">
        <div>
          <UserLeftPanel isOpened={this.props.leftPanelIsOpened}/>
          <UserRightPanel isOpened={true} graphLayoutFunction={graphLayoutFun}
                          exportGraph={exportGraphFun} zoomIn={zoomInFun} zoomOut={zoomOutFun}/>
          {(_.isEmpty(this.props.fetchedProcessDetails) || this.props.graphLoading) ? <LoaderSpinner show={true}/> : <Graph ref="graph"/> }

        </div>
      </div>
    )
  },

}));

Visualization.title = 'Visualization'
Visualization.path = '/visualization/:processId'
Visualization.header = 'Wizualizacja'


function mapState(state) {
  return {
    fetchedProcessDetails: state.graphReducer.fetchedProcessDetails,
    currentNodeId: (state.graphReducer.nodeToDisplay || {}).id,
    graphLoading: state.graphReducer.graphLoading,
    leftPanelIsOpened: state.ui.leftPanelIsOpened,
    undoRedoAvailable: state.ui.allModalsClosed,
    nothingToSave: ProcessUtils.nothingToSave(state),
    canDelete: state.ui.allModalsClosed && !NodeUtils.nodeIsGroup(state.graphReducer.nodeToDisplay)
  };
}
export default connect(mapState, ActionsUtils.mapDispatchWithEspActions)(Visualization);