import {isEqual} from "lodash"
import {useCallback, useMemo} from "react"
import {useDebounce} from "use-debounce"
import {normalizeParams} from "../../common/VisualizationUrl"
import HttpService from "../../http/HttpService"
import {useFetch} from "../hooks/useFetch"
import {FiltersState} from "../TableFilters"
import {Queries} from "./types"

export function useFilteredProcesses(filters: FiltersState & Queries) {
  const normalizedFilters = useMemo(() => filters && normalizeParams(filters), [filters])
  const [params] = useDebounce(normalizedFilters, 200, {equalityFn: isEqual})

  const fetchAction = useCallback(() => {
    if (params) {
      const {...rest} = params
      return HttpService.fetchProcesses(rest)
    }
  }, [params])

  const [processes, getProcesses, isLoading] = useFetch(fetchAction, [])
  return {processes, getProcesses, isLoading}
}
