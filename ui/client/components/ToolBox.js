import React from 'react'
import {render} from "react-dom";
import {Scrollbars} from "react-custom-scrollbars";
import {connect} from "react-redux";
import {bindActionCreators} from "redux";
import _ from 'lodash'
import "../stylesheets/toolBox.styl";
import {Accordion, Panel} from "react-bootstrap";
import Tool from "./Tool"

import TreeView from 'react-treeview'
import reactTreeviewCss from 'react-treeview/react-treeview.css'

class ToolBox extends React.Component {

  static propTypes = {
    processDefinitionData: React.PropTypes.object.isRequired,
    processCategory: React.PropTypes.string.isRequired
  }

  constructor(props) {
    super(props);
    this.state = {
      openedNodeGroups: {}
    }
  }

  toggleGroup = (nodeGroup) => {
    if (!this.nodeGroupIsEmpty(nodeGroup)) {
      const newOpenedNodeGroups = {
        ...this.state.openedNodeGroups,
        [nodeGroup.name]: !this.state.openedNodeGroups[nodeGroup.name]
      }
      this.setState({openedNodeGroups: newOpenedNodeGroups});
    }
  }

  nodeGroupIsEmpty = (nodeGroup) => {
    return nodeGroup.possibleNodes.length == 0
  }

  render() {
    return (
      <div id="toolbox">
        <div>
          {this.props.nodesToAdd.map((nodeGroup, i) => {
            const label =
              <span onClick={this.toggleGroup.bind(this, nodeGroup)}>{nodeGroup.name}</span>
            return (
              <TreeView
                itemClassName={this.nodeGroupIsEmpty(nodeGroup) ? "disabled" : ""}
                key={i}
                nodeLabel={label}
                collapsed={!this.state.openedNodeGroups[nodeGroup.name]}
                onClick={this.toggleGroup.bind(this, nodeGroup)}
              >
                {nodeGroup.possibleNodes.map(node =>
                  <Tool nodeModel={node.node} label={node.label} key={node.type + node.label}/>
                )}
              </TreeView>
            );
          })}
        </div>
      </div>
    );
  }
}

function mapState(state) {
  const processDefinitionData = state.settings.processDefinitionData || {}
  const processCategory = _.get(state.graphReducer.fetchedProcessDetails, 'processCategory', '')
  const nodesToAdd = (processDefinitionData.nodesToAdd || []).map((group) => {
    return {
      ...group,
      possibleNodes: group.possibleNodes.filter((node) => node.categories.includes(processCategory))
    }
  })

  return {
    processDefinitionData: processDefinitionData,
    processCategory: processCategory,
    nodesToAdd: nodesToAdd
  }
}

export default connect(mapState, () => ({}))(ToolBox);
