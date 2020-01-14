import React from "react"
import {DragSource, DropTarget} from "react-dnd"
import ReactDOM from "react-dom"
import Select from "react-select"
import {allValid} from "../../../../common/Validators"
import ValidationLabels from "../../../modals/ValidationLabels"
import SvgDiv from "../../../SvgDiv"

class RowSelect extends React.Component {
  render() {
    const {
      changeName, changeValue, connectDragSource, connectDropTarget,
      field, index, isDragging, isMarked, options, toogleCloseOnEsc,
      showValidation, readOnly, remove, value, validators,
    } = this.props

    const markedClass = isMarked(index) ? " marked" : ""
    const opacity = isDragging ? 0 : 1

    return connectDropTarget(connectDragSource(
      <div className="node-row movable-row" style={{opacity}}>
        <div className={`node-value fieldName${markedClass}`}
          //to prevent dragging on specified elements, see https://stackoverflow.com/a/51911875
             draggable={true}
             onDragStart={this.preventDrag}>
          <input
            className={!showValidation || allValid(validators, [field.name]) ? "node-input" : "node-input node-input-with-error"}
            type="text"
            value={field.name}
            placeholder="Name"
            readOnly={readOnly}
            onChange={(e) => changeName(e.target.value)}
          />
          {showValidation && <ValidationLabels validators={validators} values={[field.name]}/>}
        </div>
        <div className={`node-value field${markedClass}`}
             draggable={true}
             onDragStart={this.preventDrag}>
          <Select
            className="node-value node-value-select node-value-type-select"
            classNamePrefix="node-value-select"
            disabled={readOnly}
            maxMenuHeight={190}
            onChange={(option) => changeValue(option.value)}
            onMenuOpen={() => toogleCloseOnEsc()}
            onMenuClose={() => toogleCloseOnEsc()}
            options={options}
            value={value}
          />
        </div>
        {
          readOnly ? null :
            <div className="node-value fieldRemove"
                 draggable={true}
                 onDragStart={this.preventDrag}>
              <button className="addRemoveButton" title="Remove field" onClick={() => {
                remove()
              }}>-
              </button>
            </div>
        }
        <SvgDiv svgFile={"handlebars.svg"} className={"handle-bars"}/>
      </div>,
    ))
  }

  preventDrag = (event) => {
    event.preventDefault()
    event.stopPropagation()
  }
}

const MovableRow =
  new DropTarget(
    "field", {
      // http://react-dnd.github.io/react-dnd/examples/sortable/simple
      hover(props, monitor, component) {
        if (!component) {
          return null
        }
        // node = HTML Div element from imperative API
        const node = ReactDOM.findDOMNode(component)
        if (!node) {
          return null
        }
        const dragIndex = monitor.getItem().index
        const hoverIndex = props.index
        // Don't replace items with themselves
        if (dragIndex === hoverIndex) {
          return
        }
        // Determine rectangle on screen
        const hoverBoundingRect = node.getBoundingClientRect()
        // Get vertical middle
        const hoverMiddleY = (hoverBoundingRect.bottom - hoverBoundingRect.top) / 2
        // Determine mouse position
        const clientOffset = monitor.getClientOffset()
        // Get pixels to the top
        const hoverClientY = clientOffset.y - hoverBoundingRect.top
        // Only perform the move when the mouse has crossed half of the items height
        // When dragging downwards, only move when the cursor is below 50%
        // When dragging upwards, only move when the cursor is above 50%
        // Dragging downwards
        if (dragIndex < hoverIndex && hoverClientY < hoverMiddleY) {
          return
        }
        // Dragging upwards
        if (dragIndex > hoverIndex && hoverClientY > hoverMiddleY) {
          return
        }

        props.moveItem(dragIndex, hoverIndex)
        monitor.getItem().index = hoverIndex
      },
    }, (connect) => ({
      connectDropTarget: connect.dropTarget(),
    }),
  )(
  new DragSource(
    "field", {
    beginDrag: (props) => ({
      index: props.index,
    }),
  }, (connect, monitor) => ({
    connectDragSource: connect.dragSource(),
    isDragging: monitor.isDragging(),
  }))(RowSelect),
)

export default MovableRow
