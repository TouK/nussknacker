import React, {PropsWithChildren} from "react"
import {ButtonWithFocus} from "../../../withFocus"
import {NodeRow} from "./NodeRow"
import {useFieldsContext} from "./NodeRowFields"
import {NodeValue} from "./NodeValue"

export function FieldsRow({index, children}: PropsWithChildren<{index: number}>): JSX.Element {
  const {readOnly, remove} = useFieldsContext()
  return (
    <NodeRow className="movable-row">
      {children}
      {!readOnly && (
        <NodeValue className="fieldRemove">
          <ButtonWithFocus
            className="addRemoveButton"
            title="Remove field"
            onClick={() => remove(index)}
          >-
          </ButtonWithFocus>
        </NodeValue>
      )}

    </NodeRow>
  )
}
