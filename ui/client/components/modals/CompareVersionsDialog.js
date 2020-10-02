import _ from "lodash"
import React from "react"
import Scrollbars from "react-custom-scrollbars"
import {connect} from "react-redux"
import ActionsUtils from "../../actions/ActionsUtils"
import {formatAbsolutely} from "../../common/DateUtils"
import * as JsonUtils from "../../common/JsonUtils"
import HttpService from "../../http/HttpService"
import "../../stylesheets/visualization.styl"
import EdgeDetailsContent from "../graph/node-modal/EdgeDetailsContent"
import NodeDetailsContent from "../graph/node-modal/NodeDetailsContent"
import {SelectWithFocus} from "../withFocus"
import Dialogs from "./Dialogs"
import GenericModalDialog from "./GenericModalDialog"
import {isBusinessView} from "../../reducers/selectors/graph"

//TODO: handle displaying groups
//TODO: handle different textarea heights
class CompareVersionsDialog extends React.Component {

  //TODO: better way of detecting remote version? also: how to sort versions??
  remotePrefix = "remote-"

  constructor(props) {
    super(props)
    this.initState = {
      otherVersion: null,
      currentDiffId: null,
      difference: null,
      remoteVersions: [],
    }
    this.state = this.initState
  }

  componentWillReceiveProps(nextProps) {
    if (nextProps.processId && nextProps.otherEnvironment) {
      HttpService.fetchRemoteVersions(nextProps.processId).then(response => this.setState({remoteVersions: response.data || []}))
    }
  }

  loadVersion(versionId) {
    if (versionId) {
      HttpService.compareProcesses(this.props.processId, this.props.version, this.versionToPass(versionId), this.props.businessView, this.isRemote(versionId)).then(
        (response) => this.setState({difference: response.data, otherVersion: versionId, currentDiffId: null}),
      )
    } else {
      this.setState(this.initState)
    }

  }

  isRemote(versionId) {
    return versionId.startsWith(this.remotePrefix)
  }

  versionToPass(versionId) {
    return versionId.replace(this.remotePrefix, "")
  }

  versionDisplayString(versionId) {
    return this.isRemote(versionId) ? `${this.versionToPass(versionId)} on ${this.props.otherEnvironment}` : versionId
  }

  createVersionElement(version, versionPrefix) {
    const versionId = (versionPrefix || "") + version.processVersionId
    return (
      <option key={versionId} value={versionId}>
        {this.versionDisplayString(versionId)} - created by {version.user} &nbsp; {formatAbsolutely(version.createDate)}</option>
    )
  }

  render() {
    return (
      <GenericModalDialog
        init={() => this.setState(this.initState)}
        header="compare versions"
        type={Dialogs.types.compareVersions}
        style="compareModal"
      >

        <div className="esp-form-row">
          <p>Version to compare</p>
          <SelectWithFocus
            autoFocus={true}
            id="otherVersion"
            className="node-input"
            value={this.state.otherVersion || ""}
            onChange={(e) => this.loadVersion(e.target.value)}
          >
            <option key="" value=""/>
            {this.props.versions.filter(version => this.props.version !== version.processVersionId).map((version, index) => this.createVersionElement(version))}
            {this.state.remoteVersions.map((version, index) => this.createVersionElement(version, this.remotePrefix))}
          </SelectWithFocus>
        </div>
        {
          this.state.otherVersion ?
            (
              <div>
                <div className="esp-form-row">
                  <p>Difference to pick</p>
                  <SelectWithFocus
                    id="otherVersion"
                    className="node-input"
                    value={this.state.currentDiffId || ""}
                    onChange={(e) => this.setState({currentDiffId: e.target.value})}
                  >
                    <option key="" value=""/>
                    {_.keys(this.state.difference).map((diffId) => (
                      <option key={diffId} value={diffId}>{diffId}</option>))}
                  </SelectWithFocus>
                </div>
                {this.state.currentDiffId ?
                  (
                    <Scrollbars
                      hideTracksWhenNotNeeded={true}
                      autoHeightMin={"100px"}
                      autoHeight
                      autoHeightMax={"390px"}
                      renderThumbVertical={props => <div {...props} className="thumbVertical"/>}
                    >
                      {this.printDiff(this.state.currentDiffId)}
                    </Scrollbars>
                  ) : null }
              </div>
            ) : null
        }
      </GenericModalDialog>
    )
  }

  printDiff(diffId) {
    const diff = this.state.difference[diffId]

    switch (diff.type) {
      case "NodeNotPresentInOther":
      case "NodeNotPresentInCurrent":
      case "NodeDifferent":
        return this.renderDiff(diff.currentNode, diff.otherNode, this.printNode)
      case "EdgeNotPresentInCurrent":
      case "EdgeNotPresentInOther":
      case "EdgeDifferent":
        return this.renderDiff(diff.currentEdge, diff.otherEdge, this.printEdge)
      case "PropertiesDifferent":
        return this.renderDiff(diff.currentProperties, diff.otherProperties, this.printProperties)
      default:
        console.error(`Difference type ${diff.type} is not supported`)
    }
  }

  renderDiff(currentElement, otherElement, printElement) {
    const differentPaths = this.differentPathsForObjects(currentElement, otherElement)
    return (
      <div className="compareContainer">
        <div>
          <div className="versionHeader">Current version</div>
          {printElement(currentElement, differentPaths)}
        </div>
        <div>
          <div className="versionHeader">Version {this.versionDisplayString(this.state.otherVersion)}</div>
          {printElement(otherElement, [])}
        </div>
      </div>
    )
  }

  differentPathsForObjects(currentNode, otherNode) {
    const diffObject = JsonUtils.objectDiff(currentNode, otherNode)
    const flattenObj = JsonUtils.flattenObj(diffObject)
    return _.keys(flattenObj)
  }

  printNode(node, pathsToMark) {
    return node ? (
      <NodeDetailsContent
        isEditMode={false}
        showValidation={false}
        showSwitch={false}
        node={node}
        pathsToMark={pathsToMark}
        onChange={() => {}}
      />
    ) :
      (<div className="notPresent">Node not present</div>)
  }

  printEdge(edge, pathsToMark) {
    return edge ? (
      <EdgeDetailsContent
        edge={edge}
        readOnly={true}
        showValidation={false}
        showSwitch={false}
        changeEdgeTypeValue={() => {}}
        updateEdgeProp={() => {}}
        pathsToMark={pathsToMark}
        variableTypes={{}}
      />
    ) :
      (<div className="notPresent">Edge not present</div>)
  }

  printProperties(property, pathsToMark) {
    return property ? (
      <NodeDetailsContent
        isEditMode={false}
        showValidation={false}
        showSwitch={false}
        node={property}
        pathsToMark={pathsToMark}
        onChange={() => {}}
      />
    ) :
      (<div className="notPresent">Properties not present</div>)
  }
}

function mapState(state) {
  return {
    processId: _.get(state.graphReducer, "fetchedProcessDetails.id"),
    version: _.get(state.graphReducer, "fetchedProcessDetails.processVersionId"),
    processDefinitionData: state.settings.processDefinitionData,
    otherEnvironment: _.get(state.settings, "featuresSettings.remoteEnvironment.targetEnvironmentId"),
    versions: _.get(state.graphReducer, "fetchedProcessDetails.history", []),
    businessView: isBusinessView(state),
  }
}

export default connect(mapState, ActionsUtils.mapDispatchWithEspActions)(CompareVersionsDialog)
