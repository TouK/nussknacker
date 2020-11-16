import React, {PropsWithChildren, useEffect} from "react"
import {useSearchQuery} from "./hooks/useSearchQuery"
import {FiltersState, SearchItem, TableFilters} from "./TableFilters"

type Props = {
  filters?: SearchItem[],
  onChange: (value: FiltersState) => void,
}

function SearchQuery(props: PropsWithChildren<Props>): JSX.Element {
  const {onChange} = props
  const [query, setQuery] = useSearchQuery<FiltersState>()
  useEffect(() => {onChange(query)}, [query, onChange])
  return (
    <TableFilters
      filters={props.filters}
      value={query}
      onChange={setQuery}
    />
  )
}

export const SearchQueryComponent = SearchQuery
