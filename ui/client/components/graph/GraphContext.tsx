/* eslint-disable i18next/no-literal-string */
import React, {createContext, PropsWithChildren, useContext} from "react"
import {Graph} from "./Graph"

type GraphContextType = () => Graph

const GraphContext = createContext<GraphContextType>(null)

export const GraphProvider = ({graph, children}: PropsWithChildren<{ graph: GraphContextType }>) => (
  <GraphContext.Provider value={graph}>
    {children}
  </GraphContext.Provider>
)

export const useGraph = (): GraphContextType => {
  const graphGetter = useContext(GraphContext)
  if (!graphGetter) {
    throw "no graph getter provided!"
  }
  return graphGetter
}

