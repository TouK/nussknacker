import type { dia } from "jointjs";
import { g } from "jointjs";

import { RECT_HEIGHT, RECT_WIDTH } from "./EspNode/esp";

export type AlignCellsVariant =
    | "top"
    | "right"
    | "bottom"
    | "left"
    | "center:horizontal"
    | "center:vertical"
    | "distribute:horizontal"
    | "distribute:vertical";

export function alignCells(variant: AlignCellsVariant, elements: dia.Cell[]) {
    const boundingBox = g.Rect.fromRectUnion(...elements.map((cell) => cell.getBBox()));
    if (variant === "distribute:vertical" || variant === "distribute:horizontal") {
        const hdist = (boundingBox.leftMiddle().distance(boundingBox.rightMiddle()) - RECT_WIDTH) / (elements.length - 1);
        const vdist = (boundingBox.topMiddle().distance(boundingBox.bottomMiddle()) - RECT_HEIGHT) / (elements.length - 1);
        let p = boundingBox.topLeft();
        if (variant === "distribute:horizontal") {
            p = p.translate(-20000, 0);
        }
        if (variant === "distribute:vertical") {
            p = p.translate(0, -20000);
        }
        const sorted = elements.sort((a, b) => {
            const ap = a.getBBox().center();
            const bp = b.getBBox().center();
            return ap.distance(p) - bp.distance(p);
        });
        return sorted.forEach((cell: dia.Element, index) => {
            cell.position(
                variant === "distribute:horizontal" ? boundingBox.topLeft().x + hdist * index : cell.position().x,
                variant === "distribute:vertical" ? boundingBox.topLeft().y + vdist * index : cell.position().y,
            );
        });
    }
    return elements.forEach((cell: dia.Element) => {
        cell.position(
            variant === "left"
                ? boundingBox.leftMiddle().x
                : variant === "center:horizontal"
                ? boundingBox.center().x - cell.getBBox().width / 2
                : variant === "right"
                ? boundingBox.rightMiddle().x - cell.getBBox().width
                : cell.position().x,
            variant === "top"
                ? boundingBox.topMiddle().y
                : variant === "center:vertical"
                ? boundingBox.center().y - cell.getBBox().height / 2
                : variant === "bottom"
                ? boundingBox.bottomMiddle().y - cell.getBBox().height
                : cell.position().y,
        );
    });
}
