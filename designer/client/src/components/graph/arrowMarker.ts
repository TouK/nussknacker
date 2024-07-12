/* eslint-disable i18next/no-literal-string */
import { dia, V } from "jointjs";
import arrow from "!raw-loader!./arrow.svg";
import { Theme } from "@mui/material";

export function createUniqueArrowMarker(paper: dia.Paper, theme: Theme) {
    return paper.defineMarker({
        markup: V(arrow).node.innerHTML,
        attrs: {
            color: theme.palette.text.secondary,
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
