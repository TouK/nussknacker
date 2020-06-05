import {Option} from "./FilterTypes"
import {useMemo} from "react"

function parseValue<T>(options: Option<T>[], input: T): Option<T> | undefined {
  return options.find(o => o.value === input)
}

export function useParseValue<T>(options: Option<T>[], input: T): Option<T> | undefined {
  return useMemo(
    () => parseValue(options, input),
    [options, input],
  )
}

export function useParseValues<T>(options: Option<T>[], input: T[]): Option<T>[] {
  return useMemo(
    () => input.map(i => parseValue(options, i)).filter(Boolean),
    [options, input],
  )
}
