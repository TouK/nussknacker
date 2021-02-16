import React, {useContext, useEffect, useState} from "react"

const STORAGE_KEY = "featureFlags"

type FeatureFlags = Record<"showDeploymentsInCounts" | string, boolean>

function getFeatureFlags(): FeatureFlags {
  const stored = localStorage.getItem(STORAGE_KEY)?.split(",") || []
  return Object.fromEntries(stored.filter(Boolean).map(k => [k, true]))
}

function setFeatureFlags(flags: FeatureFlags): void {
  const enabled = Object.entries(flags).filter(([key, value]) => key && value)
  const event = new Event("storage")
  localStorage.setItem(STORAGE_KEY, enabled.join(","))
  window.dispatchEvent(event)
}

const FFContext = React.createContext<FeatureFlags>(null)

export const FFProvider = (props) => {
  const [state, setState] = useState(getFeatureFlags())
  useEffect(() => {
    const handler = () => setState(getFeatureFlags)
    window.addEventListener("storage", handler)
    return () => {
      window.removeEventListener("storage", handler)
    }
  }, [])

  return (
    <FFContext.Provider {...props} value={state}/>
  )
}

export const useFFlags = () => {
  return useContext(FFContext)
}

// expose to console
window["__FF"] = setFeatureFlags
