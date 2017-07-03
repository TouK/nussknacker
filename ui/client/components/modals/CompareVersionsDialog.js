import React from "react";
import {render} from "react-dom";
import {connect} from "react-redux";
import _ from "lodash";
import ActionsUtils from "../../actions/ActionsUtils";
import "../../stylesheets/visualization.styl";
import GenericModalDialog from "./GenericModalDialog";
import Dialogs from "./Dialogs";
import HttpService from "../../http/HttpService";
import {Table, Td, Tr} from "reactable";
import NodeDetailsContent from "../graph/NodeDetailsContent";
import Moment from "moment";

import Scrollbars from "react-custom-scrollbars";

//TODO: handle displaying groups
//TODO: handle different textarea heights
class CompareVersionsDialog extends React.Component {

  constructor(props) {
    super(props);
    this.initState = {
      otherVersion: null,
      currentDiffId: null,
      difference: null
    };
    this.state = this.initState
  }

  loadVersion(versionId) {
    if (versionId) {
      HttpService.compareProcesses(this.props.processId, this.props.version, versionId)
        .then((difference) => this.setState({difference: difference, otherVersion: versionId, currentDiffId: null}))
    } else {
      this.setState(this.initState)
    }

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
            {this.props.versions.filter(version => this.props.version !== version.processVersionId).map((version, index) => (
              <option key={version.processVersionId} value={version.processVersionId}>
                {version.processVersionId} - created by {version.user}
                &nbsp;on {Moment(version.createDate).format("YYYY-MM-DD HH:mm:ss")}</option>))}
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
                               autoHeight autoHeightMax={'350px'}
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
    return (
      <div className="compareContainer">
        <div>
          <div className="versionHeader">Current version</div>
          {this.printNode(diff.currentNode)}
        </div>
        <div>
          <div className="versionHeader">Version {this.state.otherVersion}</div>
          {this.printNode(diff.otherNode)}
        </div>
      </div>
    )
  }

  printNode(node) {
    return node ? (<NodeDetailsContent isEditMode={false} node={node}
                                       processDefinitionData={this.props.processDefinitionData}
                                       onChange={() => {
                                       }}/>) :
      (<div className="notPresent">Node not present</div>)


  }
}

function mapState(state) {
  return {
    processId: _.get(state.graphReducer, 'fetchedProcessDetails.id'),
    version: _.get(state.graphReducer, 'fetchedProcessDetails.processVersionId'),
    processDefinitionData: state.settings.processDefinitionData,
    versions: _.get(state.graphReducer, 'fetchedProcessDetails.history', [])
  }
}

export default connect(mapState, ActionsUtils.mapDispatchWithEspActions)(CompareVersionsDialog);
