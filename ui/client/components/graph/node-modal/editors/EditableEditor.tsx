import React from "react"
import ProcessUtils from "../../../../common/ProcessUtils"
import {DualEditorMode, editors, EditorType} from "./expression/Editor"
import SwitchIcon from "./expression/SwitchIcon"
import FixedValuesEditor from "./expression/FixedValuesEditor"
import {isEmpty} from "lodash"
import {ExpressionObj} from "./expression/types"
import {spelFormatters} from "./expression/Formatter"

type Props = {
  fieldType?: string,
  expressionObj: ExpressionObj,
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

class EditableEditor extends React.Component<Props, State> {

  toggleEditor = (_) => {
    this.setState({
      displayRawEditor: !this.state.displayRawEditor,
    })
  }

  constructor(props) {
    super(props)

    const {param, expressionObj, values} = this.props
    this.state = {
      displayRawEditor: !(param?.editor.defaultMode === DualEditorMode.SIMPLE &&
        editors[param?.editor.simpleEditor.type].switchableTo(expressionObj, param, values)),
    }
  }

  render() {
    const {
      fieldType, expressionObj, rowClassName, valueClassName, showSwitch, param, renderFieldLabel, fieldLabel, readOnly,
      values, errors, fieldName, showValidation,
    } = this.props

    const paramType = fieldType || (param ? ProcessUtils.humanReadableType(param.typ.refClazzName) : "expression")

    const editorType = paramType === EditorType.FIXED_VALUES_PARAMETER_EDITOR ?
      EditorType.FIXED_VALUES_PARAMETER_EDITOR :
      !isEmpty(param) ? param.editor.type : EditorType.RAW_PARAMETER_EDITOR
    const editor = editors[editorType]

    const Editor = editor.editor(param, this.state.displayRawEditor, expressionObj)

    return (
      <div className={`${rowClassName ? rowClassName : " node-row"}`}>
        {fieldLabel && renderFieldLabel(fieldLabel)}
        <Editor
          toggleEditor={this.toggleEditor}
          className={`${valueClassName ? valueClassName : "node-value"} ${editor.showSwitch ? "switchable " : ""}`}
          {...this.props}
          values={Editor === FixedValuesEditor ? editor.values(param, values) : []}
          validators={editor.validators(param, errors, fieldName || fieldLabel, this.state.displayRawEditor)}
          components={editor.components(param)}
          formatter={expressionObj.language === "spel" && spelFormatters[param?.typ.refClazzName] != null ?
            spelFormatters[param.typ.refClazzName] : null}
          showValidation={showValidation}
        />
        {
            param?.editor?.type === EditorType.DUAL_PARAMETER_EDITOR && (
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

export default EditableEditor
