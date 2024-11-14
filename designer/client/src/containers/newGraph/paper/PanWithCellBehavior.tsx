import { dia, g } from "jointjs";
import { clamp } from "lodash";
import React, { useCallback, useEffect, useImperativeHandle, useMemo, useState } from "react";
import { createContextHook } from "../utils/context";
import { polygonDiff } from "../utils/geom";
import { usePanZoomBehavior } from "./PanZoomBehavior";
import { PaperSvgPortal, usePaper } from "./Paper";

function getViewportBounds(paper: dia.Paper, domOverlays: Element[], border = 0): g.Polygon {
    const viewport = new g.Rect(paper?.el.getBoundingClientRect());
    const rect = viewport.clone().inflate(border * -2);
    return domOverlays.reduce((poly, el) => {
        const cutout = new g.Rect(el.getBoundingClientRect()).inflate(border / 4);
        return polygonDiff(poly, g.Polygon.fromRect(cutout));
    }, g.Polygon.fromRect(rect).close());
}

export type ContextType = {
    bindMoveWithEdge: (callback?: (offset: g.PlainPoint) => void) => () => void;
    setMousePosition: (...args: [number, number] | [g.PlainPoint]) => void;
};

const Context = React.createContext<ContextType>(null);

export type PanWithCellBehaviorProps = React.PropsWithChildren<{
    border?: number;
    domOverlays?: () => Element[];
}>;

export const PanWithCellBehavior = React.forwardRef<ContextType, PanWithCellBehaviorProps>(function PanWithCellBehavior(
    { border = 40, domOverlays = () => [], children },
    forwardedRef,
) {
    const maxSpeed = border / 2;
    const { paper } = usePaper();
    const panZoom = usePanZoomBehavior();

    const mutableMousePosition = useMemo(() => {
        const viewport = getViewportBounds(paper, domOverlays(), border * 4);
        return viewport.bbox().center();
    }, [border, domOverlays, paper]);

    const [viewport, setViewport] = useState<g.Polygon>(null);
    const [maskOpacity, setMaskOpacity] = useState<number>(0);
    const [maskPosition, setMaskPosition] = useState<g.Point>(mutableMousePosition);

    const bindMoveWithEdge = useCallback(
        (callback?: (offset: g.PlainPoint) => void) => {
            const { x, y } = paper.svg.getBoundingClientRect();
            const translateToPaper = (p: g.Point) => p.translate({ x: -x, y: -y });

            const viewport = getViewportBounds(paper, domOverlays(), border);
            viewport.points.forEach(translateToPaper);
            setViewport(viewport.clone());

            let frame: number;
            const panWithEdge = () => {
                const mouse = translateToPaper(mutableMousePosition.clone());
                if (!viewport.containsPoint(mouse)) {
                    const point = viewport.closestPoint(mouse);
                    setMaskPosition(point);
                    const distance = point.difference(mouse);
                    const x = clamp(distance.x / 2, -maxSpeed, maxSpeed);
                    const y = clamp(distance.y / 2, -maxSpeed, maxSpeed);
                    setMaskOpacity(Math.sqrt(x * x + y * y) / maxSpeed);
                    const offset = { x, y };
                    callback?.(offset);
                } else {
                    setMaskOpacity(0);
                }
                frame = requestAnimationFrame(panWithEdge);
            };
            frame = requestAnimationFrame(panWithEdge);
            return () => {
                cancelAnimationFrame(frame);
                setViewport(null);
            };
        },
        [border, domOverlays, maxSpeed, mutableMousePosition, paper],
    );

    const behavior = useMemo<ContextType>(
        () => ({
            setMousePosition: mutableMousePosition.update.bind(mutableMousePosition),
            bindMoveWithEdge,
        }),
        [bindMoveWithEdge, mutableMousePosition],
    );

    const observeCellMousePosition = useCallback(() => {
        const callback = (cellView: dia.CellView, event: dia.Event) => {
            behavior.setMousePosition(event.clientX, event.clientY);
        };
        paper.on("cell:pointermove", callback);
        return () => {
            paper.off("cell:pointermove", callback);
        };
    }, [behavior, paper]);

    const updateCellPosition = useCallback(({ model }: dia.CellView, offset: g.PlainPoint) => {
        const nextPosition = model.position().translate(-offset.x, -offset.y);
        model.set("position", nextPosition);
    }, []);

    const onDragStart = useCallback(
        (cellView: dia.CellView) => {
            if (!panZoom) return;
            const unobserveCellMousePosition = observeCellMousePosition();
            const unbindMoveWithEdge = behavior.bindMoveWithEdge((offset) => {
                panZoom.panBy(offset);
                updateCellPosition(cellView, offset);
            });
            cellView.once("cell:pointerup", () => {
                unobserveCellMousePosition();
                unbindMoveWithEdge();
            });
        },
        [behavior, observeCellMousePosition, panZoom, updateCellPosition],
    );

    useEffect(() => {
        paper?.on("cell:pointerdown", onDragStart);
        return () => {
            paper?.off("cell:pointerdown", onDragStart);
        };
    }, [onDragStart, paper]);

    useImperativeHandle(forwardedRef, () => behavior, [behavior]);

    return (
        <Context.Provider value={behavior}>
            {children}
            {viewport ? (
                <PaperSvgPortal>
                    <Polygon geom={viewport} intensity={maskOpacity} position={maskPosition} />
                </PaperSvgPortal>
            ) : null}
        </Context.Provider>
    );
});

export const ClickToZoomBehavior = function ClickToZoomBehavior() {
    const { paper } = usePaper();
    const panZoom = usePanZoomBehavior();

    const onDragStart = useCallback(
        (cellView: dia.CellView) => {
            if (!panZoom) return;
            panZoom.fitContent(paper.clientToLocalRect(cellView.getBBox()));
            console.log(cellView);
        },
        [panZoom, paper],
    );

    useEffect(() => {
        paper?.on("cell:pointerclick", onDragStart);
        return () => {
            paper?.off("cell:pointerclick", onDragStart);
        };
    }, [onDragStart, paper]);

    return null;
};

export const usePanWithCellBehavior = createContextHook(Context, PanWithCellBehavior);

function Polygon({ geom, intensity = 0, position }: { geom: g.Polygon; position: g.Point; intensity?: number }) {
    const points = useMemo(() => geom.points.map(({ x, y }) => `${x}, ${y}`).join(" "), [geom]);
    return (
        <g pointerEvents="none">
            <mask id="clip">
                <circle cx={position.x} cy={position.y} r={150} filter={`blur(25px)`} fill="white" opacity={intensity * 0.75} />
            </mask>
            <polygon
                points={points}
                fill="none"
                stroke="blue"
                strokeWidth={2}
                strokeDasharray={9}
                filter={`blur(0.5px)`}
                mask="url(#clip)"
            />
        </g>
    );
}
