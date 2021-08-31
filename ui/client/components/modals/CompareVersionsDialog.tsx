/* eslint-disable i18next/no-literal-string */
import {WindowContentProps} from "@touk/window-manager"
import {css, cx} from "emotion"
import _ from "lodash"
import React from "react"
import {connect} from "react-redux"
import {formatAbsolutely} from "../../common/DateUtils"
import * as JsonUtils from "../../common/JsonUtils"
import HttpService from "../../http/HttpService"
import {getProcessId, getProcessVersionId, getVersions} from "../../reducers/selectors/graph"
import {getTargetEnvironmentId} from "../../reducers/selectors/settings"
import "../../stylesheets/visualization.styl"
import {WindowContent} from "../../windowManager"
import EdgeDetailsContent from "../graph/node-modal/edge/EdgeDetailsContent"
import NodeDetailsContent from "../graph/node-modal/NodeDetailsContent"
import {ProcessVersionType} from "../Process/types"
import {SelectWithFocus} from "../withFocus"

interface State {
  currentDiffId: string,
  otherVersion: string,
  remoteVersions: ProcessVersionType[],
  difference: unknown,
}

//TODO: handle displaying groups
//TODO: handle different textarea heights
class VersionsForm extends React.Component<Props, State> {

  //TODO: better way of detecting remote version? also: how to sort versions??
  remotePrefix = "remote-"
  initState: State = {
    otherVersion: null,
    currentDiffId: null,
    difference: null,
    remoteVersions: [],
  }

  state = this.initState

  UNSAFE_componentWillReceiveProps(nextProps: Props) {
    if (nextProps.processId && nextProps.otherEnvironment) {
      HttpService.fetchRemoteVersions(nextProps.processId).then(response => this.setState({remoteVersions: response.data || []}))
    }
  }

  loadVersion(versionId: string) {
    if (versionId) {
      HttpService.compareProcesses(
        this.props.processId,
        this.props.version,
        this.versionToPass(versionId),
        this.isRemote(versionId),
      ).then(
        (response) => this.setState({difference: response.data, otherVersion: versionId, currentDiffId: null}),
      )
    } else {
      this.setState(this.initState)
    }

  }

  isRemote(versionId: string) {
    return versionId.startsWith(this.remotePrefix)
  }

  versionToPass(versionId: string) {
    return versionId.replace(this.remotePrefix, "")
  }

  versionDisplayString(versionId: string) {
    return this.isRemote(versionId) ? `${this.versionToPass(versionId)} on ${this.props.otherEnvironment}` : versionId
  }

  createVersionElement(version: ProcessVersionType, versionPrefix = "") {
    const versionId = versionPrefix + version.processVersionId
    return (
      <option key={versionId} value={versionId}>
        {this.versionDisplayString(versionId)} - created by {version.user} &nbsp; {formatAbsolutely(version.createDate)}</option>
    )
  }

  render() {
    return (
      <>
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
            {this.props.versions.filter(version => this.props.version !== version.processVersionId)
              .map(version => this.createVersionElement(version))}
            {this.state.remoteVersions.map(version => this.createVersionElement(version, this.remotePrefix))}
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
                  this.printDiff(this.state.currentDiffId) :
                  null}
              </div>
            ) :
            null
        }
      </>
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
    return node ?
      (
        <NodeDetailsContent
          isEditMode={false}
          showValidation={false}
          showSwitch={false}
          node={node}
          pathsToMark={pathsToMark}
          onChange={() => {return}}
        />
      ) :
      (<div className="notPresent">Node not present</div>)
  }

  printEdge(edge, pathsToMark) {
    return edge ?
      (
        <EdgeDetailsContent
          edge={edge}
          readOnly={true}
          showValidation={false}
          showSwitch={false}
          changeEdgeTypeValue={() => {return}}
          updateEdgeProp={() => {return}}
          pathsToMark={pathsToMark}
          variableTypes={{}}
        />
      ) :
      (<div className="notPresent">Edge not present</div>)
  }

  printProperties(property, pathsToMark) {
    return property ?
      (
        <NodeDetailsContent
          isEditMode={false}
          showValidation={false}
          showSwitch={false}
          node={property}
          pathsToMark={pathsToMark}
          onChange={() => {return}}
        />
      ) :
      (<div className="notPresent">Properties not present</div>)
  }
}

function mapState(state) {
  return {
    processId: getProcessId(state),
    version: getProcessVersionId(state),
    otherEnvironment: getTargetEnvironmentId(state),
    versions: getVersions(state),
  }
}

type Props = ReturnType<typeof mapState>

//TODO: move to hooks
const CompareVersionsForm = connect(mapState)(VersionsForm)

export function CompareVersionsDialog(props: WindowContentProps): JSX.Element {
  return (
    <WindowContent {...props}>
      <div className={cx("compareModal", "modalContentDark", css({minWidth: 980, padding: "1em"}))}>
        <CompareVersionsForm/>
      </div>
    </WindowContent>
  )
}
