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

export enum Status {
  clean,
  ready,
  failed
}

export type ScopeString = string
export type PathString = string
export type ScriptUrl = string
export type ModuleString = string
export type ModuleUrl = string

type Hooks = { [K in `use${Capitalize<string>}`]: ((...args: any[]) => any) }
export type Module = {default?: ComponentType} & Hooks

