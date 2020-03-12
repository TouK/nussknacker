import React from "react"
import ProcessUtils from "../../../../../common/ProcessUtils"
import {dualEditorMode, editors, editorTypes} from "./Editor"
import SwitchIcon from "./SwitchIcon"
import FixedValuesEditor from "./FixedValuesEditor"
import _ from "lodash"

type Props = {
  fieldType?: string,
  expressionObj: $TodoType,
  showSwitch: boolean,
  renderFieldLabel?: Function,
  fieldLabel?: string,
  readOnly: boolean,
  rowClassName?: string,
  valueClassName?: string,
  param?: $TodoType,
  values?: Array<$TodoType>,
  editorName?: string,
  fieldName?: string,
  isMarked?: boolean,
  showValidation?: boolean,
  onValueChange: Function,
  errors?: Array<Error>,
}

type State = {
  displayRawEditor: boolean,
}

class EditableExpression extends React.Component<Props, State> {

  toggleEditor = (_) => {
    this.setState({
      displayRawEditor: !this.state.displayRawEditor,
    })
  }

  constructor(props) {
    super(props)

    const {param, expressionObj, values} = this.props
    this.state = {
      displayRawEditor: !(param?.editor.defaultMode === dualEditorMode.SIMPLE &&
          editors[param?.editor.simpleEditor.type].switchableTo(expressionObj, values)),
    }
  }

  render() {
    const {
      fieldType, expressionObj, rowClassName, valueClassName, showSwitch, param, renderFieldLabel, fieldLabel, readOnly,
      values, errors, fieldName,
    } = this.props

    const paramType = fieldType || (param ? ProcessUtils.humanReadableType(param.typ.refClazzName) : "expression")

    const editorType = paramType === editorTypes.FIXED_VALUES_PARAMETER_EDITOR ?
      editorTypes.FIXED_VALUES_PARAMETER_EDITOR :
      !_.isEmpty(param) ? param.editor.type : editorTypes.RAW_PARAMETER_EDITOR
    const editor = editors[editorType]

    const Editor = editor.editor(param, this.state.displayRawEditor)

    return (
      <div className={`${rowClassName ? rowClassName : " node-row"}`}>
        {fieldLabel && renderFieldLabel(fieldLabel)}
        <Editor
          toggleEditor={this.toggleEditor}
          className={`${valueClassName ? valueClassName : "node-value"} ${editor.showSwitch ? "switchable " : ""}`}
          {...this.props}
          values={Editor === FixedValuesEditor ? editor.values(param, values) : []}
          validators={editor.validators(param, errors, fieldName || fieldLabel, this.state.displayRawEditor)}
        />
        {
            param?.editor?.type === editorTypes.DUAL_PARAMETER_EDITOR && (
            <SwitchIcon
              switchable={editor.switchable(Editor, param, expressionObj)}
              hint={editor.hint(editor.switchable(Editor, param, expressionObj), Editor, param)}
              onClick={this.toggleEditor}
              shouldShowSwitch={editor.showSwitch}
              displayRawEditor={this.state.displayRawEditor}
              readOnly={readOnly}
            />
          )}
      </div>
    )
  }
}

export default EditableExpression
