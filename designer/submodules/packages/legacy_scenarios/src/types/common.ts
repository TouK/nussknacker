declare global {
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  type $TodoType = any
}

export type UnknownRecord = Record<string, unknown>

//from BE comes as date in zulu time
export type Instant = string
