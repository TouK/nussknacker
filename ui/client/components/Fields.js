import React from "react";
import _ from "lodash";


export default class Fields extends React.Component {

  static propTypes = {
    //value, e.g. { name: "name", value1: "inputValue" }
    fields: React.PropTypes.array.isRequired,
    //function (field, onChangeCallback)
    fieldCreator: React.PropTypes.func.isRequired,
    //function (fields)
    onChange: React.PropTypes.func.isRequired,
    //e.g. { name: "", value1: "" }
    newValue: React.PropTypes.object.isRequired
  }

  constructor(props) {
    super(props)

    this.state = {
      fields: this.props.fields
    }
  }

  render() {
    return (<div className="fieldsControl">
        {
          this.state.fields.map((field, index) => {
            const markedClass = this.props.isMarked(index) ? " marked" : ""
            return (
              <div className="node-row" key={index}>
                <div className={"node-value fieldName" + markedClass}>
                  <input className="node-input" type="text" value={field.name} placeholder="Field name"
                         onChange={e => this.changeName(index, e.target.value)}/>
                </div>
                <div className={"node-value field" + markedClass}>
                  {this.props.fieldCreator(field, (value) => this.changeValue(index, field.name, value))}
                </div>
                <div className="node-value fieldRemove">
                  {/* TODO: add nicer buttons. Awesome font? */}
                  <button className="addRemoveButton" title="Remove field" onClick={() => this.removeField(index)}>-
                  </button>
                </div>
              </div>
            )

            }
          )
        }
      <div>
        <button className="addRemoveButton"  title="Add field"  onClick={() => this.addField()}>+</button>
      </div>
    </div>)

  }

  changeName(index, name) {
    this.edit(previous => {
      previous[index].name = name
      return previous
    })

  }

  changeValue(index, name, value) {
    this.edit(previous => {
      previous[index] = value
      previous[index].name = name
      return previous
    })

  }

  addField() {
    this.edit(previous => {
      previous.push(this.props.newValue)
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
