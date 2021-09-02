import {isEmpty} from "lodash"
import React from "react"
import {VariableTypes} from "../../../../types"
import {UnknownFunction} from "../../../../types/common"
import {editors, EditorType, simpleEditorValidators} from "./expression/Editor"
import {spelFormatters} from "./expression/Formatter"
import {ExpressionLang, ExpressionObj} from "./expression/types"
import {ParamType} from "./types"
import {Error} from "./Validators"

type Props = {
  expressionObj: ExpressionObj,
  showSwitch?: boolean,
  renderFieldLabel?: UnknownFunction,
  fieldLabel?: string,
  readOnly: boolean,
  rowClassName?: string,
  valueClassName?: string,
  param?: ParamType,
  values?: Array<$TodoType>,
  fieldName?: string,
  isMarked?: boolean,
  showValidation?: boolean,
  onValueChange: UnknownFunction,
  errors?: Array<Error>,
  variableTypes: VariableTypes,
  validationLabelInfo?: string,
}

type State = {
  displayRawEditor: boolean,
}

class EditableEditor extends React.Component<Props, State> {

  render() {
    const {
      expressionObj, rowClassName, valueClassName, param, renderFieldLabel, fieldLabel,
      errors, fieldName, validationLabelInfo,
    } = this.props

    const editorType = isEmpty(param) ? EditorType.RAW_PARAMETER_EDITOR : param.editor.type

    const Editor = editors[editorType]

    const validators = simpleEditorValidators(param, errors, fieldName, fieldLabel)

    const formatter = expressionObj.language === ExpressionLang.SpEL ? spelFormatters[param?.typ?.refClazzName] : null

    return (
      <div className={`${rowClassName ? rowClassName : " node-row"}`}>
        {fieldLabel && renderFieldLabel?.(fieldLabel)}
        <Editor
          {...this.props}
          editorConfig={param?.editor}
          className={`${valueClassName ? valueClassName : "node-value"}`}
          validators={validators}
          formatter={formatter}
          expressionInfo={validationLabelInfo}
        />
      </div>
    )
  }
}

export default EditableEditor
