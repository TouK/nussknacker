import React from 'react'
import {render} from "react-dom";
import {Scrollbars} from "react-custom-scrollbars";
import {connect} from "react-redux";
import {bindActionCreators} from "redux";
import "../stylesheets/toolBox.styl";
import {Accordion, Panel} from "react-bootstrap";
import Tool from "./Tool"

class ToolBox extends React.Component {

  static propTypes = {
    processDefinitionData: React.PropTypes.object.isRequired
  }


  render() {
    var nodesToAdd = this.props.processDefinitionData.nodesToAdd || []

    //TODO: jakie opcje scrollbara??
    return (
      <div id="toolbox">
        <Scrollbars renderTrackHorizontal={props => <div className="hide"/>} autoHeight  autoHeightMax={400}>
        {
          nodesToAdd.map(group => {
            var nodes = group.possibleNodes.map(node => <Tool nodeModel={node.node} label={node.label} key={node.type + node.label}/>)
            nodes.push(<hr className="tool-group"/>)
            return nodes
          })
        }
        </Scrollbars>

      </div>
    );
  }
}

function mapState(state) {
  return {
    processDefinitionData: state.settings.processDefinitionData || {}
  }
}

export default connect(mapState, () => ({}))(ToolBox);
