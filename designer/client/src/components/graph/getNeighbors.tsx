import type { dia } from "jointjs";

type ExclusiveFalse<A extends string, B extends string> = (Record<A, true> & Record<B, false>) | (Record<A, false> & Record<B, true>);
type GetNeighborsOptions = Partial<
    ExclusiveFalse<"inbound", "outbound"> & {
        depth: number;
        withSelf: boolean;
    }
>;

export function getNeighbors(graph: dia.Graph, element: dia.Element, options: GetNeighborsOptions = {}): dia.Element[] {
    const { depth = 1, inbound = true, outbound = true, withSelf = false } = options;
    const result = new Set<dia.Element>(withSelf ? [element] : []);

    const walk = (currentElement: dia.Element, remaining: number) => {
        if (remaining === 0) return;
        for (const neighbor of graph.getNeighbors(currentElement, { inbound, outbound })) {
            if (result.has(neighbor)) continue;
            if (neighbor.id === element.id && !withSelf) continue;
            result.add(neighbor);
            walk(neighbor, remaining - 1);
        }
    };

    walk(element, depth);
    return Array.from(result.values());
}
