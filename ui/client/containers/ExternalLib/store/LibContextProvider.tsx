import React, {PropsWithChildren} from "react"
import {Module, ModuleString} from "../types"
import {ExternalLibContext} from "./context"
import {ModulesStore} from "./modulesStore"
import {useExternalLib} from "../hooks"

interface Props<M extends Module> {
  lib: M,
  scope: ModuleString,
}

/**
 * Module context provider. If store is available in context extend existing store with module.
 * @param lib module to store in context
 * @param scope name of module - based on remote ModuleFederationPlugin config
 * @param children
 * @constructor
 */
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
