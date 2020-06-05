export type FilterProps<T = string> = {
  onChange: (element: T) => void,
  value: T,
}

export type MultiSelectFilterProps<T> = FilterProps<T[]>

export type Option<T> = {
  label: string,
  value?: T,
}
