import {css} from "emotion"
import React from "react"
import {DragDropContext} from "react-dnd"

import HTML5Backend from "react-dnd-html5-backend"

//TODO: this looks wierd, consider remove
class DragArea extends React.Component {
  render() {
    return (
      <div className={css({width: "100%", height: "100%"})}>
        {this.props.children}
      </div>
    )

  }
}
export default DragDropContext(HTML5Backend)(DragArea)
