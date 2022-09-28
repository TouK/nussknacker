import {get} from "lodash"
import {UnknownFunction} from "../../../types/common"
import EditableEditor from "./editors/EditableEditor"
import React, {useCallback} from "react"
import {ExpressionLang} from "./editors/expression/types"
import {errorValidator, PossibleValue} from "./editors/Validators"
import {NodeValidationError} from "../../../types";

export interface AdditionalPropertyConfig {
  editor: any,
  label: string,
  values: Array<PossibleValue>,
}

interface Props {
  showSwitch: boolean,
  showValidation: boolean,
  propertyName: string,
  propertyConfig: AdditionalPropertyConfig,
  propertyErrors: NodeValidationError[],
  editedNode: any,
  onChange: UnknownFunction,
  renderFieldLabel: UnknownFunction,
  readOnly: boolean,
}

export default function AdditionalProperty(props: Props) {

  const {
    showSwitch, showValidation, propertyName, propertyConfig, propertyErrors, editedNode, onChange, renderFieldLabel,
    readOnly,
  } = props

  const values = propertyConfig.values?.map(value => ({expression: value, label: value}))
  let propertyPath = `additionalFields.properties.${propertyName}`;
  const current = get(editedNode, propertyPath) || ""
  const expressionObj = {expression: current, value: current, language: ExpressionLang.String}
  
  let validator = errorValidator(propertyErrors, propertyName);
  const onValueChange = useCallback((newValue) => onChange(propertyPath, newValue), [onChange, propertyName])

  return (
    <EditableEditor
      param={propertyConfig}
      fieldName={propertyName}
      fieldLabel={propertyConfig.label || propertyName}
      onValueChange={onValueChange}
      expressionObj={expressionObj}
      renderFieldLabel={renderFieldLabel}
      values={values}
      readOnly={readOnly}
      key={propertyName}
      showSwitch={showSwitch}
      showValidation={showValidation}
      //AdditionalProperties do not use any variables
      variableTypes={{}}
      validators={[validator]}
    />
  )
}
