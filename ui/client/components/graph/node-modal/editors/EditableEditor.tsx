import React from "react"
import {editors, EditorType, simpleEditorValidators} from "./expression/Editor"
import {isEmpty} from "lodash"
import {ExpressionObj} from "./expression/types"
import {spelFormatters} from "./expression/Formatter"
import {VariableTypes} from "../../../../types"
import {Error} from "./Validators"

type Props = {
  expressionObj: ExpressionObj,
  showSwitch: boolean,
  renderFieldLabel?: Function,
  fieldLabel?: string,
  readOnly: boolean,
  rowClassName?: string,
  valueClassName?: string,
  param?: $TodoType,
  values?: Array<$TodoType>,
  fieldName?: string,
  isMarked?: boolean,
  showValidation?: boolean,
  onValueChange: Function,
  errors?: Array<Error>,
  variableTypes: VariableTypes,
}

type State = {
  displayRawEditor: boolean,
}

class EditableEditor extends React.Component<Props, State> {

  render() {
    const {
      expressionObj, rowClassName, valueClassName, param, renderFieldLabel, fieldLabel,
      errors, fieldName,
    } = this.props

    const editorType = !isEmpty(param) ? param.editor.type : EditorType.RAW_PARAMETER_EDITOR

    const Editor = editors[editorType]

    const validators = simpleEditorValidators(param, errors, fieldName, fieldLabel)
    return (
      <div className={`${rowClassName ? rowClassName : " node-row"}`}>
        {fieldLabel && renderFieldLabel(fieldLabel)}
        <Editor
          {...this.props}
          editorConfig={param?.editor}
          className={`${valueClassName ? valueClassName : "node-value"}`}
          validators={validators}
          formatter={expressionObj.language === "spel" && spelFormatters[param?.typ.refClazzName] != null ?
            spelFormatters[param.typ.refClazzName] : null}
        />
      </div>
    )
  }
}

export default EditableEditor
