import PropTypes from "prop-types"
import React from "react"
import {DragSource} from "react-dnd"
import * as LoaderUtils from "../common/LoaderUtils"
import "../stylesheets/toolBox.styl"

class Tool extends React.Component {

  static propTypes = {
    nodeModel: PropTypes.object.isRequired,
    label: PropTypes.string.isRequired,
    connectDragSource: PropTypes.func.isRequired,
  };

  render() {
    //FIXME load icon defined in config
    const icon = LoaderUtils.loadNodeSvgContent(`${this.props.nodeModel.type}.svg`)

    return this.props.connectDragSource(
      <div className="tool">
        <div className="toolWrapper">
          <div dangerouslySetInnerHTML={{__html: icon}} className="toolIcon"/> {this.props.label} </div>
        </div>,
    )
  }
}

var spec = {
  beginDrag: (props, monitor, component) => {
    const nodeModel = _.cloneDeep(props.nodeModel)
    _.set(nodeModel, "id", props.label)
    return nodeModel
  },
}

export default DragSource("element", spec, (connect, monitor) => ({
  connectDragSource: connect.dragSource(),
}))(Tool)
