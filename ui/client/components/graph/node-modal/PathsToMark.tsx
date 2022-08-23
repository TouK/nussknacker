import React, {createContext, PropsWithChildren, useCallback, useContext} from "react"
import {startsWith} from "lodash"

const PathsToMarkContext = createContext<string[] | null>(null)

export const PathsToMarkProvider = ({value = [], children}: PropsWithChildren<{ value?: string[] }>): JSX.Element => {
  return (
    <PathsToMarkContext.Provider value={value}>
      {children}
    </PathsToMarkContext.Provider>
  )
}

export function useDiffMark(): [(path?: string) => boolean, boolean] {
  const pathsToMark = useContext(PathsToMarkContext)
  const callback = useCallback((path = ""): boolean => {
    return pathsToMark?.some(toMark => startsWith(toMark, path))
  }, [pathsToMark])
  return [callback, !!pathsToMark]
}
