import { ComponentType } from "react";

export interface Container {
    init(scope: unknown): Promise<unknown>;

    get<M = { default: ComponentType<any> }>(module: PathString): Promise<() => M>;
}

/**
 * remote name from ModuleFederationPlugin
 */
export type ScopeString = string;

/**
 * remote exposed module "path" from ModuleFederationPlugin
 */
export type PathString = string;

/**
 * url to remote entry .js file
 */
export type ScriptUrl = string;

/**
 * `${ScopeString}/${PathString}`
 */
export type ModuleString = string;

/**
 * `${ModuleString}@${ScriptUrl}`
 */
export type ModuleUrl = string;

type Hooks = Record<`use${Capitalize<string>}`, (...args: any[]) => any>;
export type Module = { default?: ComponentType<any> } & Hooks;
