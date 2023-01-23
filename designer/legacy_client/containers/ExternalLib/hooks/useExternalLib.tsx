import {useContext} from "react"
import {Module, ModuleString} from "../types"
import {ExternalLibContext} from "../store/context"
import {ExternalLibContextType} from "../store/types"

export function useExternalLib<M extends Module = Module>(scope?: ModuleString): {module: M, context: ExternalLibContextType} {
  const context = useContext(ExternalLibContext)
  const module = context?.modules[scope] as M

  if (scope && !module) {
    throw new Error("useExternalLib must be used within a ExternalLibContext.Provider. Module not loaded.")
  }

  return {module, context}
}
