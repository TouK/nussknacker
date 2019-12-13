import RawEditor from "./RawEditor"
import BoolEditor from "./BoolEditor"
import React from "react"
import ExpressionWithFixedValues from "./ExpressionWithFixedValues"
import {parseableBoolean} from "./ExpressionParser"

export default class EditableExpression extends React.Component {

  constructor(props) {
    super(props)
    this.state = {
      displayRawEditor: true
    }
  }

  render() {
    const {fieldType, expressionObj, rowClassName, valueClassName, showSwitch} = this.props
    const editorName = resolveEditorName(fieldType, expressionObj, this.state.displayRawEditor)
    const Editor = editorTypes[editorName]
    return <Editor toggleEditor={this.toggleEditor}
                   switchable={this.switchable(editorName, expressionObj)}
                   shouldShowSwitch={this.showSwitch(fieldType, showSwitch)}
                   rowClassName={rowClassName ? rowClassName : "node-row"}
                   valueClassName={valueClassName ? valueClassName : "node-value"}
                   {...this.props}/>
  }

  toggleEditor = (_) => this.setState({
    displayRawEditor: !this.state.displayRawEditor
  })

  switchable = (editorName, expressionObj) => {
    switch (editorName) {
      case boolEditor:
        return true
      case rawEditor:
        return parseableBoolean(expressionObj)
      default:
        return false
    }
  }

  showSwitch = (fieldType, showSwitch) =>
    showSwitch &&
    (!(fieldType === "VariableBuilder" || fieldType === "Variable")
      && (fieldType === "expression" || fieldType === "Boolean"))
}

const resolveEditorName = (fieldType, expressionObj, displayRawEditor) => {
  if (displayRawEditor) {
    return rawEditor
  }

  switch (fieldType) {
    case "expression":
      return parseableBoolean(expressionObj) ? boolEditor : rawEditor
    case "Boolean":
      return parseableBoolean(expressionObj) ? boolEditor : rawEditor
    case expressionWithFixedValuesEditor:
      return fieldType
    case rawEditor:
      return fieldType
    default:
      return rawEditor
  }
}

const editorTypes = {
  rawEditor: RawEditor,
  boolEditor: BoolEditor,
  expressionWithFixedValues: ExpressionWithFixedValues
}

export const boolEditor = "boolEditor"
export const rawEditor = "rawEditor"
export const expressionWithFixedValuesEditor = "expressionWithFixedValues"