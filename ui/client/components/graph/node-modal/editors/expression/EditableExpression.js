import React from "react"
import {editorType, Types} from "./EditorType"
import ProcessUtils from "../../../../../common/ProcessUtils"
import StringEditor from "./StringEditor"
import BoolEditor from "./BoolEditor"

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
    const editorObject = editorType.editor(editorName)
    const Editor = editorObject.editor

    return <Editor toggleEditor={this.toggleEditor}
                   switchable={editorObject.switchable}
                   switchableHint={editorObject.switchableHint}
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
      case Types.BOOLEAN:
        return !displayRawEditor && BoolEditor.switchableOnto(expressionObj) ? Types.BOOL_EDITOR : Types.RAW_EDITOR
      case Types.STRING:
        return !displayRawEditor && StringEditor.switchableOnto(expressionObj) ? Types.STRING_EDITOR : Types.RAW_EDITOR
      case Types.EXPRESSION_WITH_FIXED_VALUES:
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