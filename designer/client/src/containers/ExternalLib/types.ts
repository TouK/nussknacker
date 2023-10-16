import { ComponentType } from "react";
import type { Opaque } from "type-fest";

export interface Container {
    init(scope: unknown): Promise<unknown>;

    get<M = { default: ComponentType<any> }>(module: PathString): Promise<() => M>;
}

/**
 * remote name from ModuleFederationPlugin
 */
export type ScopeString = Opaque<string, "ScopeString">;

/**
 * remote exposed module "path" from ModuleFederationPlugin
 */
export type PathString = Opaque<string, "PathString">;

/**
 * url to remote entry .js file
 */
export type ScriptUrl = Opaque<string, "ScriptUrl">;

/**
 * query from remote entry url
 */
export type QueryString = Opaque<string, "QueryString">;

/**
 * `${ScopeString}/${PathString}`
 */
export type ModuleString = Opaque<string, "ModuleString">;

/**
 * `${ModuleString}@${ScriptUrl}`
 */
export type ModuleUrl = Opaque<string, "ModuleUrl">;

type Hooks = Record<`use${Capitalize<string>}`, (...args: any[]) => any>;
export type Module = { default?: ComponentType<any> } & Hooks;
