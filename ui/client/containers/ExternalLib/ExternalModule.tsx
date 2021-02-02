import {lazy} from "@loadable/component"
import React, {PropsWithChildren, useMemo} from "react"
import {LibContextProvider} from "./store"
import {createScript, loadComponent, splitUrl} from "./tools"
import {Module, ModuleUrl} from "./types"

/**
 * Loads external module (federation) from url. Works as modules context provider.
 * After loading module renders children providing context with module accessible by hooks
 * @param url "url" to module in format `${federatedModuleName}/${exposedModule}@http(...).js`
 * @param children
 * @constructor
 */
export function ExternalModule<M extends Module>({children, url}: PropsWithChildren<{url: ModuleUrl}>): JSX.Element {
  const [, context, scriptUrl, scope, module] = splitUrl(url)

  const LoadedLib = useMemo(() => lazy.lib(async () => {
    await createScript(scriptUrl)
    const loader = await loadComponent(scope, module)
    return loader()
  }), [scriptUrl, scope, module])

  return (
    <LoadedLib>
      {(lib: M) => (
        <LibContextProvider<M> lib={lib} scope={context}>
          {children}
        </LibContextProvider>
      )}
    </LoadedLib>
  )
}
