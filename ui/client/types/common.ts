declare global {
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  type $TodoType = any
}

export type UnknownRecord = Record<string, unknown>
export type UnknownFunction = (...args: unknown[]) => unknown

export type WithId<T, I = string> = T & {id: I}
