import { dia, shapes } from "jointjs";
import { useEffect, useRef } from "react";
import { useGraph } from "../GraphProvider";

type EdgeProps = {
    from: string;
    to: string;
};

export function Edge({ from, to }: EdgeProps) {
    const graph = useGraph();
    const shape = useRef<dia.Link>();

    useEffect(() => {
        const link = new shapes.standard.Link({});
        shape.current = link;
        link.addTo(graph);

        return () => {
            link.remove();
        };
    }, [graph]);

    useEffect(() => {
        const link = shape.current;
        link.source({ id: from });
        link.target({ id: to });
    }, [from, to]);

    return null;
}
