import { dia, g } from "jointjs";
import { clamp } from "lodash";
import { useCallback, useEffect, useLayoutEffect, useMemo, useState } from "react";
import { polyDiff } from "../polyDiff";
import { PaperBehaviorProps, PaperContextType } from "./Paper";

export type PanWithCellBehavior = {
    bindMoveWithEdge: (callback: (offset: { x: number; y: number }) => void) => () => void;
    mutableMousePosition: g.Point;
};

function getViewportBounds(paper: dia.Paper, domOverlays: Element[], border = 0): g.Polygon {
    const viewport = new g.Rect(paper?.el.getBoundingClientRect());
    const rect = viewport.clone().inflate(-border, -border);
    return domOverlays.reduce((poly, el) => {
        const rect = new g.Rect(el.getBoundingClientRect());
        const cutout = g.Polygon.fromRect(rect.inflate(border, border));
        return polyDiff(poly, cutout);
    }, g.Polygon.fromRect(rect));
}

export function usePanWithCellBehavior(
    [{ paper, panZoom }, register]: [PaperContextType, (behavior: PanWithCellBehavior) => void],
    { interactive }: PaperBehaviorProps,
) {
    const [_register] = useState(() => register);

    const mutableMousePosition = useMemo(() => {
        const viewport = new g.Rect(paper?.el.getBoundingClientRect());
        return new g.Point(viewport.center());
    }, []);

    const observeCellMousePosition = useCallback(() => {
        const callback = (cellView: dia.CellView, event: dia.Event) => {
            mutableMousePosition.update(event.clientX, event.clientY);
        };
        paper.on("cell:pointermove", callback);
        return () => {
            paper.off("cell:pointermove", callback);
        };
    }, [mutableMousePosition, paper]);

    const updateCellPosition = useCallback(({ model }: dia.CellView, offset: { x: number; y: number }) => {
        const currentPos = model.position();
        model.set("position", { x: currentPos.x - offset.x, y: currentPos.y - offset.y });
    }, []);

    const domOverlays = Array.from(document.querySelectorAll("[data-testid='SidePanel'] .droppable .draggable-list"));
    const bindMoveWithEdge = useCallback(
        (callback: (offset: { x: number; y: number }) => void) => {
            const border = 80;
            const viewport = getViewportBounds(paper, domOverlays, border);

            let frame: number;
            const panWithEdge = () => {
                if (!viewport.containsPoint(mutableMousePosition)) {
                    const distance = viewport.closestPoint(mutableMousePosition).difference(mutableMousePosition);
                    viewport.closestPoint(mutableMousePosition);
                    const x = clamp(distance.x / 2, -(border / 4), border / 4);
                    const y = clamp(distance.y / 2, -(border / 4), border / 4);
                    callback({ x, y });
                }
                frame = requestAnimationFrame(panWithEdge);
            };
            frame = requestAnimationFrame(panWithEdge);
            return () => {
                cancelAnimationFrame(frame);
            };
        },
        [mutableMousePosition, paper?.el],
    );

    useEffect(() => {
        if (interactive) {
            const callback = (cellView: dia.CellView) => {
                if (!panZoom) return;
                cellView.once(
                    "cell:pointerup",
                    bindMoveWithEdge((offset) => {
                        panZoom.panBy(offset);
                        updateCellPosition(cellView, offset);
                    }),
                );
                cellView.once("cell:pointerup", observeCellMousePosition());
            };
            paper?.on("cell:pointerdown", callback);
            return () => {
                paper?.off("cell:pointerdown", callback);
            };
        }
    }, [bindMoveWithEdge, interactive, observeCellMousePosition, panZoom, paper, updateCellPosition]);

    useLayoutEffect(() => {
        const behavior: PanWithCellBehavior = { mutableMousePosition, bindMoveWithEdge };
        _register(behavior);
    }, [_register, bindMoveWithEdge, mutableMousePosition]);
}
