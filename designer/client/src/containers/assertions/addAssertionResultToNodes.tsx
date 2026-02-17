import { darken, useTheme } from "@mui/material";
import type { attributes } from "jointjs";
import { useEffect } from "react";

import { getStringWidth } from "../../components/graph/EspNode/element";
import { RECT_HEIGHT, RECT_WIDTH } from "../../components/graph/EspNode/esp";
import { useGraph } from "../../components/graph/GraphContext";
import { isModelElement } from "../../components/graph/GraphPartialsInTS/cellUtils";
import { getTestAssertionResults } from "../../reducers/selectors/testing";
import { useAppSelector } from "../../store/storeHelpers";
import { calculateAssertionResultsSummary } from "./assertionResultsUtils";

export const AddAssertionResultToNodes = () => {
    const graphGetter = useGraph();
    const testAssertionResults = useAppSelector(getTestAssertionResults);
    const theme = useTheme();

    useEffect(() => {
        const graph = graphGetter?.();
        const model = graph?.processGraphPaper?.model;

        if (!model) return;

        const nodes = model.getElements().filter(isModelElement);
        nodes.forEach((element) => {
            const { hasResult, failedCount, passedCount, total } = calculateAssertionResultsSummary(
                testAssertionResults?.[element.id] ?? [],
            );

            const text = hasResult ? `${passedCount}/${total}` : "";
            const assertionResultWidth = getStringWidth(text);
            const assertionResultHeight = 24;
            const fillColor = failedCount > 0 ? theme.palette.error.dark : theme.palette.success.dark;
            const strokeColor = darken(fillColor, 0.3);
            const textColor = theme.palette.text.secondary;

            const testAssertionResult: attributes.SVGRectAttributes = {
                display: hasResult ? "block" : "none",
                ref: "border",
                width: assertionResultWidth,
                height: 24,
                fill: fillColor,
                stroke: strokeColor,
                strokeWidth: 1,
                rx: theme.shape?.borderRadius ?? 1,
                ry: theme.shape?.borderRadius ?? 1,
                x: RECT_WIDTH - assertionResultWidth / 2,
                y: RECT_HEIGHT / 2 - 12,
            };

            const testAssertionResultSummary: attributes.SVGTextAttributes = {
                display: hasResult ? "block" : "none",
                text: text,
                "font-weight": "bold",
                fill: textColor,
                "font-size": 13,
                "text-anchor": "middle",
                pointerEvents: "none",
                x: RECT_WIDTH,

                height: assertionResultHeight,
                y: RECT_HEIGHT / 2 + 4,
            };

            element.attr({ testAssertionResult, testAssertionResultSummary });
        });
    }, [
        graphGetter,
        testAssertionResults,
        theme.palette.error.main,
        theme.palette.error.dark,
        theme.palette.success.main,
        theme.palette.success.dark,
        theme.palette.text.secondary,
        theme.shape?.borderRadius,
    ]);
    return null;
};
