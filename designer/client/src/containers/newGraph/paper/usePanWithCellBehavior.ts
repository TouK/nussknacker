import { dia, g } from "jointjs";
import { clamp } from "lodash";
import { useCallback, useEffect, useMemo } from "react";
import { PaperBehaviorProps, PaperContextType } from "./Paper";

export function usePanWithCellBehavior([{ paper, panZoom }]: [PaperContextType], { interactive }: PaperBehaviorProps) {
    const viewport = useMemo(() => new g.Rect(paper?.el.getBoundingClientRect()), [paper?.el]);
    const bindMoveWithEdge = useCallback(
        (cellView: dia.CellView, viewport: g.Rect) => {
            const { paper, model } = cellView;
            const border = 80;
            const mousePosition = new g.Point(viewport.center());
            const updateMousePosition = (cellView: dia.CellView, event: dia.Event) => {
                mousePosition.update(event.clientX, event.clientY);
            };
            let frame: number;
            const panWithEdge = () => {
                const rect = viewport.clone().inflate(-border, -border);
                if (!rect.containsPoint(mousePosition)) {
                    const distance = rect.pointNearestToPoint(mousePosition).difference(mousePosition);
                    const x = clamp(distance.x / 2, -(border / 4), border / 4);
                    const y = clamp(distance.y / 2, -(border / 4), border / 4);

                    panZoom.panBy({ x, y });
                    const p = model.position();
                    model.set("position", { x: p.x - x, y: p.y - y });
                }
                frame = requestAnimationFrame(panWithEdge);
            };
            if (panZoom) {
                frame = requestAnimationFrame(panWithEdge);
            }
            paper.on("cell:pointermove", updateMousePosition);
            return () => {
                paper.off("cell:pointermove", updateMousePosition);
                cancelAnimationFrame(frame);
            };
        },
        [panZoom],
    );

    useEffect(() => {
        if (interactive) {
            const callback = (cellView: dia.CellView) => {
                const cleanup = bindMoveWithEdge(cellView, viewport);
                cellView.once("cell:pointerup", () => {
                    cleanup();
                });
            };
            paper?.on("cell:pointerdown", callback);
            return () => {
                paper?.off("cell:pointerdown", callback);
            };
        }
    }, [bindMoveWithEdge, interactive, paper, viewport]);
}
