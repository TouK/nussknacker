import {dia, shapes} from "jointjs"

export const isLink = (c: dia.Cell): c is dia.Link => c.isLink()
export const isElement = (c: dia.Cell): c is dia.Element => c?.isElement()

export function isModelElement(el: dia.Element): el is shapes.devs.Model {
  return el instanceof shapes.devs.Model
}

export function isGroupElement(el: dia.Element): boolean {
  return el.attributes.nodeData?.type === "_group"
}

export function isGroupRect(el: dia.Element): el is shapes.basic.Rect {
  return el instanceof shapes.basic.Rect && isGroupElement(el)
}

export function isBackgroundObject(cell: dia.Cell): boolean {
  // eslint-disable-next-line i18next/no-literal-string
  return !!cell.get?.("backgroundObject")
}

export const isCellSelected = (selectedItems: Array<dia.Cell["id"]>) => (c: dia.Cell): boolean => {
  return isLink(c) ?
    selectedItems.includes(c.getSourceElement().id) && selectedItems.includes(c.getTargetElement().id) :
    selectedItems.includes(c.id)
}
