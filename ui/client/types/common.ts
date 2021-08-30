declare global {
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  type $TodoType = any
}

export type UnknownRecord = Record<string, unknown>
export type UnknownFunction = (...args: unknown[]) => unknown

export type WithId<T, K extends string = "id"> = T & {[key in K]: string}
export type WithUuid<T> = WithId<T, "uuid">
