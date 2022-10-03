import {Module, ModuleString} from "../types"
import {ExternalLibContextType, Modules} from "./types"

export class ModulesStore implements ExternalLibContextType {
  modules: Modules = {}
  add = <M extends Module>(scope: ModuleString, module: M): void => {
    this.modules = {...this.modules, [scope]: module}
  }
}
