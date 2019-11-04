import React from "react";
import PropTypes from 'prop-types';
import _ from "lodash";
import {DragSource, DropTarget} from "react-dnd";
import dragHandleIcon from "../assets/img/drag-handle.png";
import update from "immutability-helper";
import ReactDOM from "react-dom";
import {notEmptyValidator} from "../common/Validators";
import ValidationLabels from "./ValidationLabels";

class RawField extends React.Component {
  render() {
    const markedClass = this.props.isMarked(this.props.index) ? " marked" : "";
    const index = this.props.index;
    const field = this.props.field;
    const opacity = this.props.isDragging ? 0 : 1;

    return this.props.connectDropTarget(this.props.connectDragSource(
      <div className="node-row movable-row" style={{opacity}}>
        <img src={dragHandleIcon} />
        <div className={"node-value fieldName" + markedClass}>
          <input className="node-input" type="text" value={field.name} placeholder="Name"
                 onChange={(e) => this.props.changeName(index, e.target.value)}/>
          <ValidationLabels validators={[notEmptyValidator]} value={field.name}/>
        </div>
        <div className={"node-value field" + markedClass}>
          {this.props.fieldCreator(field, (value) => this.props.changeValue(index, field.name, value))}
        </div>
        <div className="node-value fieldRemove">
          {/* TODO: add nicer buttons. Awesome font? */}
          <button className="addRemoveButton" title="Remove field" onClick={() => this.props.removeField(index)}>-
          </button>
        </div>
      </div>
    ));
  }
}

const Field =
  new DropTarget(
    "field", {
      // http://react-dnd.github.io/react-dnd/examples/sortable/simple
      hover(props, monitor, component) {
        if (!component) {
          return null;
        }
        // node = HTML Div element from imperative API
        const node = ReactDOM.findDOMNode(component);
        if (!node) {
          return null;
        }
        const dragIndex = monitor.getItem().index;
        const hoverIndex = props.index;
        // Don't replace items with themselves
        if (dragIndex === hoverIndex) {
          return;
        }
        // Determine rectangle on screen
        const hoverBoundingRect = node.getBoundingClientRect();
        // Get vertical middle
        const hoverMiddleY = (hoverBoundingRect.bottom - hoverBoundingRect.top) / 2;
        // Determine mouse position
        const clientOffset = monitor.getClientOffset();
        // Get pixels to the top
        const hoverClientY = clientOffset.y - hoverBoundingRect.top;
        // Only perform the move when the mouse has crossed half of the items height
        // When dragging downwards, only move when the cursor is below 50%
        // When dragging upwards, only move when the cursor is above 50%
        // Dragging downwards
        if (dragIndex < hoverIndex && hoverClientY < hoverMiddleY) {
          return;
        }
        // Dragging upwards
        if (dragIndex > hoverIndex && hoverClientY > hoverMiddleY) {
          return;
        }

        props.moveItem(dragIndex, hoverIndex);
        monitor.getItem().index = hoverIndex;
      },
    }, (connect) => ({
      connectDropTarget: connect.dropTarget()
    }),
  )(
  new DragSource(
    "field", {
    beginDrag: (props) => ({
      index: props.index
    })
  }, (connect, monitor) => ({
    connectDragSource: connect.dragSource(),
    isDragging: monitor.isDragging(),
  }))(RawField)
);

export default class Fields extends React.Component {

  static propTypes = {
    //value, e.g. { name: "name", value1: "inputValue" }
    fields: PropTypes.array.isRequired,
    //function (field, onChangeCallback)
    fieldCreator: PropTypes.func.isRequired,
    //function (fields)
    onChange: PropTypes.func.isRequired,
    //e.g. { name: "", value1: "" }
    newValue: PropTypes.object.isRequired
  }

  constructor(props) {
    super(props)

    const fieldsWithId = _.map(_.cloneDeep(this.props.fields), (elem, idx) => {
      elem.id = idx;
      return elem;
    });

    this.state = {
      fields: fieldsWithId
    }
  }

  render() {
    const moveItem = (dragIndex, hoverIndex) => {
      this.edit(previous => {
        return update(previous, {
          $splice: [[dragIndex, 1], [hoverIndex, 0, previous[dragIndex]]],
        })
      })
    };

    return (<div className="fieldsControl">
      {
        this.state.fields.map((field, index) =>
          <Field key={field.id}
                 field={field}
                 index={index}
                 changeName={this.changeName.bind(this)}
                 changeValue={this.changeValue.bind(this)}
                 removeField={this.removeField.bind(this)}
                 moveItem={moveItem}
                 {...this.props} />
        )
      }
      <div>
        <button className="addRemoveButton"  title="Add field"  onClick={() => this.addField()}>+</button>
      </div>
    </div>);
  }

  changeName(index, name) {
    this.edit(previous => {
      previous[index].name = name
      return previous
    })

  }

  changeValue(index, name, value) {
    this.edit(previous => {
      previous[index] = {
        ...previous[index],
        ...value
      };
      return previous
    })

  }

  addField() {
    this.edit(previous => {
      const newValue = _.cloneDeep(this.props.newValue);
      const fieldWithMaxId = _.maxBy(this.state.fields, field => field.id);

      newValue.id = fieldWithMaxId ? fieldWithMaxId.id + 1 : 0;
      previous.push(newValue);

      return previous
    })
  }

  removeField(index) {
    this.edit(previous => {
      previous.splice(index, 1)
      return previous
    })
  }

  edit(fieldFun) {
    this.setState(previous => {
      const previousCopied = _.cloneDeep(previous.fields)
      const newState = fieldFun(previousCopied)
      this.props.onChange(newState)
      return {
        fields: newState
      }
    })
  }
}