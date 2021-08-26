import React, {useState} from "react"
import {DragSource, DropTarget} from "react-dnd"
import ReactDOM from "react-dom"
import Select from "react-select"
import styles from "../../../../stylesheets/select.styl"
import ValidationLabels from "../../../modals/ValidationLabels"
import SvgDiv from "../../../SvgDiv"
import {ButtonWithFocus, InputWithFocus} from "../../../withFocus"
import {allValid} from "../editors/Validators"

function RowSelect(props) {
  const {
    changeName, changeValue, connectDragSource, connectDropTarget,
    field, index, isDragging, isMarked, options,
    showValidation, readOnly, remove, value, validators,
  } = props

  const [captureEsc, setCaptureEsc] = useState(false)

  const markedClass = isMarked(index) ? " marked" : ""
  const opacity = isDragging ? 0 : 1

  return connectDropTarget(connectDragSource(
    <div className="node-row movable-row" style={{opacity}}>
      <div
        className={`node-value fieldName${markedClass}`}
        //to prevent dragging on specified elements, see https://stackoverflow.com/a/51911875
        draggable={true}
        onDragStart={(event) => {
          event.preventDefault()
          event.stopPropagation()
        }}
      >
        <InputWithFocus
          className={!showValidation || allValid(validators, [field.name]) ? "node-input" : "node-input node-input-with-error"}
          type="text"
          value={field.name}
          placeholder="Name"
          readOnly={readOnly}
          onChange={(e) => changeName(e.target.value)}
        />
        {showValidation && <ValidationLabels validators={validators} values={[field.name]}/>}
      </div>
      <div
        className={`node-value field${markedClass}`}
        draggable={true}
        onDragStart={(event) => {
          event.preventDefault()
          event.stopPropagation()
        }}
        onKeyDown={e => {
          //prevent modal close by esc
          if (captureEsc && e.key === "Escape") {
            e.stopPropagation()
          }
        }}
      >
        <Select
          className="node-value node-value-select node-value-type-select"
          classNamePrefix={styles.nodeValueSelect}
          isDisabled={readOnly}
          maxMenuHeight={190}
          onChange={(option) => changeValue(option.value)}
          onMenuOpen={() => setCaptureEsc(true)}
          onMenuClose={() => setCaptureEsc(false)}
          options={options}
          value={value}
        />
      </div>
      {
        readOnly ?
          null :
          (
            <div
              className="node-value fieldRemove"
              draggable={true}
              onDragStart={(event) => {
                event.preventDefault()
                event.stopPropagation()
              }}
            >
              <ButtonWithFocus
                className="addRemoveButton"
                title="Remove field"
                onClick={() => {
                  remove()
                }}
              >-
              </ButtonWithFocus>
            </div>
          )}
      <SvgDiv svgFile={"handlebars.svg"} className={"handle-bars"}/>
    </div>,
  ))
}

const MovableRow =
  DropTarget(
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
    DragSource(
      "field", {
        beginDrag: (props) => ({
          index: props.index,
        }),
      }, (connect, monitor) => ({
        connectDragSource: connect.dragSource(),
        isDragging: monitor.isDragging(),
      })
    )(RowSelect),
  )

export default MovableRow
