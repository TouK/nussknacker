/* eslint-disable i18next/no-literal-string */
import React, {createContext, PropsWithChildren, SyntheticEvent, useContext} from "react"
import {Graph} from "./Graph"

type GraphContextType = () => Graph

const GraphContext = createContext<GraphContextType>(null)

type SelectionActions = {
  copy: (event: SyntheticEvent) => void,
  canCopy: boolean,
  cut: (event: SyntheticEvent) => void,
  canCut: boolean,
  paste: (event: SyntheticEvent) => void,
  canPaste: boolean,
}

const SelectionContext = createContext<SelectionActions>(null)

export const GraphProvider = ({graph, selectionActions, children}: PropsWithChildren<{ graph: GraphContextType, selectionActions: SelectionActions }>) => (
  <GraphContext.Provider value={graph}>
    <SelectionContext.Provider value={selectionActions}>
      {children}
    </SelectionContext.Provider>
  </GraphContext.Provider>
)

export const useGraph = (): GraphContextType => {
  const graphGetter = useContext(GraphContext)
  if (!graphGetter) {
    throw "no graph getter provided!"
  }
  return graphGetter
}

export const useSelectionActions = (): SelectionActions => {
  const selectionActions = useContext(SelectionContext)
  if (!selectionActions) {
    return {
      canCopy: false,
      canCut: false,
      canPaste: false,
      cut() {return},
      copy() {return},
      paste() {return},
    }
  }
  return selectionActions
}
