import React from 'react'
import {render} from "react-dom";
import { DragSource } from 'react-dnd';
import * as LoaderUtils from '../common/LoaderUtils'
import "../stylesheets/toolBox.styl";
import ProcessUtils from "../common/ProcessUtils";

class Tool extends React.Component {

  static propTypes = {
    nodeModel: React.PropTypes.object.isRequired,
    label: React.PropTypes.string.isRequired,
    connectDragSource: React.PropTypes.func.isRequired
  };

  render() {
    //FIXME load icon defined in config
    const icon = LoaderUtils.loadNodeSvgContent(`${this.props.nodeModel.type}.svg`)

    return this.props.connectDragSource(
      <div className="tool">
        <div className="toolWrapper">
          <div dangerouslySetInnerHTML={{__html: icon}} className="toolIcon"/> {this.props.label} </div>
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
