import React, {PropsWithChildren, useCallback} from "react"
import {NodeRow} from "./NodeRow"
import {useFieldsContext} from "./NodeRowFields"
import {NodeValue} from "./NodeValue"
import {RemoveButton} from "./RemoveButton"
import {cx} from "@emotion/css"

export function FieldsRow({index, className, children}: PropsWithChildren<{index: number, className?: string}>): JSX.Element {
  const {readOnly, remove} = useFieldsContext()
  const onClick = useCallback(() => remove?.(index), [index, remove])
  return (
    <NodeRow className={cx("movable-row", className)} data-testid={`fieldsRow:${index}`}>
      {children}
      {!readOnly && remove && (
        <NodeValue className="fieldRemove">
          <RemoveButton onClick={onClick}/>
        </NodeValue>
      )}
    </NodeRow>
  )
}
