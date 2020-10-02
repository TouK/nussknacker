import {defaults} from "lodash"
import * as queryString from "query-string"
import {ParseOptions} from "query-string"
import {useCallback, useMemo} from "react"
import {useHistory} from "react-router"
import {defaultArrayFormat, setAndPreserveLocationParams} from "../../common/VisualizationUrl"
import {UnknownRecord} from "../../types/common"

export function useSearchQuery<T extends UnknownRecord>(options?: ParseOptions): [T, (v: T) => void] {
  const history = useHistory()

  const query = useMemo(() => {
    const parsedQuery = queryString.parse(
      history.location.search,
      defaults(options, {arrayFormat: defaultArrayFormat, parseBooleans: true}),
    )
    return parsedQuery as T
  }, [history.location])

  const updateQuery = useCallback((value: T) => {
    history.replace({search: setAndPreserveLocationParams(value)})
  }, [history])

  return [query, updateQuery]
}
