import React, {ReactNode} from "react"
import ProcessUtils from "../../../../../common/ProcessUtils"
import {editors, editorType, EditorTypes, SimpleEditorTypes} from "./EditorType"
import SwitchIcon from "./SwitchIcon"

class EditableExpression extends React.Component {

  constructor(props) {
    super(props)
    this.state = {
      displayRawEditor: undefined
    }
  }

  toggleEditor = (_) => this.setState({
    displayRawEditor: !this.state.displayRawEditor,
  })

  showSwitch = (paramType, showSwitch, parameterEditorType) => showSwitch
      && editorType.basicEditorSupported(paramType)
      && parameterEditorType.showSwitch()

  render() {
    const {
      fieldType, expressionObj, rowClassName, valueClassName, showSwitch, param, renderFieldLabel, fieldLabel, readOnly,
      values
    } = this.props

    const paramType = fieldType || (param ? ProcessUtils.humanReadableType(param.typ.refClazzName) : SimpleEditorTypes.EXPRESSION)

    const parameterEditorType = paramType === SimpleEditorTypes.FIXED_VALUES_EDITOR ?
      EditorTypes.find(editor => editor.type === "SimpleParameterEditor") :
      (!_.isEmpty(param) && !_.isEmpty(param.editor) ?
          EditorTypes.find(editor => editor.type === param.editor.type) :
          EditorTypes.find(editor => editor.type === "RawParameterEditor"))

    const shouldShowSwitch = this.showSwitch(paramType, showSwitch, parameterEditorType)

    const editor = _.get(editors, parameterEditorType.editorName(param, values, this.state.displayRawEditor))
    const Editor = editor.editor

    const switchableToEditorName = editor.switchableToEditors.find(editor => editors[editor].isSupported(paramType)) || SimpleEditorTypes.RAW_EDITOR
    const switchableToEditor = _.get(editors, switchableToEditorName)

    return (
      <div className={`${rowClassName ? rowClassName : " node-row"}`}>
        {fieldLabel && renderFieldLabel(fieldLabel)}
        <Editor toggleEditor={this.toggleEditor}
                className={`${valueClassName ? valueClassName : "node-value"} ${shouldShowSwitch ? "switchable " : ""}`}
                {...this.props}
                values={parameterEditorType.values(param, values)}
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
}

export default EditableExpression
