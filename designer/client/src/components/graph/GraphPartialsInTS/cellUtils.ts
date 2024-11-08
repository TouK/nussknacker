import { dia, shapes } from "jointjs";

export const isLink = (c: dia.Cell): c is dia.Link => c.isLink();
export const isElement = (c: dia.Cell): c is dia.Element => c?.isElement();

export function isModelElement(el: dia.Cell): el is shapes.devs.Model {
    return el instanceof shapes.devs.Model;
}

export function isStickyNoteElement(el: dia.Cell) {
    return isModelElement(el) && el.get("type") === `stickyNote.Model`;
}

export function isConnected(el: dia.Element): boolean {
    return el.graph.getNeighbors(el).length > 0;
}

export const isCellSelected =
    (selectedItems: Array<dia.Cell["id"]>) =>
    (c: dia.Cell): boolean => {
        return isLink(c)
            ? selectedItems.includes(c.getSourceElement().id) && selectedItems.includes(c.getTargetElement().id)
            : selectedItems.includes(c.id);
    };
