export interface ChangeableValue<T extends Record<string, unknown>> {
  value: T,
  onChange: (value: T) => void,
}
