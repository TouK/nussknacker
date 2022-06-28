import {isEmpty} from "lodash"
import React, {useMemo} from "react"
import {VariableTypes} from "../../../../types"
import {UnknownFunction} from "../../../../types/common"
import {editors, EditorType, simpleEditorValidators} from "./expression/Editor"
import {spelFormatters} from "./expression/Formatter"
import {ExpressionLang, ExpressionObj} from "./expression/types"
import {ParamType} from "./types"
import {Error} from "./Validators"

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
}

export function EditableEditor(props: Props): JSX.Element {
  const {
    expressionObj, valueClassName, param, fieldLabel,
    errors, fieldName, validationLabelInfo,
  } = props

  const editorType = useMemo(
    () => isEmpty(param) ? EditorType.RAW_PARAMETER_EDITOR : param.editor.type,
    [param]
  )

  const Editor = useMemo(
    () => editors[editorType],
    [editorType]
  )

  const validators = useMemo(
    () => simpleEditorValidators(param, errors, fieldName, fieldLabel),
    [errors, fieldLabel, fieldName, param]
  )

  const formatter = useMemo(
    () => expressionObj.language === ExpressionLang.SpEL ? spelFormatters[param?.typ?.refClazzName] : null,
    [expressionObj.language, param?.typ?.refClazzName]
  )

  return (
    <Editor
      {...props}
      editorConfig={param?.editor}
      className={`${valueClassName ? valueClassName : "node-value"}`}
      validators={validators}
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
