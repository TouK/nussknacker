import { dia, shapes } from "jointjs";
import { useEffect, useRef } from "react";
import { useGraph } from "./GraphProvider";

type NodeProps = {
    children: string;
    x?: number;
    y?: number;
    id: string;
};

export function Node({ children, id, x = 0, y = 0, ...props }: NodeProps) {
    const graph = useGraph();
    const shape = useRef<dia.Element>();

    useEffect(() => {
        const rect = new shapes.standard.Rectangle({
            id,
            size: { width: 200, height: 50 },
        });
        shape.current = rect;
        rect.addTo(graph);

        return () => {
            rect.remove();
        };
    }, [graph, id]);

    useEffect(() => {
        shape.current.attr("label/text", children);
        shape.current.position(x, y);
    }, [children, x, y]);

    return null;
}
