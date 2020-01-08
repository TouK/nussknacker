import React from "react"
import {editors, editorType, Types} from "./EditorType"
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
    const type = fieldType || (param ? ProcessUtils.humanReadableType(param.typ.refClazzName) : Types.EXPRESSION)

    const basicEditor = Object.entries(editors).find(
      ([editorName, value]) => value.isSupported(type) && editorName !== Types.RAW_EDITOR)
    const editorName = (!this.state.displayRawEditor && !_.isEmpty(basicEditor)) || type === Types.EXPRESSION_WITH_FIXED_VALUES ?
      basicEditor[0] : Types.RAW_EDITOR
    const editor = _.get(editors, editorName)
    const Editor = editor.editor

    const switchableToEditorName = editor.switchableToEditors.find(editor => editors[editor].isSupported(type)) || Types.RAW_EDITOR
    const switchableToEditor = _.get(editors, switchableToEditorName)

    return <Editor toggleEditor={this.toggleEditor}
                   switchableTo={switchableToEditor.switchableTo}
                   switchableToHint={switchableToEditor.switchableToHint}
                   notSwitchableToHint={switchableToEditor.notSwitchableToHint}
                   editorName={editorName}
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

  showSwitch = (fieldType, showSwitch) => showSwitch && editorType.basicEditorSupported(fieldType)
}