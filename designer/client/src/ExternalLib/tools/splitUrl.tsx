import { ModuleString, ModuleUrl, PathString, QueryString, ScopeString, ScriptUrl } from "../types";

/**
 * Split ExternalModule url to url and module parts.
 * @param fullModuleUrl
 */
export function splitUrl(fullModuleUrl: ModuleUrl): [ModuleUrl, ModuleString, ScriptUrl, ScopeString, PathString, QueryString] {
    const [module, url] = fullModuleUrl.split("@");
    const [scope] = module.split("/");
    const path = module.replace(scope, ".");
    const [script, query] = url.split("?");

    if (!scope || !script.match(/\.js$/)) {
        throw new Error("invalid remote module url");
    }

    return [fullModuleUrl, module as ModuleString, script as ScriptUrl, scope as ScopeString, path as PathString, query as QueryString];
}
