import {ModuleString, ModuleUrl, PathString, ScopeString, ScriptUrl} from "../types"

export function splitUrl(url: ModuleUrl): [ModuleUrl, ModuleString, ScriptUrl, ScopeString, PathString] {
  const [module, script] = url.split("@")
  const [scope] = module.split("/")
  const path = module.replace(scope, ".")

  if (!scope || !script.match(/^http.*\.js$/)) {
    throw new Error("invalid remote module url")
  }

  return [url, module as ModuleString, script as ScriptUrl, scope as ScopeString, path as PathString]
}
