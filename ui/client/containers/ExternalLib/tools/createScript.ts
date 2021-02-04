import {ScriptUrl} from "../types"

export function createScript(url: ScriptUrl): Promise<void> {
  // eslint-disable-next-line i18next/no-literal-string
  const element = document.createElement("script")
  const promise = new Promise<void>((resolve, reject) => {
    element.src = url
    element.type = "text/javascript"
    element.async = true

    element.onload = () => {
      resolve()
    }

    element.onerror = () => {
      console.error(`Dynamic Script Error: ${url}`)
      reject()
    }

    document.head.appendChild(element)

  }).finally(() => cleanup())

  const cleanup = () => {
    document.head.removeChild(element)
  }

  return promise
}
