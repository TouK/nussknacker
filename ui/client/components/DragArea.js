import React from "react"

import HTML5Backend from "react-dnd-html5-backend";
import {DragDropContext} from "react-dnd";

class DragArea extends React.Component {
  render() {
    return (<div>
      {this.props.children}
    </div>)

  }
}
export default DragDropContext(HTML5Backend)(DragArea)
