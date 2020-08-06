import {useMemo} from "react"
import {OptionType} from "./TableSelect"

function parseValue<T>(options: OptionType<T>[], input: T): OptionType<T> | undefined {
  return options.find(o => o.value === input)
}

export function useParseValue<T>(options: OptionType<T>[], input: T): OptionType<T> | undefined {
  return useMemo(
    () => parseValue(options, input),
    [options, input],
  )
}

export function useParseValues<T>(options: OptionType<T>[], input: T[] = []): OptionType<T>[] {
  return useMemo(
    () => input.map(i => parseValue(options, i)).filter(Boolean),
    [options, input],
  )
}
