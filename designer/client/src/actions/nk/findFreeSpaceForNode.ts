import type { dia } from "jointjs";
import { g } from "jointjs";

import { RECT_HEIGHT, RECT_WIDTH } from "../../components/graph/EspNode/esp";

const defaultOptions = {
    width: RECT_WIDTH,
    height: RECT_HEIGHT,
    dx: RECT_HEIGHT,
};

function countCellsInArea(paper: dia.Paper, area: g.Rect, self?: dia.Cell): number {
    return paper.model.findModelsInArea(area).filter((c) => c !== self).length;
}

function findFreeSpace(
    paper: dia.Paper,
    plainPoint: g.PlainPoint,
    self?: dia.Cell,
    options: Partial<typeof defaultOptions> = defaultOptions,
): g.Point {
    const rect = new g.Rect(plainPoint.x, plainPoint.y, options.width, options.height);
    if (countCellsInArea(paper, rect.clone().inflate(options.dx / 2), self)) {
        return findFreeSpace(paper, rect.offset(options.dx).topLeft(), self, options);
    }
    return rect.topLeft();
}

export const findFreeSpaceForNode = (paper: dia.Paper, plainPoint: g.PlainPoint, self?: dia.Cell): g.PlainPoint => {
    return findFreeSpace(paper, plainPoint, self).snapToGrid(1, 1).toJSON();
};
