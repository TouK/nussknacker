import React, {createContext, PropsWithChildren, useCallback, useContext, useMemo} from "react"
import {FieldsControl} from "./FieldsControl"
import {NodeRow} from "./NodeRow"
import {NodeValue} from "./NodeValue"

interface FieldsContext {
  add: () => void,
  remove: (index: number) => void,
  readOnly: boolean,
}

interface NodeRowFieldsProps {
  label: string,
  path: string,
  onFieldAdd: (namespace: string) => void,
  onFieldRemove: (namespace: string, index: number) => void,
  readOnly?: boolean,
}

const Context = createContext<FieldsContext>(null)

export function useFieldsContext(): FieldsContext {
  const fieldsContext = useContext(Context)
  if (!fieldsContext) {
    throw new Error(`Used outside <NodeRowFields>!`)
  }
  return fieldsContext
}

export function NodeRowFields({children, ...props}: PropsWithChildren<NodeRowFieldsProps>): JSX.Element {
  const {label, path, onFieldAdd, onFieldRemove, readOnly} = props

  const remove = useCallback((index: number) => onFieldRemove(path, index), [onFieldRemove, path])

  const add = useCallback(() => {
    onFieldAdd(path)
  }, [onFieldAdd, path])

  const ctx = useMemo(
    () => ({add, remove, readOnly}),
    [add, remove, readOnly],
  )

  return (
    <NodeRow label={label}>
      <NodeValue>
        <Context.Provider value={ctx}>
          <FieldsControl readOnly={readOnly}>
            {children}
          </FieldsControl>
        </Context.Provider>
      </NodeValue>
    </NodeRow>
  )
}
