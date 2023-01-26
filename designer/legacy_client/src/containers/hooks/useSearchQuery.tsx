import {defaults} from "lodash"
import * as queryString from "query-string"
import {ParseOptions} from "query-string"
import {useEffect, useMemo, useState} from "react"
import {useHistory} from "react-router"
import {defaultArrayFormat, setAndPreserveLocationParams} from "../../common/VisualizationUrl"
import {UnknownRecord} from "../../types/common"

export function useSearchQuery<T extends UnknownRecord>(options?: ParseOptions): [T, (v: T) => void] {
  const history = useHistory()

  const parsedQuery = useMemo(
    () => queryString.parse(
      history.location.search,
      defaults(options, {arrayFormat: defaultArrayFormat, parseBooleans: true}),
    ) as T,
    [history.location.search, options]
  )

  const [state, setState] = useState<T>(parsedQuery)

  useEffect(
    () => history.replace({search: setAndPreserveLocationParams(state)}),
    [history, state]
  )

  return [state, setState]
}
