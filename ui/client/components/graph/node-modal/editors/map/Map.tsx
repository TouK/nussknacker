import React from "react"
import {Field, TypedObjectTypingResult, VariableTypes} from "../../../../../types"
import {FieldsRow} from "../../subprocess-input-definition/FieldsRow"
import {NodeRowFields} from "../../subprocess-input-definition/NodeRowFields"
import {Error} from "../Validators"
import MapRow from "./MapRow"

export interface MapCommonProps {
  isMarked: (paths: string) => boolean,
  onChange: (path: string, newValue: unknown) => void,
  readOnly?: boolean,
  showValidation: boolean,
  variableTypes: VariableTypes,
  errors: Error[],
}

interface MapProps<F extends Field> extends MapCommonProps {
  fields: F[],
  label: string,
  namespace: string,
  addField: (namespace: string, field?: F) => void,
  removeField: (namespace: string, index: number) => void,
  expressionType?: TypedObjectTypingResult,
}

export type TypedField = Field & {typeInfo: string}

export function Map<F extends Field>(props: MapProps<F>): JSX.Element {
  const {
    label, fields, onChange, addField, removeField, namespace, isMarked, readOnly, showValidation,
    errors, variableTypes, expressionType,
  } = props

  const fieldsWithTypeInfo: Array<F & {typeInfo: string}> = fields.map(expressionObj => {
    const fields = expressionType?.fields
    const typeInfo = fields ? fields[expressionObj.name]?.display : expressionType?.display
    return {...expressionObj, typeInfo: typeInfo}
  })

  return (
    <NodeRowFields
      label={label}
      path={namespace}
      onFieldAdd={addField}
      onFieldRemove={removeField}
      readOnly={readOnly}
    >
      {fieldsWithTypeInfo.map((field, index) => (
        <FieldsRow key={index} index={index}>
          <MapRow
            field={field}
            path={`${namespace}[${index}]`}
            readOnly={readOnly}
            showValidation={showValidation}
            onChange={onChange}
            isMarked={isMarked}
            errors={errors}
            variableTypes={variableTypes}
          />
        </FieldsRow>
      ))}
    </NodeRowFields>
  )
}

export default Map
