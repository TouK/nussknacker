import React, {PropTypes, Component} from "react";
import {render} from "react-dom";
import {connect} from "react-redux";
import {Tabs, Tab} from "react-bootstrap";
import { Scrollbars } from 'react-custom-scrollbars';
import {browserHistory} from "react-router";
import { Panel } from 'react-bootstrap';
import {bindActionCreators} from "redux";
import Dropzone from "react-dropzone";
import cn from "classnames";

import ActionsUtils from "../../actions/ActionsUtils";
import HttpService from "../../http/HttpService";
import DialogMessages from "../../common/DialogMessages";
import ProcessUtils from "../../common/ProcessUtils";
import SideNodeDetails from "./SideNodeDetails";
import NodeUtils from '../graph/NodeUtils'
import InlinedSvgs from "../../assets/icons/InlinedSvgs"
import Dialogs from "../modals/Dialogs"
import TogglePanel from "../TogglePanel";

import '../../stylesheets/userPanel.styl';

class UserRightPanel extends Component {

  static propTypes = {
    isOpened: React.PropTypes.bool.isRequired,
    graphLayoutFunction: React.PropTypes.func.isRequired,
    layout: React.PropTypes.array.isRequired,
    exportGraph: React.PropTypes.func.isRequired,
    zoomIn: React.PropTypes.func.isRequired,
    zoomOut: React.PropTypes.func.isRequired,
    featuresSettings: React.PropTypes.object.isRequired,
  }

  render() {
    const { isOpened, actions } = this.props;
    const buttonsInBusinessView = [
      "metrics",
      "compare",
      "export",
      "exportPDF",
      "zoomIn",
      "zoomOut",
      "properties",
      "counts",
    ];

    const config = this.getConfig();

    return (
      <div id="espRightNav" className={cn('rightSidenav', { 'is-opened': isOpened })}>
        <TogglePanel type="right" isOpened={isOpened} onToggle={actions.toggleRightPanel}/>
        <Scrollbars renderThumbVertical={props => <div {...props} className="thumbVertical"/>} hideTracksWhenNotNeeded={true}>
          <div className="panel-properties">
            <label>
              <input type="checkbox" onChange={(e) => {
                this.props.actions.businessViewChanged(e.target.checked)
                this.props.actions.fetchProcessToDisplay(this.processId(), this.versionId(), e.target.checked)
              }}/>
              Business view
            </label>
          </div>
          {config.filter(obj => obj).map ((panel, panelIdx) => {
            return (
              <Panel key={panelIdx} collapsible defaultExpanded header={panel.panelName}>
                {panel.buttons.map((panelButton, idx) => this.renderPanelButton(panelButton, idx, buttonsInBusinessView))}
              </Panel>
            )}
          )}
          <Panel collapsible defaultExpanded header="Details">
            <SideNodeDetails/>
          </Panel>
        </Scrollbars>
      </div>
    )
  }

  getConfig = () => {
    const saveDisabled = this.props.nothingToSave && this.props.processIsLatestVersion;
    const deployPossible = this.props.processIsLatestVersion && ProcessUtils.hasNoErrors(this.props.processToDisplay) && this.props.nothingToSave;
    const migratePossible = this.props.processIsLatestVersion && ProcessUtils.hasNoErrors(this.props.processToDisplay) && this.props.nothingToSave;

    return [
      (this.props.isSubprocess ? null : {
        panelName: "Deployment",
        buttons:[
          {name: "deploy", visible: this.props.loggedUser.canDeploy, disabled: !deployPossible, onClick: this.deploy, icon: InlinedSvgs.buttonDeploy},
          {name: "stop", visible: this.props.loggedUser.canDeploy, disabled: !this.isRunning(), onClick: this.stop, icon: InlinedSvgs.buttonStop},
          {name: "metrics", onClick: this.showMetrics, icon: InlinedSvgs.buttonMetrics}
        ]
      }),
      {
      panelName: "Process",
      buttons: [
        {name: "save" + (!saveDisabled ? "*" : ""), visible: this.props.loggedUser.canWrite, disabled: saveDisabled, onClick: this.save, icon: InlinedSvgs.buttonSave},
        {name: "migrate", visible: this.props.loggedUser.canDeploy && !_.isEmpty(this.props.featuresSettings.migration), disabled: !migratePossible, onClick: this.migrate, icon: InlinedSvgs.buttonMigrate},
        {name: "compare", onClick: this.compareVersions, icon: InlinedSvgs.compareButton, disabled: this.hasOneVersion()},
        {name: "import", visible: this.props.loggedUser.canWrite, disabled: false, onClick: this.importProcess, icon: InlinedSvgs.buttonImport, dropzone: true},
        {name: "export", onClick: this.exportProcess, icon: InlinedSvgs.buttonExport},
        {name: "exportPDF", disabled: !this.props.nothingToSave, onClick: this.exportProcessToPdf, icon: InlinedSvgs.buttonExport},
        {name: "zoomIn", onClick: this.props.zoomIn, icon: InlinedSvgs.buttonZoomIn},
        {name: "zoomOut", onClick: this.props.zoomOut, icon: InlinedSvgs.buttonZoomOut}

      ]
    },
      {
        panelName: "Edit",
        buttons: [
          {name: "undo", onClick: this.undo, icon: InlinedSvgs.buttonUndo},
          {name: "redo", onClick: this.redo, icon: InlinedSvgs.buttonRedo},
          {name: "align", onClick: this.props.graphLayoutFunction, icon: InlinedSvgs.buttonAlign, visible: this.props.loggedUser.canWrite},
          {name: "properties", onClick: this.showProperties, icon: InlinedSvgs.buttonSettings, visible: !this.props.isSubprocess},
          {name: "duplicate", onClick: this.duplicateNode, icon: InlinedSvgs.duplicateButton,
            //cloning groups can be tricky...
            disabled: this.noChosenNode(this.props.nodeToDisplay) || NodeUtils.nodeIsGroup(this.props.nodeToDisplay),
            visible: this.props.loggedUser.canWrite},
          {name: "delete", onClick: this.deleteNode, icon: InlinedSvgs.buttonDelete, visible: this.props.loggedUser.canWrite, disabled: this.noChosenNode(this.props.nodeToDisplay) }

        ]
      },
      //TODO: testing subprocesses should work, but currently we don't know how to pass parameters in sane way...
      (this.props.isSubprocess ? null : {
        panelName: "Test",
        buttons: [
          {name: "from file", onClick: this.testProcess, icon: InlinedSvgs.buttonFromFile, dropzone: true,
            disabled: !this.props.testCapabilities.canBeTested},
          {name: "hide", onClick: this.hideRunProcessDetails, icon: InlinedSvgs.buttonHide, disabled: !this.props.showRunProcessDetails},
          {name: "generate", onClick: this.generateData, icon: InlinedSvgs.buttonGenerate,
            disabled: !this.props.processIsLatestVersion || !this.props.testCapabilities.canGenerateTestData},
          {name: "counts", onClick: this.fetchProcessCounts, icon: InlinedSvgs.buttonCounts,
            visible: this.props.featuresSettings.counts && !this.props.isSubprocess},
        ]
      }),
      {
        panelName: "Group",
        buttons: [
          {name: "start", onClick: this.props.actions.startGrouping, icon: InlinedSvgs.buttonGroup, disabled: this.props.groupingState != null},
          {name: "finish", onClick: this.props.actions.finishGrouping, icon: InlinedSvgs.buttonGroup, disabled: (this.props.groupingState || []).length <= 1},
          {name: "cancel", onClick: this.props.actions.cancelGrouping, icon: InlinedSvgs.buttonUngroup, disabled: !this.props.groupingState },
          {name: "ungroup", onClick: this.ungroup, icon: InlinedSvgs.buttonUngroup, disabled: !NodeUtils.nodeIsGroup(this.props.nodeToDisplay) }
        ]
      }
    ];
  }

  renderPanelButton = (panelButton, idx, buttonsInBusinessView) => {
    const buttonClass = "espButton right-panel"
    return panelButton.visible === false || (this.props.businessView === true && !_.includes(buttonsInBusinessView, panelButton.name)) ? null :
      panelButton.dropzone ?
        <Dropzone key={idx} disableClick={panelButton.disabled === true} onDrop={panelButton.onClick}
                  className={"dropZone " + buttonClass + (panelButton.disabled === true ? " disabled" : "")}>
            <div dangerouslySetInnerHTML={{__html: panelButton.icon}} />
            <div>{panelButton.name}</div>
          </Dropzone>
        :
        <button key={idx} type="button" className={buttonClass} disabled={panelButton.disabled === true}
                onClick={panelButton.onClick}>
          <div dangerouslySetInnerHTML={{__html: panelButton.icon}} /> {panelButton.name}
        </button>
  }

  noChosenNode = (node) => {
    return NodeUtils.nodeIsProperties(node) || _.isEmpty(node)
  }

  isRunning = () => this.props.fetchedProcessDetails && (this.props.fetchedProcessDetails.currentlyDeployedAt || []).length > 0

  showProperties = () => {
    this.props.actions.displayModalNodeDetails(this.props.processToDisplay.properties)
  }

  save = () => {
    this.props.actions.toggleModalDialog(Dialogs.types.saveProcess)
  }

  migrate = () => {
    this.props.actions.toggleConfirmDialog(true, DialogMessages.migrate(this.processId(), this.props.featuresSettings.migration.targetEnvironmentId), () => {
      HttpService.migrateProcess(this.processId())
    })
  }

  deploy = () => {
    this.props.actions.toggleConfirmDialog(true, DialogMessages.deploy(this.processId()), () => {
      return HttpService.deploy(this.processId()).then((resp) => {
        this.fetchProcessDetails()
      })
    })
  }

  stop = () => {
    this.props.actions.toggleConfirmDialog(true, DialogMessages.stop(this.processId()), () => {
      return HttpService.stop(this.processId()).then((resp) => {
        this.fetchProcessDetails()
      })
    })
  }

  clearHistory = () => {
    return this.props.undoRedoActions.clear()
  }

  fetchProcessDetails = () => {
    this.props.actions.displayCurrentProcessVersion(this.processId())
  }

  processId = () => {
    return (this.props.processToDisplay || {}).id
  }

  versionId = () => this.props.fetchedProcessDetails.processVersionId

  showMetrics = () => {
    browserHistory.push('/metrics/' + this.processId())
  }

  exportProcess = () => {
    HttpService.exportProcess(this.processId(), this.versionId())
  }

  exportProcessToPdf = () => {
    const data = this.props.exportGraph()
    HttpService.exportProcessToPdf(this.processId(), this.versionId(), data, this.props.businessView)
  }

  generateData = () => {
    this.props.actions.toggleModalDialog(Dialogs.types.generateTestData)
  }

  compareVersions = () => {
    this.props.actions.toggleModalDialog(Dialogs.types.compareVersions)
  }

  fetchProcessCounts = () => {
    this.props.actions.toggleModalDialog(Dialogs.types.calculateCounts)
  }

  importProcess = (files) => {
    files.forEach((file)=>
      this.props.actions.importProcess(this.processId(), file)
    );
  }

  testProcess = (files) => {
    files.forEach((file)=>
      this.props.actions.testProcessFromFile(this.processId(), file, this.props.processToDisplay)
    );
  }

  hideRunProcessDetails = () => {
    this.props.actions.hideRunProcessDetails()
  }

  undo = () => {
    //this `if` should be closer to reducer?
    if (this.props.keyActionsAvailable) {
      this.props.undoRedoActions.undo()
    }
  }

  redo = () => {
    if (this.props.keyActionsAvailable) {
      this.props.undoRedoActions.redo()
    }
  }

  duplicateNode = () => {
    const duplicatedNodePosition = this.props.layout.find(node => node.id === this.props.nodeToDisplay.id) || {position: {x: 0, y: 0}}
    const visiblePosition = {x: duplicatedNodePosition.position.x -200, y: duplicatedNodePosition.position.y}
    this.props.actions.nodeAdded(this.props.nodeToDisplay, visiblePosition)
  }

  deleteNode = () => {
    this.props.actions.deleteNode(this.props.nodeToDisplay.id)
  }

  ungroup = () => {
    this.props.actions.ungroup(this.props.nodeToDisplay)
  }

  hasOneVersion = () => _.get(this.props.fetchedProcessDetails, 'history', []).length <= 1

}

function mapState(state) {
  const fetchedProcessDetails = state.graphReducer.fetchedProcessDetails
  return {
    isOpened: state.ui.rightPanelIsOpened,
    fetchedProcessDetails: fetchedProcessDetails,
    processToDisplay: state.graphReducer.processToDisplay,
    //TODO: now only needed for duplicate, maybe we can do it somehow differently?
    layout: state.graphReducer.layout || [],

    testCapabilities: state.graphReducer.testCapabilities || {},

    loggedUser: state.settings.loggedUser,
    nothingToSave: ProcessUtils.nothingToSave(state),
    showRunProcessDetails: !_.isEmpty(state.graphReducer.testResults) || !_.isEmpty(state.graphReducer.processCounts),
    keyActionsAvailable: state.ui.allModalsClosed,
    processIsLatestVersion: _.get(fetchedProcessDetails, 'isLatestVersion', false),
    nodeToDisplay: state.graphReducer.nodeToDisplay,
    groupingState: state.graphReducer.groupingState,
    featuresSettings: state.settings.featuresSettings,
    isSubprocess: _.get(fetchedProcessDetails, "properties.isSubprocess", false),
    businessView: state.graphReducer.businessView
  };
}


export default connect(mapState, ActionsUtils.mapDispatchWithEspActions)(UserRightPanel);
