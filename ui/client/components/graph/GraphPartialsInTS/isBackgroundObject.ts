import {dia} from "jointjs"

export function isBackgroundObject(cell: dia.Cell): boolean {
  // eslint-disable-next-line i18next/no-literal-string
  return !!cell.get?.("backgroundObject")
}
