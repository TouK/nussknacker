import {get} from "lodash"
import {UnknownFunction} from "../../../types/common"
import EditableEditor from "./editors/EditableEditor"
import React from "react"
import {PossibleValue} from "./editors/Validators"

type AdditionalPropertyConfig = {
  editor: any,
  label: string,
  values: Array<PossibleValue>,
}

type Props = {
  showSwitch: boolean,
  showValidation: boolean,
  propertyName: string,
  propertyConfig: AdditionalPropertyConfig,
  propertyErrors: Array<any>,
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
  const current = get(editedNode, `additionalFields.properties.${propertyName}`) || ""
  const expressionObj = {expression: current, value: current, language: "string"}

  return (
    <EditableEditor
      param={propertyConfig}
      fieldLabel={propertyConfig.label || propertyName}
      onValueChange={(newValue) => onChange(`additionalFields.properties.${propertyName}`, newValue)}
      expressionObj={expressionObj}
      renderFieldLabel={renderFieldLabel}
      values={values}
      readOnly={readOnly}
      key={propertyName}
      showSwitch={showSwitch}
      showValidation={showValidation}
      //AdditionalProperties do not use any variables
      variableTypes={{}}
      errors={propertyErrors}
    />
  )
}
