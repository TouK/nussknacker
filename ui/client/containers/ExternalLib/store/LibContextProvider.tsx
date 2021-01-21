import React, {PropsWithChildren} from "react"
import {Module, ModuleString} from "../types"
import {ExternalLibContext} from "./context"
import {ModulesStore} from "./modulesStore"
import {useExternalLib} from "../hooks"

interface Props<M extends Module> {
  lib: M,
  scope: ModuleString,
}

export function LibContextProvider<M extends Module>({lib, scope, children}: PropsWithChildren<Props<M>>): JSX.Element {
  const {context} = useExternalLib()

  if (context) {
    context.add(scope, lib)
    return <>{children}</>
  }

  const modulesStore = new ModulesStore()
  modulesStore.add(scope, lib)

  return (
    <ExternalLibContext.Provider value={modulesStore}>
      {children}
    </ExternalLibContext.Provider>
  )
}
