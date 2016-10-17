import React from 'react'
import {render} from "react-dom";
import { DragSource } from 'react-dnd';
import "../stylesheets/toolBox.styl";

class Tool extends React.Component {

  static propTypes = {
    nodeModel: React.PropTypes.object.isRequired,
    label: React.PropTypes.string.isRequired,
    connectDragSource: React.PropTypes.func.isRequired
  };

  render() {
    const type = this.props.nodeModel.type
    const attributes = require('json!../assets/json/nodeAttributes.json')
    const iconName = attributes[type].icon
    const icon = require('../assets/img/' + iconName);

    return this.props.connectDragSource(
      <div className="tool">
        <div className="icon-block">
          <img src={icon} className="toolIcon"/>
        </div>
        <div className="title-block">
          <span>{this.props.label}</span>
        </div>
      </div>
    )
  }
}

var spec = {
  beginDrag: (props, monitor, component) => props.nodeModel
};

export default DragSource("element", spec, (connect, monitor) => ({
  connectDragSource: connect.dragSource()
}))(Tool);
