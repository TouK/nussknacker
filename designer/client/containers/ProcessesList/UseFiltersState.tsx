import {useCallback, useMemo, useState} from "react"
import {FiltersState} from "../TableFilters"
import {Queries} from "./types"

interface UseFiltersState {
  (defaultQuery: Queries): {
    search: string,
    setFilters: (value: Partial<FiltersState>) => void,
    filters: Omit<FiltersState & Queries, "search">,
  },
}

export const useFiltersState: UseFiltersState = (defaultQuery) => {
  const [_filters, _setFilters] = useState<FiltersState & Queries>({})
  const setFilters: (value: Partial<FiltersState>) => void = useCallback(value => {
    _setFilters({...value, ...defaultQuery})
  }, [_setFilters, defaultQuery])

  const [search, filters] = useMemo(() => {
    if (_filters) {
      const {search, ...filters} = _filters
      return [search, filters]
    }
    return [null, null]
  }, [_filters])
  return {search, filters, setFilters}
}
