import {isEmpty} from "lodash"
import React from "react"
import {VariableTypes} from "../../../../types"
import {UnknownFunction} from "../../../../types/common"
import {editors, EditorType, simpleEditorValidators} from "./expression/Editor"
import {spelFormatters} from "./expression/Formatter"
import {ExpressionLang, ExpressionObj} from "./expression/types"
import {ParamType} from "./types"
import {Error, Validator} from "./Validators"

interface Props {
  expressionObj: ExpressionObj,
  showSwitch?: boolean,
  fieldLabel?: string,
  readOnly: boolean,
  valueClassName?: string,
  param?: ParamType,
  values?: Array<$TodoType>,
  fieldName?: string,
  isMarked?: boolean,
  showValidation?: boolean,
  onValueChange: (value: string) => void,
  errors?: Array<Error>,
  variableTypes: VariableTypes,
  validationLabelInfo?: string,
  validators?: Validator[],
}

export function EditableEditor(props: Props): JSX.Element {
  const {
    expressionObj, valueClassName, param, fieldLabel,
    errors, fieldName, validationLabelInfo, validators = [],
  } = props

  const editorType = isEmpty(param) ? EditorType.RAW_PARAMETER_EDITOR : param.editor.type

  const Editor = editors[editorType]

  const mergedValidators = validators.concat(simpleEditorValidators(param, errors, fieldName, fieldLabel))

  const formatter = expressionObj.language === ExpressionLang.SpEL ? spelFormatters[param?.typ?.refClazzName] : null

  return (
    <Editor
      {...props}
      editorConfig={param?.editor}
      className={`${valueClassName ? valueClassName : "node-value"}`}
      validators={mergedValidators}
      formatter={formatter}
      expressionInfo={validationLabelInfo}
    />
  )
}

function EditableEditorRow({rowClassName, renderFieldLabel, fieldLabel, ...props}: Props & {
  rowClassName?: string,
  renderFieldLabel?: UnknownFunction,
}): JSX.Element {
  return (
    <div className={`${rowClassName ? rowClassName : " node-row"}`}>
      {fieldLabel && renderFieldLabel?.(fieldLabel)}
      <EditableEditor {...props}/>
    </div>
  )
}

export default EditableEditorRow
