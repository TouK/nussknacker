import React, {useContext, useEffect, useState} from "react"

const STORAGE_KEY = "featureFlags"

type FeatureFlags = Record<"showDeploymentsInCounts" | string, boolean>

function getFeatureFlags(): FeatureFlags {
  const stored = localStorage.getItem(STORAGE_KEY)?.split(",") || []
  const entries = stored.filter(Boolean).map(k => [k, true])
  return Object.fromEntries(entries)
}

function setFeatureFlags(flags: FeatureFlags): void {
  const enabled = Object.entries(flags).filter(([key, value]) => key && value).map(([key]) => key)
  const event = new Event("storage")
  localStorage.setItem(STORAGE_KEY, enabled.join(","))
  window.dispatchEvent(event)
}

function toggleFeatureFlags(keys: Array<keyof FeatureFlags>): void {
  const current = getFeatureFlags()
  const flags = keys.reduce((previousValue, key) => ({...previousValue, [key]: !current[key]}), {})
  setFeatureFlags({...current, ...flags})
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

export const useFFlags: () => [FeatureFlags, typeof toggleFeatureFlags] = () => {
  const current = useContext(FFContext)
  return [current, toggleFeatureFlags]
}

// expose to console
window["__FF"] = setFeatureFlags
