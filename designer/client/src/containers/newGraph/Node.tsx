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
        shape.current = graph.replaceCell(
            shape.current,
            new shapes.standard.Rectangle({
                id,
                size: { width: 250, height: 50 },
            }),
        );
    }, [graph, id]);

    useEffect(() => {
        return () => {
            shape.current?.remove();
        };
    }, []);

    useEffect(() => {
        shape.current.attr("label/text", children);
        shape.current.position(x, y);
    }, [children, x, y]);

    return null;
}
