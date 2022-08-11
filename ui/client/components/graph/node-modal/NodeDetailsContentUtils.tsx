import {NodeType, ProcessDefinitionData, UIParameter} from "../../../types"
import ProcessUtils from "../../../common/ProcessUtils"
import React, {SetStateAction, useCallback, useEffect, useRef, useState} from "react"

export function getParameterDefinitions(processDefinitionData: ProcessDefinitionData, node: NodeType, dynamicParameterDefinitions?: UIParameter[]): UIParameter[] {
  return dynamicParameterDefinitions || ProcessUtils.findNodeObjectTypeDefinition(node, processDefinitionData.processDefinition)?.parameters
}

export function hasOutputVar(node: NodeType, processDefinitionData: ProcessDefinitionData): boolean {
  const returnType = ProcessUtils.findNodeObjectTypeDefinition(node, processDefinitionData.processDefinition)?.returnType
  return !!returnType || !!node.outputVar
}

type Callback<T> = (value?: T) => void
export type DispatchWithCallback<T> = (value: T, callback?: Callback<T>) => void

export function useStateCallback<T>(initialState: T | (() => T)): [T, DispatchWithCallback<SetStateAction<T>>] {
  const [state, _setState] = useState(initialState)

  const callbackRef = useRef<Callback<T>>()
  const isFirstCallbackCall = useRef(true)

  const setState = useCallback((setStateAction: SetStateAction<T>, callback?: Callback<T>): void => {
    callbackRef.current = callback
    _setState(setStateAction)
  }, [])

  useEffect(() => {
    if (isFirstCallbackCall.current) {
      isFirstCallbackCall.current = false
      return
    }
    callbackRef.current?.(state)
  }, [state])

  return [state, setState]
}
