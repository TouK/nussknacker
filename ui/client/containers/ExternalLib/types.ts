import {ComponentType} from "react"

declare global {
  let __webpack_init_sharing__: (name: string) => unknown
  let __webpack_share_scopes__: {
    [name: string]: unknown,
    default: unknown,
  }
}

export interface Container {
  init(scope: unknown): Promise<unknown>,

  get<M = {default: ComponentType<any>}>(module: PathString): Promise<() => M>,
}

/**
 * remote name from ModuleFederationPlugin
 */
export type ScopeString = string

/**
 * remote exposed module "path" from ModuleFederationPlugin
 */
export type PathString = string

/**
 * url to remote entry .js file
 */
export type ScriptUrl = string

/**
 * `${ScopeString}/${PathString}`
 */
export type ModuleString = string

/**
 * `${ModuleString}@${ScriptUrl}`
 */
export type ModuleUrl = string

type Hooks = { [K in `use${Capitalize<string>}`]: ((...args: any[]) => any) }
export type Module = {default?: ComponentType} & Hooks

