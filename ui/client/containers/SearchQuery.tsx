import React, {PropsWithChildren, useEffect} from "react"
import {useSearchQuery} from "./hooks/useSearchQuery"
import {FiltersState, SearchItem, TableFilters} from "./TableFilters"

type Props = {
  filters?: SearchItem[],
  onChange: (value: FiltersState, prevValue: FiltersState) => void,
}

function SearchQuery(props: PropsWithChildren<Props>) {
  const {onChange} = props
  const [query, setQuery] = useSearchQuery<FiltersState>()
  useEffect(() => {onChange(query, null)}, [])
  return (
    <TableFilters
      filters={props.filters}
      value={query}
      onChange={(value, prevValue) => {
        setQuery(value)
        onChange(value, prevValue)
      }}
    />
  )
}

export const SearchQueryComponent = SearchQuery
