/* eslint-disable i18next/no-literal-string */
import React, {createContext, PropsWithChildren, useContext} from "react"
import Graph from "./Graph"

const GraphContext = createContext<() => Graph>(null)

export const GraphProvider = ({graph, children}: PropsWithChildren<{ graph: () => Graph }>) => (
  <GraphContext.Provider value={graph}>
    {children}
  </GraphContext.Provider>
)

export const useGraph = () => {
  const graphGetter = useContext(GraphContext)
  if (!graphGetter) {
    throw "no graph getter provided!"
  }
  return graphGetter
}
