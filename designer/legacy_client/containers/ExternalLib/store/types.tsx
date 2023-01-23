import {Module, ModuleString} from "../types"

export type Modules = Record<ModuleString, Module>

export interface ExternalLibContextType<M extends Module = Module> {
  modules: Modules,
  add: (scope: ModuleString, module: M) => void,
}
