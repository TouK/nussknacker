import React from "react"
import ProcessUtils from "../../../../../common/ProcessUtils"
import {editors, editorType, Types} from "./EditorType"
import SwitchIcon from "./SwitchIcon"

export default class EditableExpression extends React.Component {

  constructor(props) {
    super(props)
    this.state = {
      displayRawEditor: true,
    }
  }

  render() {
    const {fieldType, expressionObj, rowClassName, valueClassName, showSwitch, param, renderFieldLabel, fieldLabel, readOnly} = this.props
    const type = fieldType || (param ? ProcessUtils.humanReadableType(param.typ.refClazzName) : Types.EXPRESSION)

    const basicEditor = Object.entries(editors).find(
      ([editorName, value]) => value.isSupported(type) && editorName !== Types.RAW_EDITOR)
    const editorName = (!this.state.displayRawEditor && !_.isEmpty(basicEditor)) || type === Types.EXPRESSION_WITH_FIXED_VALUES ?
      basicEditor[0] : Types.RAW_EDITOR
    const editor = _.get(editors, editorName)
    const Editor = editor.editor

    const switchableToEditorName = editor.switchableToEditors.find(editor => editors[editor].isSupported(type)) || Types.RAW_EDITOR
    const switchableToEditor = _.get(editors, switchableToEditorName)

    const shouldShowSwitch = this.showSwitch(type, showSwitch)

    return (
      <div className={`${rowClassName ? rowClassName : " node-row"}`}>
        {fieldLabel && renderFieldLabel(fieldLabel)}
        <Editor toggleEditor={this.toggleEditor}
                className={`${valueClassName ? valueClassName : "node-value"} ${shouldShowSwitch ? "switchable " : ""}`}
                {...this.props}
        />
        <SwitchIcon
          switchable={switchableToEditor.switchableTo(expressionObj)}
          hint={switchableToEditor.switchableTo(expressionObj) ? switchableToEditor.switchableToHint : switchableToEditor.notSwitchableToHint}
          onClick={this.toggleEditor}
          shouldShowSwitch={shouldShowSwitch}
          displayRawEditor={this.state.displayRawEditor}
          readOnly={readOnly}
        />
      </div>
    )
  }

  toggleEditor = (_) => this.setState({
    displayRawEditor: !this.state.displayRawEditor,
  })

  showSwitch = (fieldType, showSwitch) => showSwitch && editorType.basicEditorSupported(fieldType)
}
