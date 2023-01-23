import {Container, Module} from "../types"
import {splitUrl} from "./splitUrl"
import {createScript} from "./createScript"

export async function loadComponent<M extends Module = Module>(url: string): Promise<M> {
  const [, , scriptUrl, scope, module] = splitUrl(url)

  // Initializes the share scope. This fills it with known provided modules from this build and all remotes
  await __webpack_init_sharing__("default")

  // load once
  if (!window[scope]) {
    await createScript(`${scriptUrl}?${__BUILD_HASH__}`)
  }

  const container: Container = window[scope] // or get the container somewhere else
  // Initialize the container, it may provide shared modules
  await container.init(__webpack_share_scopes__.default)
  const factory = await container.get<M>(module)
  return factory()
}
