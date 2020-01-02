import React from "react"
import {editorType, Types} from "./EditorType"
import ProcessUtils from "../../../../../common/ProcessUtils"
import {switchableToStringEditor} from "./StringEditor"
import {switchableToBoolEditor} from "./BoolEditor"

export default class EditableExpression extends React.Component {

  constructor(props) {
    super(props)
    this.state = {
      displayRawEditor: true
    }
  }

  render() {
    const {fieldType, expressionObj, rowClassName, valueClassName, showSwitch, param} = this.props
    const type = fieldType || (param ? ProcessUtils.humanReadableType(param.typ.refClazzName) : Types.EXPRESSION)
    const editorName = this.editorName(type, expressionObj, this.state.displayRawEditor)
    const Editor = editorType.editor(editorName)
    return <Editor toggleEditor={this.toggleEditor}
                   editorName={editorName}
                   shouldShowSwitch={this.showSwitch(type, showSwitch)}
                   rowClassName={rowClassName ? rowClassName : "node-row"}
                   valueClassName={valueClassName ? valueClassName : "node-value"}
                   displayRawEditor={this.state.displayRawEditor}
                   {...this.props}
                   fieldType={type}
    />
  }

  editorName = (fieldType, expressionObj, displayRawEditor) => {
    switch (fieldType) {
      case Types.EXPRESSION:
        return !displayRawEditor && switchableToBoolEditor(expressionObj) ? Types.BOOL_EDITOR : Types.RAW_EDITOR
      case Types.BOOLEAN:
        return !displayRawEditor && switchableToBoolEditor(expressionObj) ? Types.BOOL_EDITOR : Types.RAW_EDITOR
      case Types.STRING:
        return !displayRawEditor && switchableToStringEditor(expressionObj) ? Types.STRING_EDITOR : Types.RAW_EDITOR
      case Types.EXPRESSION_WITH_FIXED_VALUES || Types.RAW_EDITOR:
        return fieldType
      default:
        return Types.RAW_EDITOR
    }
  }

  toggleEditor = (_) => this.setState({
    displayRawEditor: !this.state.displayRawEditor
  })

  showSwitch = (fieldType, showSwitch) => showSwitch && editorType.isSupported(fieldType)
}