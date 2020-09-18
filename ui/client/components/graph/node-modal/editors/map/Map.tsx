import React from "react"
import {ButtonWithFocus} from "../../../../withFocus"
import MapRow from "./MapRow"
import {Field, TypedObjectTypingResult, VariableTypes} from "../../../../../types"
import {Error} from "../Validators"

type Props = {
  fields: Array<Field>,
  label: string,
  namespace: string,
  isMarked: (paths: string) => boolean,
  onChange: (propToMutate: $TodoType, newValue: $TodoType, defaultValue?: $TodoType) => void,
  addField: $TodoType,
  removeField: (namespace: string, ix: number) => void,
  readOnly?: boolean,
  showValidation: boolean,
  showSwitch?: boolean,
  variableTypes: VariableTypes,
  errors: Array<Error>,
  expressionType?: TypedObjectTypingResult,
}

const Map = (props: Props) => {

  const {
    label, fields, onChange, addField, removeField, namespace, isMarked, readOnly, showValidation,
    showSwitch, errors, variableTypes, expressionType,
  } = props

  const fieldsWithTypeInfo: Array<Field & {typeInfo: string}> = fields.map(expressionObj => ({
    ...expressionObj,
    typeInfo: expressionType?.fields[expressionObj.name]?.display,
  }))

  return (
    <div className="node-row">
      <div className="node-label" title={label}>{label}:</div>
      <div className="node-value">
        <div className="fieldsControl">
          {
            fieldsWithTypeInfo.map((field, index) => (
              <MapRow
                key={index}
                field={field}
                showValidation={showValidation}
                showSwitch={showSwitch}
                readOnly={readOnly}
                paths={`${namespace}[${index}]`}
                isMarked={isMarked}
                onChange={onChange}
                onRemoveField={() => removeField(namespace, index)}
                errors={errors}
                variableTypes={variableTypes}
                validationLabelInfo={field.typeInfo}
              />
            ))
          }
          {
            readOnly ? null : (
              <div>
                <ButtonWithFocus
                  onClick={addField}
                  className="addRemoveButton"
                  title="Add field"
                >+
                </ButtonWithFocus>
              </div>
            )}
        </div>
      </div>
    </div>
  )
}

Map.defaultProps = {
  readOnly: false,
}

export default Map
