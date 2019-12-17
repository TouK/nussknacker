import React from "react"
import {parseableBoolean} from "./ExpressionParser"

import {editorType, Types} from "./EditorType"

export default class EditableExpression extends React.Component {

  constructor(props) {
    super(props)
    this.state = {
      displayRawEditor: true
    }
  }

  render() {
    const {fieldType, expressionObj, rowClassName, valueClassName, showSwitch} = this.props
    const editorName = editorType.editorName(fieldType, expressionObj, this.state.displayRawEditor)
    const Editor = editorType.editor(editorName)
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
      case Types.BOOL_EDITOR:
        return true
      case Types.RAW_EDITOR:
        return parseableBoolean(expressionObj)
      default:
        return false
    }
  }

  showSwitch = (fieldType, showSwitch) => showSwitch && editorType.isSupported(fieldType)
}