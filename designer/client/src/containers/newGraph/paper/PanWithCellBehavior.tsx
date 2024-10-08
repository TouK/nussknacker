import { dia, g } from "jointjs";
import { clamp } from "lodash";
import React, { useCallback, useEffect, useMemo } from "react";
import { createContextHook, useContextForward } from "../utils/context";
import { polygonDiff } from "../utils/geom";
import { usePanZoomBehavior } from "./PanZoomBehavior";
import { usePaper } from "./Paper";

function getViewportBounds(paper: dia.Paper, domOverlays: Element[], border = 0): g.Polygon {
    const viewport = new g.Rect(paper?.el.getBoundingClientRect());
    const rect = viewport.clone().inflate(-border, -border);
    return domOverlays.reduce((poly, el) => {
        const rect = new g.Rect(el.getBoundingClientRect());
        const cutout = g.Polygon.fromRect(rect.inflate(border, border));
        return polygonDiff(poly, cutout);
    }, g.Polygon.fromRect(rect));
}

type ContextType = {
    bindMoveWithEdge: (callback?: (offset: { x: number; y: number }) => void) => () => void;
    mutableMousePosition: g.Point;
};

const Context = React.createContext<ContextType>(null);

export type PanWithCellBehaviorProps = React.PropsWithChildren;

export const PanWithCellBehavior = React.forwardRef<ContextType, PanWithCellBehaviorProps>(function PanWithCellBehavior(
    { children },
    forwardedRef,
) {
    const { paper } = usePaper();
    const panZoom = usePanZoomBehavior();

    const mutableMousePosition = useMemo(() => {
        const viewport = new g.Rect(paper?.el.getBoundingClientRect());
        return new g.Point(viewport.center());
    }, [paper?.el]);

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
        (callback = panZoom.panBy) => {
            const border = 80;
            const maxDistance = border / 4;
            const viewport = getViewportBounds(paper, domOverlays, border);

            let frame: number;
            const panWithEdge = () => {
                if (!viewport.containsPoint(mutableMousePosition)) {
                    const distance = viewport.closestPoint(mutableMousePosition).difference(mutableMousePosition);
                    viewport.closestPoint(mutableMousePosition);
                    const x = clamp(distance.x / 2, -maxDistance, maxDistance);
                    const y = clamp(distance.y / 2, -maxDistance, maxDistance);
                    callback({ x, y });
                }
                frame = requestAnimationFrame(panWithEdge);
            };
            frame = requestAnimationFrame(panWithEdge);
            return () => {
                cancelAnimationFrame(frame);
            };
        },
        [domOverlays, mutableMousePosition, panZoom.panBy, paper],
    );

    useEffect(() => {
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
    }, [bindMoveWithEdge, observeCellMousePosition, panZoom, paper, updateCellPosition]);

    const behavior = useMemo<ContextType>(() => {
        return { mutableMousePosition, bindMoveWithEdge };
    }, [bindMoveWithEdge, mutableMousePosition]);

    useContextForward(forwardedRef, behavior);

    return <Context.Provider value={behavior}>{children}</Context.Provider>;
});

export const usePanWithCellBehavior = createContextHook(Context, PanWithCellBehavior);
