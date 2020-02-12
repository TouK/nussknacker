import PropTypes from "prop-types"
import React from "react"
import {DragSource} from "react-dnd"
import "../stylesheets/toolBox.styl"
import {absoluteBePath} from "../common/UrlUtils"
import ProcessUtils from "../common/ProcessUtils"

class Tool extends React.Component {

  static propTypes = {
    nodeModel: PropTypes.object.isRequired,
    label: PropTypes.string.isRequired,
    connectDragSource: PropTypes.func.isRequired,
    processDefinitionData: PropTypes.object.isRequired,
  };

  render() {
    const nodesSettings = this.props.processDefinitionData.nodesConfig || {}
    const iconFromConfig = (nodesSettings[ProcessUtils.findNodeConfigName(this.props.nodeModel)] || {}).icon
    const defaultIconName = `${this.props.nodeModel.type}.svg`
    const icon = absoluteBePath(`/assets/nodes/${iconFromConfig ? iconFromConfig : defaultIconName}`)

    return this.props.connectDragSource(
      <div className="tool">
        <div className="toolWrapper">
          <img src={icon} alt={"node icon"} className="toolIcon"/>
          {this.props.label}
        </div>
      </div>
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
