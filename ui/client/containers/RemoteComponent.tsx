/* eslint-disable i18next/no-literal-string */
import React, {ComponentType} from "react"

declare let __webpack_init_sharing__: (name: string) => unknown
declare let __webpack_share_scopes__: {
  [name: string]: unknown,
  default: unknown,
}

interface Container {
  init(scope: unknown): Promise<unknown>,

  get<C = ComponentType<any>>(module: string): Promise<() => {default: C}>,
}

function loadComponent<C = ComponentType<any>>(scope: string, module: string) {
  return async () => {
    // Initializes the share scope. This fills it with known provided modules from this build and all remotes
    await __webpack_init_sharing__("default")

    const container: Container = window[scope] // or get the container somewhere else
    // Initialize the container, it may provide shared modules
    await container.init(__webpack_share_scopes__.default)
    const factory = await container.get<C>(module)
    const Module = factory()
    return Module
  }
}

const useDynamicScript = (args: {url: string}) => {
  const [ready, setReady] = React.useState(false)
  const [failed, setFailed] = React.useState(false)

  React.useEffect(() => {
    if (!args.url) {
      return
    }

    const element = document.createElement("script")

    element.src = args.url
    element.type = "text/javascript"
    element.async = true

    setReady(false)
    setFailed(false)

    element.onload = () => {
      console.log(`Dynamic Script Loaded: ${args.url}`)
      setReady(true)
    }

    element.onerror = () => {
      console.error(`Dynamic Script Error: ${args.url}`)
      setReady(false)
      setFailed(true)
    }

    document.head.appendChild(element)

    return () => {
      console.log(`Dynamic Script Removed: ${args.url}`)
      document.head.removeChild(element)
    }
  }, [args.url])

  return {
    ready,
    failed,
  }
}

export function RemoteComponent(props: {
  url: string,
  scope: string,
  module: string,
}): JSX.Element {
  const {ready, failed} = useDynamicScript({url: props.url})

  if (!props.url || !props.scope) {
    return <h2>Not system specified</h2>
  }

  if (!ready) {
    return <h2>Loading dynamic script: {props.url}</h2>
  }

  if (failed) {
    return <h2>Failed to load dynamic script: {props.url}</h2>
  }

  const Component = React.lazy(
    loadComponent(props.scope, props.module),
  )

  return (
    <React.Suspense fallback="Loading...">
      <Component/>
    </React.Suspense>
  )
}
