import {defaults} from "lodash"
import * as queryString from "query-string"
import {ParseOptions} from "query-string"
import {useCallback, useMemo} from "react"
import {defaultArrayFormat, setAndPreserveLocationParams} from "../../common/VisualizationUrl"
import {UnknownRecord} from "../../types/common"
import {useLocation, useNavigate} from "react-router-dom"

export function useSearchQuery<T extends UnknownRecord>(options?: ParseOptions): [T, (v: T) => void] {
  const navigate = useNavigate()
  const location = useLocation()

  const query = useMemo(() => {
    const parsedQuery = queryString.parse(
      location.search,
      defaults(options, {arrayFormat: defaultArrayFormat, parseBooleans: true}),
    )
    return parsedQuery as T
  }, [location.search, options])

  const updateQuery = useCallback((value: T) => {
    navigate({search: setAndPreserveLocationParams(value)}, {replace: true})
  }, [navigate])

  return [query, updateQuery]
}
