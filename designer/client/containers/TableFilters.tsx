import {isEqual} from "lodash"
import React, {useEffect} from "react"
import {CategoriesFilter} from "../components/table/CategoriesFilter"
import {StatusFilter} from "../components/table/StatusFilter"
import SearchFilter from "../components/table/SearchFilter"
import {SubprocessFilter} from "../components/table/SubprocessFilter"
import {ensureArray} from "../common/arrayUtils"
import {usePrevious} from "./hooks/usePrevious"
import {useStateInSync} from "./hooks/useStateInSync"

export enum SearchItem {
  categories = "categories",
  isSubprocess = "isSubprocess",
  isDeployed = "isDeployed",
}

export type CategoryName = string

export type FiltersState = Partial<{
  search: string,
  categories: CategoryName[],
  isSubprocess: boolean,
  isDeployed: boolean,
}>

type Props = {
  filters: SearchItem[],
  value: FiltersState,
  onChange: (value: FiltersState, prevValue: FiltersState) => void,
}

export function TableFilters(props: Props): JSX.Element {
  const {filters = []} = props
  const {value, onChange} = props

  const [state, setState] = useStateInSync<FiltersState>(value)
  const prev = usePrevious(state)

  useEffect(() => {
    if (!isEqual(value, state)) {
      onChange(state, prev)
    }
  }, [state])

  return (
    <>
      <SearchFilter
        onChange={search => setState(s => ({...s, search}))}
        value={state.search}
      />

      {filters.includes(SearchItem.categories) && (
        <CategoriesFilter
          onChange={categories => setState(s => ({...s, categories}))}
          value={ensureArray(state.categories)}
        />
      )}

      {filters.includes(SearchItem.isSubprocess) && (
        <SubprocessFilter
          onChange={isSubprocess => setState(s => ({...s, isSubprocess}))}
          value={state.isSubprocess}
        />
      )}

      {filters.includes(SearchItem.isDeployed) && (
        <StatusFilter
          onChange={isDeployed => setState(s => ({...s, isDeployed}))}
          value={state.isDeployed}
        />
      )}
    </>
  )
}

