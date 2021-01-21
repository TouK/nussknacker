import {lazy} from "@loadable/component"
import React, {PropsWithChildren, useMemo} from "react"
import {LibContextProvider} from "./store"
import {createScript, loadComponent, splitUrl} from "./tools"
import {Module, ModuleUrl} from "./types"

export function ExternalModule<M extends Module>(props: PropsWithChildren<{url: ModuleUrl}>): JSX.Element {
  const {url, children} = props
  const [, context, scriptUrl, scope, module] = splitUrl(url)

  const LoadedLib = useMemo(() => lazy.lib(async () => {
    await createScript(scriptUrl)
    const loader = await loadComponent(scope, module)
    return loader()
  }), [scriptUrl, scope, module])

  return (
    <LoadedLib>
      {(lib: M) => {
        return (
          <LibContextProvider<M> lib={lib} scope={context}>
            {children}
          </LibContextProvider>
        )
      }}
    </LoadedLib>
  )
}
