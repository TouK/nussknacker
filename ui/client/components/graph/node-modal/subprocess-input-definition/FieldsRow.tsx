import React, {PropsWithChildren, useCallback} from "react"
import {NodeRow} from "./NodeRow"
import {useFieldsContext} from "./NodeRowFields"
import {NodeValue} from "./NodeValue"
import {RemoveButton} from "./RemoveButton"

export function FieldsRow({index, children}: PropsWithChildren<{index: number}>): JSX.Element {
  const {readOnly, remove} = useFieldsContext()
  const onClick = useCallback(() => remove(index), [index, remove])
  return (
    <NodeRow className="movable-row">
      {children}
      {!readOnly && (
        <NodeValue className="fieldRemove">
          <RemoveButton onClick={onClick}/>
        </NodeValue>
      )}
    </NodeRow>
  )
}
