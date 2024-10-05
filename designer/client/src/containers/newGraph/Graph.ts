import { dia } from "jointjs";

export class Graph<A extends dia.ObjectHash = dia.Graph.Attributes, S = dia.ModelSetOptions> extends dia.Graph<A, S> {
    replaceCell<E extends dia.Cell>(id: dia.Cell.ID | dia.Cell, nextCell: E): E {
        const currentCell = this.getCell(id);
        currentCell?.set("id", `${currentCell.id}__removed`);

        this.addCell(nextCell);
        currentCell?.remove();

        return nextCell;
    }
}
