import React, {useMemo} from "react"
import {Field} from "../../../../../types"
import {mandatoryValueValidator} from "../Validators"
import {MapCommonProps, TypedField} from "./Map"
import MapKey from "./MapKey"
import MapValue from "./MapValue"
import {useDiffMark} from "../../PathsToMark"

interface MapRowProps<F extends Field> extends MapCommonProps {
  field: F,
  path: string,
}

export default function MapRow<F extends TypedField>({field, path, ...props}: MapRowProps<F>): JSX.Element {
  const {readOnly, showValidation, onChange, fieldErrors, variableTypes} = props
  const validators = useMemo(() => [mandatoryValueValidator], [])
  const [isMarked] = useDiffMark()

  return (
    <>
      <MapKey
        readOnly={readOnly}
        showValidation={showValidation}
        isMarked={isMarked(`${path}.name`)}
        onChange={(value) => onChange(`${path}.name`, value)}
        value={field.name}
        validators={validators}
      />
      <MapValue
        readOnly={readOnly}
        showValidation={showValidation}
        isMarked={isMarked(`${path}.expression.expression`)}
        onChange={value => onChange(`${path}.expression.expression`, value)}
        validationLabelInfo={field.typeInfo}
        value={field.expression}
        errors={fieldErrors}
        variableTypes={variableTypes}
      />
    </>
  )
}
