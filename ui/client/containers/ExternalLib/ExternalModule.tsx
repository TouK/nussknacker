import {lazy} from "@loadable/component"
import React, {PropsWithChildren} from "react"
import {SuspenseSpinner} from "../../components/SuspenseSpinner"
import {LibContextProvider} from "./store"
import {loadComponent, splitUrl} from "./tools"
import {Module, ModuleUrl} from "./types"

/**
 * Loads external module (federation) from url. Works as modules context provider.
 * After loading module renders children providing context with module accessible by hooks
 * @param url "url" to module in format `${federatedModuleName}/${exposedModule}@http(...).js`
 * @param children
 * @constructor
 */
export function ExternalModule<M extends Module>({children, url}: PropsWithChildren<{ url: ModuleUrl }>): JSX.Element {
  const [, context] = splitUrl(url)
  const LoadedLib = lazy.lib(async () => loadComponent(url))

  return (
    <SuspenseSpinner>
      <LoadedLib>
        {(lib: M) => (
          <LibContextProvider<M> lib={lib} scope={context}>
            {children}
          </LibContextProvider>
        )}
      </LoadedLib>
    </SuspenseSpinner>
  )
}
