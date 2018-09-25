import React from "react";
import {render} from "react-dom";
import {connect} from "react-redux";
import _ from "lodash";
import ActionsUtils from "../../actions/ActionsUtils";
import "../../stylesheets/visualization.styl";
import GenericModalDialog from "./GenericModalDialog";
import Dialogs from "./Dialogs";
import HttpService from "../../http/HttpService";
import * as JsonUtils from "../../common/JsonUtils";
import {Table, Td, Tr} from "reactable";
import NodeDetailsContent from "../graph/NodeDetailsContent";
import Moment from "moment";

import Scrollbars from "react-custom-scrollbars";

//TODO: handle displaying groups
//TODO: handle different textarea heights
class CompareVersionsDialog extends React.Component {

  //TODO: better way of detecting remote version? also: how to sort versions??
  remotePrefix = "remote-"

  constructor(props) {
    super(props);
    this.initState = {
      otherVersion: null,
      currentDiffId: null,
      difference: null,
      remoteVersions: []
    };
    this.state = this.initState
  }

  componentWillReceiveProps(nextProps) {
    if (nextProps.processId && nextProps.otherEnvironment) {
      HttpService.fetchRemoteVersions(nextProps.processId).then(list => this.setState({remoteVersions: list || []}))
    }
  }

  loadVersion(versionId) {
    if (versionId) {
      HttpService.compareProcesses(this.props.processId, this.props.version, this.versionToPass(versionId), this.props.businessView, this.isRemote(versionId))
        .then((difference) => this.setState({difference: difference, otherVersion: versionId, currentDiffId: null}))
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
    return this.isRemote(versionId) ? `${this.versionToPass(versionId)} on ${this.props.otherEnvironment}` : versionId;
  }

  createVersionElement(version, versionPrefix) {
    const versionId = (versionPrefix || '') + version.processVersionId
    return (
      <option key={versionId} value={versionId}>
        {this.versionDisplayString(versionId)} - created by {version.user} &nbsp;on {Moment(version.createDate).format("YYYY-MM-DD HH:mm:ss")}</option>)
  }

  render() {
    return (
      <GenericModalDialog init={() => this.setState(this.initState)} header="Compare versions"
                          type={Dialogs.types.compareVersions} style="compareModal">

        <div className="esp-form-row">
          <p>Version to compare</p>
          <select id="otherVersion" className="node-input" value={this.state.otherVersion || ''}
                  onChange={(e) => this.loadVersion(e.target.value)}>
            <option key="" value=""/>
            {this.props.versions.filter(version => this.props.version !== version.processVersionId).map((version, index) => this.createVersionElement(version))}
            {this.state.remoteVersions.map((version, index) => this.createVersionElement(version, this.remotePrefix))}
          </select>
        </div>
        {
          this.state.otherVersion ?
            (
              <div>
                <div className="esp-form-row">
                  <p>Difference to pick</p>
                  <select id="otherVersion" className="node-input"
                          value={this.state.currentDiffId || ''}
                          onChange={(e) => this.setState({currentDiffId: e.target.value})}>
                    <option key="" value=""/>
                    {_.keys(this.state.difference).map((diffId) => (
                      <option key={diffId} value={diffId}>{diffId}</option>))}
                  </select>
                </div>
                {this.state.currentDiffId ?
                  (<Scrollbars hideTracksWhenNotNeeded={true} autoHeightMin={'100px'}
                               autoHeight autoHeightMax={'390px'}
                               renderThumbVertical={props => <div {...props} className="thumbVertical"/>}>
                    {this.printDiff(this.state.currentDiffId)}
                  </Scrollbars>) : null }
              </div>
            ) : null
        }
      </GenericModalDialog>
    );
  }

  printDiff(diffId) {
    const diff = this.state.difference[diffId]
    const differentPaths = this.differentPathsForObjects(diff.currentNode, diff.otherNode)
    return (
      <div className="compareContainer">
        <div>
          <div className="versionHeader">Current version</div>
          {this.printNode(diff.currentNode, differentPaths, "_current")}
        </div>
        <div>
          <div className="versionHeader">Version {this.versionDisplayString(this.state.otherVersion)}</div>
          {this.printNode(diff.otherNode, [], "_other")}
        </div>
      </div>
    )
  }

  differentPathsForObjects(currentNode, otherNode) {
    const diffObject = JsonUtils.objectDiff(currentNode, otherNode)
    const flattenObj = JsonUtils.flattenObj(diffObject);
    return _.keys(flattenObj)
  }

  printNode(node, pathsToMark, keySuffix) {
    return node ? (<NodeDetailsContent isEditMode={false}
                                       key={node.id + keySuffix}
                                       node={node}
                                       processDefinitionData={this.props.processDefinitionData}
                                       pathsToMark={pathsToMark}
                                       onChange={() => {}}/>) :
      (<div className="notPresent">Node not present</div>)
  }

}

function mapState(state) {
  return {
    processId: _.get(state.graphReducer, 'fetchedProcessDetails.id'),
    version: _.get(state.graphReducer, 'fetchedProcessDetails.processVersionId'),
    processDefinitionData: state.settings.processDefinitionData,
    otherEnvironment: _.get(state.settings, "featuresSettings.remoteEnvironment.targetEnvironmentId"),
    versions: _.get(state.graphReducer, 'fetchedProcessDetails.history', []),
    businessView: state.graphReducer.businessView
  }
}

export default connect(mapState, ActionsUtils.mapDispatchWithEspActions)(CompareVersionsDialog);
