import React, {PropsWithChildren} from "react"
import {AddButton} from "./AddButton"

interface FieldsControlProps {
  readOnly?: boolean,
}

export function FieldsControl(props: PropsWithChildren<FieldsControlProps>): JSX.Element {
  const {readOnly, children} = props
  return (
    <div className="fieldsControl">
      {children}
      {!readOnly && (
        <AddButton/>
      )}
    </div>
  )
}
