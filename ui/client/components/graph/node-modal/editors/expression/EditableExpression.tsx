import React from "react"
import ProcessUtils from "../../../../../common/ProcessUtils"
import {dualEditorMode, editors, editorTypes} from "./EditorType"
import SwitchIcon from "./SwitchIcon"
import FixedValuesEditor from "./FixedValuesEditor"
import _ from "lodash";
import {$TodoType} from "../../../../../actions/migrationTypes";

type Props = {
  fieldType: string
  expressionObj: $TodoType
  showSwitch: boolean
  renderFieldLabel: Function
  fieldLabel: string
  readOnly: boolean
  rowClassName?: string
  valueClassName?: string
  param?: $TodoType
  values?: Array<$TodoType>
  editorName?: string
  fieldName?: string
  isMarked?: boolean
  validators?: Array<$TodoType>
  showValidation?: boolean
  onValueChange: Function
}

type State = {
  displayRawEditor: boolean
}

class EditableExpression extends React.Component<Props, State> {

  state = {
    displayRawEditor: undefined
  }

  toggleEditor = (_) => {
    const {param} = this.props
    this.setState({
      displayRawEditor: this.state.displayRawEditor === undefined ?
        (param.editor.defaultMode === dualEditorMode.SIMPLE) :
        !this.state.displayRawEditor
    })
  }

  render() {
    const {
      fieldType, expressionObj, rowClassName, valueClassName, showSwitch, param, renderFieldLabel, fieldLabel, readOnly,
      values
    } = this.props

    const paramType = fieldType || (param ? ProcessUtils.humanReadableType(param.typ.refClazzName) : "expression")

    const editorType = paramType === editorTypes.FIXED_VALUES_PARAMETER_EDITOR ?
      editorTypes.FIXED_VALUES_PARAMETER_EDITOR :
      (!_.isEmpty(param) ? param.editor.type : editorTypes.RAW_PARAMETER_EDITOR)
    const editor = editors[editorType]

    const Editor = editor.editor(param, this.state.displayRawEditor)

    return (
      <div className={`${rowClassName ? rowClassName : " node-row"}`}>
        {fieldLabel && renderFieldLabel(fieldLabel)}
        <Editor toggleEditor={this.toggleEditor}
                className={`${valueClassName ? valueClassName : "node-value"} ${editor.showSwitch ? "switchable " : ""}`}
                {...this.props}
                values={Editor === FixedValuesEditor ? editor.values(param, values) : []}
        />
        {
          param?.editor?.type === editorTypes.DUAL_PARAMETER_EDITOR &&
          <SwitchIcon
            switchable={editor.switchable(Editor, param, expressionObj)}
            hint={editor.hint(editor.switchable(Editor, param, expressionObj), Editor, param)}
            onClick={this.toggleEditor}
            shouldShowSwitch={editor.showSwitch}
            displayRawEditor={this.state.displayRawEditor}
            readOnly={readOnly}
          />
        }
      </div>
    )
  }
}

export default EditableExpression
