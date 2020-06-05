import SearchFilter from "../components/table/SearchFilter"
import {CategoriesFilter} from "../components/table/CategoriesFilter"
import {SubprocessFilter} from "../components/table/SubprocessFilter"
import {DeployedFilter} from "../components/table/DeployedFilter"
import React, {useState, useEffect, useCallback} from "react"
import {isEqual, omitBy} from "lodash"

export enum SearchItem {
  categories = "categories",
  isSubprocess = "isSubprocess",
  isDeployed = "isDeployed",
}

export type FiltersState = {
  search?: string,
  categories?: string[],
  isSubprocess?: boolean,
  isDeployed?: boolean,
}

type Props = {
  filters: SearchItem[],
  value: FiltersState,
  onChange: (value: FiltersState) => void,
}

function getDiff(current: FiltersState, base: FiltersState): Partial<FiltersState> {
  return omitBy(current, (value, key) => isEqual(value, base[key]))
}

export function TableFilters(props: Props) {
  const {filters} = props
  const {value, onChange} = props

  const [state, setState] = useState<FiltersState>(value)
  const updateState = useCallback((partial: Partial<FiltersState>) => {
    setState(prev => ({...prev, ...partial}))
  }, [])

  useEffect(() => {
    if (!isEqual(value, state)) {
      onChange(getDiff(state, value))
    }
  }, [state])

  return (
    <>
      <SearchFilter
        onChange={search => updateState({search})}
        value={state.search}
      />

      {filters.includes(SearchItem.categories) && (
        <CategoriesFilter
          onChange={categories => updateState({categories})}
          value={state.categories}
        />
      )}

      {filters.includes(SearchItem.isSubprocess) && (
        <SubprocessFilter
          onChange={isSubprocess => updateState({isSubprocess})}
          value={state.isSubprocess}
        />
      )}

      {filters.includes(SearchItem.isDeployed) && (
        <DeployedFilter
          onChange={isDeployed => updateState({isDeployed})}
          value={state.isDeployed}
        />
      )}
    </>
  )
}
