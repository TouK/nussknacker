/* eslint-disable i18next/no-literal-string */
import { dia, V } from "jointjs";
import arrow from "!raw-loader!./arrow.svg";

const arrowMarker = V(
    "marker",
    {
        viewBox: "0 0 10 10",
        refX: 8,
        refY: 5,
        markerWidth: 6,
        markerHeight: 20,
        orient: "auto-start-reverse",
        class: "arrow-marker",
    },
    V(arrow).children(),
);

export function createUniqueArrowMarker(paper: dia.Paper) {
    const uniqueArrowMarker = arrowMarker.clone();
    V(paper.defs).append(uniqueArrowMarker);
    return uniqueArrowMarker.attr("id");
}
