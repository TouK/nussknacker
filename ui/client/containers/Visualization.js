import React from 'react';
import {Prompt} from 'react-router-dom';
import Graph from '../components/graph/Graph';
import UserRightPanel from '../components/right-panel/UserRightPanel';
import UserLeftPanel from '../components/UserLeftPanel';
import HttpService from '../http/HttpService'
import _ from 'lodash';
import {connect} from 'react-redux';
import ActionsUtils from '../actions/ActionsUtils';
import ProcessUtils from '../common/ProcessUtils';
import DialogMessages from '../common/DialogMessages';
import '../stylesheets/visualization.styl';
import NodeUtils from '../components/graph/NodeUtils';
import * as VisualizationUrl from '../common/VisualizationUrl'
import SpinnerWrapper from "../components/SpinnerWrapper";

class Visualization extends React.Component {

  constructor(props) {
    super(props);
    this.state = {timeoutId: null, intervalId: null, status: {}, isArchived: null, dataResolved: false};
    this.graphRef = React.createRef()
  }

  componentDidMount() {
    const businessView = VisualizationUrl.extractBusinessViewParams(this.props.location.search)
    this.setBusinessView(businessView)
    this.fetchProcessDetails(businessView).then((details) => {
      this.props.actions.displayProcessActivity(this.props.match.params.processId)
      this.props.actions.fetchProcessDefinition(
        details.fetchedProcessDetails.processingType,
        _.get(details, "fetchedProcessDetails.json.properties.isSubprocess"),
        this.props.subprocessVersions
      ).then(() => {
        this.setState({isArchived: _.get(details, "fetchedProcessDetails.isArchived"), dataResolved: true})
        this.showModalDetailsIfNeeded(details.fetchedProcessDetails.json);
        this.showCountsIfNeeded(details.fetchedProcessDetails.json);
      })

      this.fetchProcessStatus()
    }).catch((error) => {
      this.props.actions.handleHTTPError(error)
    })

    this.bindKeyboardActions()
  }

  showModalDetailsIfNeeded(process) {
    const {nodeId, edgeId} = VisualizationUrl.extractVisualizationParams(this.props.location.search)
    if (nodeId) {
      const node = NodeUtils.getNodeById(nodeId, process)

      if (node) {
        this.props.actions.displayModalNodeDetails(node)
      } else {
        this.props.history.replace({search: VisualizationUrl.setAndPreserveLocationParams({nodeId: null})})
      }
    }

    if (edgeId) {
      const edge = NodeUtils.getEdgeById(edgeId, process)
      if (edge) {
        this.props.actions.displayModalEdgeDetails(edge)
      } else {
        this.props.history.replace({search: VisualizationUrl.setAndPreserveLocationParams({edgeId: null})})
      }
    }
  }

  setBusinessView(businessView) {
    if (businessView != null) {
      this.props.actions.businessViewChanged(businessView)
    }
  }

  showCountsIfNeeded(process) {
    const countParams = VisualizationUrl.extractCountParams(this.props.location.search);
    if (countParams) {
      const {from, to} = countParams;
      this.props.actions.fetchAndDisplayProcessCounts(process.id, from, to);
    }
  }

  bindKeyboardActions() {
    window.onkeydown = (event) => {
      if (event.ctrlKey && !event.shiftKey && event.key.toLowerCase() == "z") {
        this.undo()
      }
      if (event.ctrlKey && event.shiftKey && event.key.toLowerCase() == "z") {
        this.redo()
      }

      if (event.key === 'Delete' && !_.isEmpty(this.props.selectionState) && this.props.canDelete) {
        this.deleteSelection()
      }
    }
  }

  componentWillUnmount() {
    clearTimeout(this.state.timeoutId)
    clearInterval(this.state.intervalId)
    this.props.actions.clearProcess()
  }

  fetchProcessDetails(businessView) {
    const details = this.props.actions.fetchProcessToDisplay(this.props.match.params.processId, undefined, businessView)
    return details
  }

  fetchProcessStatus() {
    HttpService.fetchSingleProcessStatus(this.props.match.params.processId).then((response) => {
      this.setState({status: response})
    })
  }

  isRunning() {
    return _.get(this.state.status, 'isRunning', false)
  }

  undo() {
    //this `if` should be closer to reducer?
    if (this.props.undoRedoAvailable) {
      this.props.undoRedoActions.undo()
    }
  }

  redo() {
    if (this.props.undoRedoAvailable) {
      this.props.undoRedoActions.redo()
    }
  }

  deleteSelection() {
    this.props.actions.deleteNodes(this.props.selectionState)
  }

  render() {
    const {leftPanelIsOpened, actions, loggedUser} = this.props;

    //it has to be that way, because graph is redux component
    const getGraph = () => this.graphRef.current.getDecoratedComponentInstance()
    const graphLayoutFun = () => getGraph().directedLayout()
    const exportGraphFun = () => getGraph().exportGraph()
    const zoomOutFun = () => getGraph().zoomOut()
    const zoomInFun = () => getGraph().zoomIn()

    const capabilities = {
      write: loggedUser.canWrite(this.props.processCategory) && !this.state.isArchived,
      deploy: loggedUser.canDeploy(this.props.processCategory) && !this.state.isArchived,
    };

    const graphNotReady = _.isEmpty(this.props.fetchedProcessDetails) || this.props.graphLoading;

    return (
      <div className="Page">
        <Prompt
          when={!this.props.nothingToSave}
          message={(location, action) => action === 'PUSH' ? DialogMessages.unsavedProcessChanges() : true}
        />

        <UserLeftPanel
          isOpened={leftPanelIsOpened}
          onToggle={actions.toggleLeftPanel}
          loggedUser={loggedUser}
          capabilities={capabilities}
          isReady={this.state.dataResolved}
        />

        <UserRightPanel
          graphLayoutFunction={graphLayoutFun}
          exportGraph={exportGraphFun}
          zoomIn={zoomInFun}
          zoomOut={zoomOutFun}
          capabilities={capabilities}
          isReady={this.state.dataResolved}
        />

        <SpinnerWrapper isReady={!graphNotReady}>
          <Graph ref={this.graphRef} capabilities={capabilities}/>
        </SpinnerWrapper>
      </div>
    );
  }
}

Visualization.path = VisualizationUrl.visualizationPath
Visualization.header = 'Visualization'

function mapState(state) {
  const processCategory = _.get(state, 'graphReducer.fetchedProcessDetails.processCategory');
  const canDelete = state.ui.allModalsClosed
    && !NodeUtils.nodeIsGroup(state.graphReducer.nodeToDisplay)
    && state.settings.loggedUser.canWrite(processCategory);
  return {
    processCategory: processCategory,
    selectionState: state.graphReducer.selectionState,
    canDelete: canDelete,
    fetchedProcessDetails: state.graphReducer.fetchedProcessDetails,
    subprocessVersions: _.get(state.graphReducer.processToDisplay, "properties.subprocessVersions"),
    currentNodeId: (state.graphReducer.nodeToDisplay || {}).id,
    graphLoading: state.graphReducer.graphLoading,
    leftPanelIsOpened: state.ui.leftPanelIsOpened,
    undoRedoAvailable: state.ui.allModalsClosed,
    nothingToSave: ProcessUtils.nothingToSave(state),
    loggedUser: state.settings.loggedUser
  };
}

export default connect(mapState, ActionsUtils.mapDispatchWithEspActions)(Visualization);
