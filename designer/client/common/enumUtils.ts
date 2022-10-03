export function getStringEnumElement<T, K>(Type: T, value: K): T[K extends T[keyof T] ? keyof T : never] {
  const key = Object.entries(Type).find(([, v]) => v === value)?.shift()
  return Type[key]
}
