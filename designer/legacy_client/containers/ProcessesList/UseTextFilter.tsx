import {useMemo} from "react"
import {ProcessType} from "../../components/Process/types"
import {Filterable} from "./types"

export function useTextFilter(search: string, processes: ProcessType[], filterable: Filterable): ProcessType[] {
  return useMemo(
    () => {
      const searchText = search?.toString().toLowerCase()
      return searchText ?
        processes.filter(p => filterable?.some(f => p[f]?.toString().toLowerCase().includes(searchText))) :
        processes
    },
    [filterable, search, processes],
  )
}
