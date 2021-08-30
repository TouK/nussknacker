import React, {PropsWithChildren} from "react"
import {ButtonWithFocus} from "../../../withFocus"
import {useFieldsContext} from "./NodeRowFields"

interface FieldsControlProps {
  readOnly?: boolean,
}

export function FieldsControl(props: PropsWithChildren<FieldsControlProps>): JSX.Element {
  const {readOnly, children} = props
  const {add} = useFieldsContext()
  return (
    <div className="fieldsControl">
      {children}
      {!readOnly && (
        <div>
          <ButtonWithFocus className="addRemoveButton" title="Add field" onClick={add}>+</ButtonWithFocus>
        </div>
      )}
    </div>
  )
}
