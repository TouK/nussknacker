import React, {Component} from "react";
import {connect} from "react-redux";
import {Panel} from "react-bootstrap";
import {Scrollbars} from "react-custom-scrollbars";
import history from "../../history"
import cn from "classnames";

import ActionsUtils from "../../actions/ActionsUtils";
import HttpService from "../../http/HttpService";
import DialogMessages from "../../common/DialogMessages";
import ProcessUtils from "../../common/ProcessUtils";
import SideNodeDetails from "./SideNodeDetails";
import NodeUtils from "../graph/NodeUtils"
import InlinedSvgs from "../../assets/icons/InlinedSvgs"
import Dialogs from "../modals/Dialogs"
import TogglePanel from "../TogglePanel";
import SvgDiv from "../SvgDiv"

import "../../stylesheets/userPanel.styl";
import Archive from "../../containers/Archive";
import SpinnerWrapper from "../SpinnerWrapper";
import PropTypes from "prop-types";
import Dropzone from "react-dropzone"
import {events} from "../../analytics/TrackingEvents"
import ProcessStateUtils from "../../common/ProcessStateUtils";

class UserRightPanel extends Component {

  static propTypes = {
    isOpened: PropTypes.bool.isRequired,
    graphLayoutFunction: PropTypes.func.isRequired,
    layout: PropTypes.array.isRequired,
    exportGraph: PropTypes.func.isRequired,
    zoomIn: PropTypes.func.isRequired,
    zoomOut: PropTypes.func.isRequired,
    featuresSettings: PropTypes.object.isRequired,
    isReady: PropTypes.bool.isRequired,
    selectionActions: PropTypes.shape({
      copy: PropTypes.func.isRequired,
      canCopy: PropTypes.bool.isRequired,
      cut: PropTypes.func.isRequired,
      canCut: PropTypes.bool.isRequired,
      paste: PropTypes.func.isRequired,
      canPaste: PropTypes.bool.isRequired
    }).isRequired
  };

  render() {
    const {isOpened, actions, isReady} = this.props;
    const config = this.getConfig();

    return (
      <div id="espRightNav" className={cn("rightSidenav", {"is-opened": isOpened})}>
        <div className={cn("zoom-in-out", "right", {"is-opened": isOpened})}>
          <SvgDiv className={"zoom"} title={"zoom-in"} svgFile={"buttons/zoomin.svg"} onClick={this.props.zoomIn}/>
          <SvgDiv className={"zoom"} title={"zoom-out"} svgFile={"buttons/zoomout.svg"} onClick={this.props.zoomOut}/>
        </div>
        <SpinnerWrapper isReady={isReady}>
          <Scrollbars renderThumbVertical={props => <div {...props} className="thumbVertical"/>} hideTracksWhenNotNeeded={true}>
            <div className="panel-properties">
              <label>
                  <input disabled={!this.props.nothingToSave} type="checkbox" defaultChecked={this.props.businessView} onChange={(e) => {
                    this.props.actions.businessViewChanged(e.target.checked)
                    this.props.actions.fetchProcessToDisplay(this.processId(), this.versionId(), e.target.checked)
                  }}/>
                Business view
              </label>
            </div>
            {config.filter(panel => panel).map ((panel, panelIdx) => {
                const visibleButtons = panel.buttons.filter(button => button.visible !== false)
                return _.isEmpty(visibleButtons) ? null : (
                  <Panel key={panelIdx} defaultExpanded>
                    <Panel.Heading><Panel.Title toggle>{panel.panelName}</Panel.Title></Panel.Heading>
                    <Panel.Collapse>
                      <Panel.Body>
                        {visibleButtons.map((panelButton, idx) => this.renderPanelButton(panelButton, idx))}
                      </Panel.Body>
                    </Panel.Collapse>
                  </Panel>
                )
              }
            )}
            {this.props.capabilities.write ? //TODO remove SideNodeDetails? turn out to be not useful
              (<Panel defaultExpanded>
                <Panel.Heading><Panel.Title toggle>Details</Panel.Title></Panel.Heading>
                <Panel.Collapse>
                  <Panel.Body><SideNodeDetails/></Panel.Body>
                </Panel.Collapse>
              </Panel>) : null
            }
          </Scrollbars>
        </SpinnerWrapper>
        <TogglePanel type="right" isOpened={isOpened} onToggle={actions.toggleRightPanel}/>
      </div>
    )
  }

  getConfigProperties = () => {
    const saveDisabled = this.props.nothingToSave && this.props.processIsLatestVersion;
    const hasErrors = !ProcessUtils.hasNoErrors(this.props.processToDisplay)
    const deployPossible = this.props.processIsLatestVersion && !hasErrors && this.props.nothingToSave;

    let deployToolTip, deployMouseOut, deployMouseOver
    if (hasErrors) {
      deployToolTip = "Cannot deploy due to errors. Please look at the left panel for more details."
      deployMouseOver = this.props.actions.enableToolTipsHighlight
      deployMouseOut = this.props.actions.disableToolTipsHighlight
    }  else if (!saveDisabled) {
      deployToolTip = "You have unsaved changes."
    }

    let propertiesBtnClass
    if (hasErrors && !ProcessUtils.hasNoPropertiesErrors(this.props.processToDisplay)) {
      propertiesBtnClass =  "esp-button-error right-panel"
    }

    return ({
      deployMouseOut: deployMouseOut,
      deployMouseOver: deployMouseOver,
      deployPossible: deployPossible,
      deployToolTip: deployToolTip,
      propertiesBtnClass: propertiesBtnClass,
      saveDisabled: saveDisabled
    })
  }

  getConfig = () => {
    const conf = this.getConfigProperties()

    return [
      (this.props.isSubprocess ? null : {
        panelName: "Deployment",
        buttons:[
          {name: "deploy", visible: this.props.capabilities.deploy, disabled: !conf.deployPossible, icon: InlinedSvgs.buttonDeploy, btnTitle: conf.deployToolTip, onClick: this.deploy, onMouseOver: conf.deployMouseOver, onMouseOut: conf.deployMouseOut},
          {name: "cancel", visible: this.props.capabilities.deploy, disabled: !this.isRunning(), onClick: this.cancel, icon: InlinedSvgs.buttonCancel},
          {name: "metrics", onClick: this.showMetrics, icon: InlinedSvgs.buttonMetrics}
        ]
      }),
      {
        panelName: "Process",
        buttons: [
          {
            name: `save${  !conf.saveDisabled ? "*" : ""}`,
            visible: this.props.capabilities.write,
            disabled: conf.saveDisabled,
            onClick: this.save,
            icon: InlinedSvgs.buttonSave
          },
          {
            name: "migrate",
            visible: this.props.capabilities.deploy && !_.isEmpty(this.props.featuresSettings.remoteEnvironment),
            disabled: !conf.deployPossible,
            onClick: this.migrate,
            icon: InlinedSvgs.buttonMigrate
          },
          {name: "compare", onClick: this.compareVersions, icon: "compare.svg", disabled: this.hasOneVersion()},
          {
            name: "import",
            visible: this.props.capabilities.write,
            disabled: false,
            onClick: (_) => this.props.actions.reportEvent({
              category: events.categories.rightPanel,
              action: events.actions.buttonClick,
              name: "import"
            }),
            onDrop: this.importFiles,
            icon: InlinedSvgs.buttonImport,
            dropzone: true
          },
          {name: "JSON", disabled: !this.props.canExport, onClick: this.exportProcess, icon: InlinedSvgs.buttonExport},
          {name: "PDF", disabled: !this.props.canExport, onClick: this.exportProcessToPdf, icon: InlinedSvgs.pdf},
          {
            name: "archive",
            onClick: this.archiveProcess,
            disabled: this.isRunning(),
            icon: "archive.svg",
            visible: this.props.capabilities.write
          }
        ]
      },
      {
        panelName: "Edit",
        buttons: [
          {name: "undo",
            visible: this.props.capabilities.write,
            disabled: this.props.history.past.length === 0,
            onClick: this.undo,
            icon: InlinedSvgs.buttonUndo
          },
          {
            name: "redo",
            visible: this.props.capabilities.write,
            disabled: this.props.history.future.length === 0,
            onClick: this.redo,
            icon: InlinedSvgs.buttonRedo
          },
          {
            name: "layout",
            onClick: () => this.props.actions.layout(this.props.graphLayoutFunction),
            icon: InlinedSvgs.buttonLayout,
            visible: this.props.capabilities.write
          },
          {
            name: "properties",
            className: conf.propertiesBtnClass,
            onClick: this.showProperties,
            icon: InlinedSvgs.buttonSettings,
            visible: !this.props.isSubprocess
          },
          {
            name: "copy",
            onClick: (event) => this.props.actions.copySelection(
              () => this.props.selectionActions.copy(event),
              {category: events.categories.rightPanel, action: events.actions.buttonClick}
            ),
            icon: "copy.svg",
            visible: this.props.capabilities.write,
            disabled: !this.props.selectionActions.canCopy
          },
          {
            name: "cut",
            onClick: (event) => this.props.actions.cutSelection(
              () => this.props.selectionActions.cut(event),
              {category: events.categories.rightPanel, action: events.actions.buttonClick}
            ),
            icon: "cut.svg",
            visible: this.props.capabilities.write,
            disabled: !this.props.selectionActions.canCut
          },
          {
            name: "delete",
            onClick: (event) => this.props.actions.deleteSelection(
              this.props.selectionState,
              {category: events.categories.rightPanel, action: events.actions.buttonClick}
            ),
            icon: "delete.svg",
            visible: this.props.capabilities.write,
            disabled: !NodeUtils.isPlainNode(this.props.nodeToDisplay) || _.isEmpty(this.props.selectionState)
          },
          {
            name: "paste",
            onClick: (event) => this.props.actions.pasteSelection(
              () => this.props.selectionActions.paste(event),
              {category: events.categories.rightPanel, action: events.actions.buttonClick}
            ),
            icon: "paste.svg",
            visible: this.props.capabilities.write,
            disabled: !this.props.selectionActions.canPaste
          }
        ]
      },
      //TODO: testing subprocesses should work, but currently we don't know how to pass parameters in sane way...
      (this.props.isSubprocess ? null : {
        panelName: "Test",
        buttons: [
          {
            name: "from file",
            onDrop: this.testProcess,
            onClick: (_) => this.props.actions.reportEvent({
              category: events.categories.rightPanel,
              action: events.actions.buttonClick,
              name: "from file"
            }),
            icon: InlinedSvgs.buttonFromFile,
            dropzone: true,
            disabled: !this.props.testCapabilities.canBeTested,
            visible: this.props.capabilities.write
          },
          {
            name: "hide",
            onClick: this.hideRunProcessDetails,
            icon: InlinedSvgs.buttonHide,
            disabled: !this.props.showRunProcessDetails,
            visible: this.props.capabilities.write
          },
          {
            name: "generate",
            onClick: this.generateData,
            icon: "generate.svg",
            disabled: !this.props.processIsLatestVersion || !this.props.testCapabilities.canGenerateTestData,
            visible: this.props.capabilities.write
          },
//TODO: counts and metrics should not be visible in archived process
          {
            name: "counts", onClick: this.fetchProcessCounts, icon: "counts.svg",
            visible: this.props.featuresSettings.counts && !this.props.isSubprocess
          },
        ]
      }),
      {
        panelName: "Group",
        buttons: [
          {
            name: "start",
            onClick: this.props.actions.startGrouping,
            icon: InlinedSvgs.buttonGroup,
            disabled: this.props.groupingState != null,
            visible: this.props.capabilities.write
          },
          {
            name: "finish",
            onClick: this.props.actions.finishGrouping,
            icon: InlinedSvgs.buttonGroup,
            disabled: (this.props.groupingState || []).length <= 1,
            visible: this.props.capabilities.write
          },
          {
            name: "cancel",
            onClick: this.props.actions.cancelGrouping,
            icon: InlinedSvgs.buttonUngroup,
            disabled: !this.props.groupingState,
            visible: this.props.capabilities.write
          },
          {
            name: "ungroup",
            onClick: (event) => this.props.actions.ungroup(this.props.nodeToDisplay),
            icon: InlinedSvgs.buttonUngroup,
            disabled: !NodeUtils.nodeIsGroup(this.props.nodeToDisplay),
            visible: this.props.capabilities.write
          }
        ]
      }
    ];
  }

  renderPanelButton = (panelButton, idx) => {
    const buttonClass = panelButton.className || "espButton right-panel"
    //TODO: move other buttons from inlined svgs to files
    const toolTip = panelButton.btnTitle || panelButton.name
    const svgDiv = panelButton.icon.endsWith(".svg")
                 ? (<SvgDiv title={toolTip} svgFile={`buttons/${panelButton.icon}`}/>)
                 : ( <div title={toolTip} dangerouslySetInnerHTML={{__html: panelButton.icon}} />)

    return (
        panelButton.dropzone ?
        <Dropzone
          key={idx}
          title={toolTip}
          disableClick={panelButton.disabled === true}
          onDrop={panelButton.onDrop}
          onMouseOver={panelButton.onMouseOver}
          onMouseOut={panelButton.onMouseOut}
        >
          {({getRootProps, getInputProps}) => (
            <div {
                   ...getRootProps({
                     className: `dropZone ${  buttonClass  }${panelButton.disabled === true ? " disabled" : ""}`,
                     onClick: panelButton.onClick
                   })} >
              {svgDiv}
              <input {...getInputProps()} />
              <div>{panelButton.name}</div>
            </div>
          )}
        </Dropzone>
        :
        <button
            key={idx}
            type="button"
            className={buttonClass}
            disabled={panelButton.disabled === true}
            title={toolTip}
            onClick={panelButton.onClick}
            onMouseOver={panelButton.onMouseOver}
            onMouseOut={panelButton.onMouseOut}
        >
          {svgDiv}
          <div>{panelButton.name}</div>
        </button>
    )
  }

  isRunning = () => ProcessStateUtils.isDeployed(this.props.fetchedProcessDetails)

  showProperties = () => {
    this.props.actions.displayModalNodeDetails(
        this.props.processToDisplay.properties,
        undefined,
        {category: events.categories.rightPanel, name: "properties"}
      )
  }

  save = () => {
    this.props.actions.toggleModalDialog(Dialogs.types.saveProcess)
  }

  migrate = () => {
    this.props.actions.toggleConfirmDialog(
      true,
      DialogMessages.migrate(this.processId(), this.props.featuresSettings.remoteEnvironment.targetEnvironmentId),
      () => {
        HttpService.migrateProcess(this.processId(), this.versionId())
      },
      "Yes",
      "No",
      {category: events.categories.rightPanel, action: events.actions.buttonClick, name: "migrate"}
    )
  }

  deploy = () => {
    this.props.actions.toggleProcessActionDialog("Deploy process", (p, c) => HttpService.deploy(p, c), true)
  }

  cancel = () => {
    this.props.actions.toggleProcessActionDialog("Cancel process", (p, c) => HttpService.cancel(p, c), false)
  }

  clearHistory = () => {
    return this.props.undoRedoActions.clear()
  }

  fetchProcessDetails = () => {
    this.props.actions.displayCurrentProcessVersion(this.processId())
  }

  processId = () => {
    return this.props.fetchedProcessDetails.name
  }

  versionId = () => this.props.fetchedProcessDetails.processVersionId

  showMetrics = () => {
    this.props.actions.showMetrics(this.processId())
  }

  exportProcess = () => {
    this.props.actions.exportProcessToJSON(this.props.processToDisplay, this.versionId())
  }

  exportProcessToPdf = () => {
    this.props.actions.exportProcessToPdf(this.processId(), this.versionId(), this.props.exportGraph(), this.props.businessView)
  }

  archiveProcess = () => {
    if (!this.isRunning()) {
      this.props.actions.toggleConfirmDialog(
        true,
        DialogMessages.archiveProcess(this.processId()),
        () => {
          return HttpService.archiveProcess(this.processId()).then((response) => history.push(Archive.path))
        },
        "Yes",
        "No",
        {category: events.categories.rightPanel, action: events.actions.buttonClick, name: "archive"}
      )
    }
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

  importFiles = (files) => {
    this.props.actions.importFiles(files, this.processId())
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
      this.props.undoRedoActions.undo(
        {category: events.categories.rightPanel, action: events.actions.buttonClick}
      )
    }
  }

  redo = () => {
    if (this.props.keyActionsAvailable) {
      this.props.undoRedoActions.redo(
        {category: events.categories.rightPanel, action: events.actions.buttonClick}
      )
    }
  }

  hasOneVersion = () => _.get(this.props.fetchedProcessDetails, "history", []).length <= 1

}

function mapState(state) {
  const fetchedProcessDetails = state.graphReducer.fetchedProcessDetails
  return {
    isOpened: state.ui.rightPanelIsOpened,
    fetchedProcessDetails: fetchedProcessDetails,
    processToDisplay: state.graphReducer.processToDisplay || {},
    //TODO: now only needed for duplicate, maybe we can do it somehow differently?
    layout: state.graphReducer.layout || [],

    testCapabilities: state.graphReducer.testCapabilities || {},

    loggedUser: state.settings.loggedUser,
    nothingToSave: ProcessUtils.nothingToSave(state),
    canExport: ProcessUtils.canExport(state),
    showRunProcessDetails: !_.isEmpty(state.graphReducer.testResults) || !_.isEmpty(state.graphReducer.processCounts),
    keyActionsAvailable: state.ui.allModalsClosed,
    processIsLatestVersion: _.get(fetchedProcessDetails, "isLatestVersion", false),
    nodeToDisplay: state.graphReducer.nodeToDisplay,
    groupingState: state.graphReducer.groupingState,
    selectionState: state.graphReducer.selectionState,
    featuresSettings: state.settings.featuresSettings,
    isSubprocess: _.get(state.graphReducer.processToDisplay, "properties.isSubprocess", false),
    businessView: state.graphReducer.businessView,
    history: state.graphReducer.history
  };
}


export default connect(mapState, ActionsUtils.mapDispatchWithEspActions)(UserRightPanel);
