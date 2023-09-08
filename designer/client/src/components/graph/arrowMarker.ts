/* eslint-disable i18next/no-literal-string */
import { dia, V } from "jointjs";
import arrow from "!raw-loader!./arrow.svg";

export function createUniqueArrowMarker(paper: dia.Paper) {
    return paper.defineMarker({
        markup: V(arrow).node.innerHTML,
        attrs: {
            viewBox: "0 0 10 10",
            refX: 8,
            refY: 5,
            markerUnits: "strokeWidth",
            markerWidth: 6,
            markerHeight: 20,
            orient: "auto-start-reverse",
            class: "arrow-marker",
        },
    });
}
