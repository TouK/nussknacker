import {ScriptUrl} from "../types"

export function createScript(url: ScriptUrl): Promise<void> {
  const element = document.createElement(`script`)

  return new Promise<void>((resolve, reject) => {
    element.src = url
    element.type = "text/javascript"
    element.async = true

    element.onload = () => {
      resolve()
      // setTimeout to ensure full init (e.g. relying on document.currentScript etc) before remove
      setTimeout(() => document.head.removeChild(element))
    }

    element.onerror = () => {
      console.error(`Dynamic Script Error: ${url}`)
      reject()
    }

    document.head.appendChild(element)

  })
}
