import {defaultsDeep, isEqual} from "lodash"
import React, {useEffect} from "react"
import {CategoriesFilter} from "../components/table/CategoriesFilter"
import {DeployedFilter} from "../components/table/DeployedFilter"
import SearchFilter from "../components/table/SearchFilter"
import {SubprocessFilter} from "../components/table/SubprocessFilter"
import {ensureArray} from "./EnsureArray"
import {usePrevious} from "./hooks/usePrevious"
import {useStateInSync} from "./hooks/useStateInSync"
import {NkThemeProvider} from "./theme"

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

export function TableFilters(props: Props) {
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
      <NkThemeProvider theme={outerTheme => defaultsDeep({}, outerTheme)}>
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
          <DeployedFilter
            onChange={isDeployed => setState(s => ({...s, isDeployed}))}
            value={state.isDeployed}
          />
        )}
      </NkThemeProvider>
    </>
  )
}

