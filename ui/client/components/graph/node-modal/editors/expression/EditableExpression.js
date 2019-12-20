import React from "react"

import {editorType, Types} from "./EditorType"
import ProcessUtils from "../../../../../common/ProcessUtils"

export default class EditableExpression extends React.Component {

  constructor(props) {
    super(props)
    this.state = {
      displayRawEditor: true
    }
  }

  render() {
    const {fieldType, expressionObj, rowClassName, valueClassName, showSwitch, param} = this.props
    const type = fieldType || (param ? ProcessUtils.humanReadableType(param.typ.refClazzName) : "expression")
    const editorName = editorType.editorName(type, expressionObj, this.state.displayRawEditor)
    const Editor = editorType.editor(editorName)
    return <Editor toggleEditor={this.toggleEditor}
                   switchable={this.switchable(editorName, expressionObj)}
                   shouldShowSwitch={this.showSwitch(type, showSwitch)}
                   rowClassName={rowClassName ? rowClassName : "node-row"}
                   valueClassName={valueClassName ? valueClassName : "node-value"}
                   displayRawEditor={this.state.displayRawEditor}
                   {...this.props}
                   fieldType={type}
    />
  }

  toggleEditor = (_) => this.setState({
    displayRawEditor: !this.state.displayRawEditor
  })

  switchable = (editorName, expressionObj) => {
    switch (editorName) {
      case Types.BOOL_EDITOR:
        return true
      case Types.RAW_EDITOR:
        return editorType.switchableToBooleanEditor(expressionObj)
      default:
        return false
    }
  }

  showSwitch = (fieldType, showSwitch) => showSwitch && editorType.isSupported(fieldType)
}