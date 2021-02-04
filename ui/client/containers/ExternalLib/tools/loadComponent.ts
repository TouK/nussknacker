import {Container, Module, PathString, ScopeString} from "../types"

export function loadComponent<M extends Module = Module>(scope: ScopeString, module: PathString): () => Promise<M> {
  return async () => {
    // Initializes the share scope. This fills it with known provided modules from this build and all remotes
    await __webpack_init_sharing__("default")
    const container: Container = window[scope] // or get the container somewhere else
    // Initialize the container, it may provide shared modules
    await container.init(__webpack_share_scopes__.default)
    const factory = await container.get<M>(module)
    return factory()
  }
}
