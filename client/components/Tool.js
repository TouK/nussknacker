import React from 'react'
import {render} from "react-dom";
import { DragSource } from 'react-dnd';
import "../stylesheets/toolBox.styl";

class Tool extends React.Component {

  static propTypes = {
    type: React.PropTypes.string.isRequired,
    connectDragSource: React.PropTypes.func.isRequired
  };

  render() {
    const type = this.props.type
    const attributes = require('json!../assets/json/nodeAttributes.json')
    const iconName = attributes[type].icon
    const icon = require('../assets/img/' + iconName);

    return this.props.connectDragSource(
      <div className="tool">
        <img src={icon} className="toolIcon"/>{this.props.type}</div>)
  }
}

var spec = {
  beginDrag: (props, monitor, component) => ({
    type: props.type,
    expression: {
      language: "spel",
      expression: "true"
    }
  })
};

export default DragSource("element", spec, (connect, monitor) => ({
  connectDragSource: connect.dragSource()
}))(Tool);
