import type { dia } from "jointjs";

import type { NodeTransitionResult } from "../../http/resultsWithCountsDto";

type TransitionEndpoints = Pick<NodeTransitionResult, "sourceNodeId" | "destinationNodeId">;

export const getElementMatcher =
    (element: dia.Element) =>
    ({ sourceNodeId, destinationNodeId }: TransitionEndpoints): boolean => {
        if (element.hasPort("In")) return element.id === destinationNodeId;
        if (element.hasPort("Out")) return element.id === sourceNodeId;
    };

export const getLinkMatcher =
    (link: dia.Link) =>
    ({ sourceNodeId, destinationNodeId }: TransitionEndpoints): boolean => {
        return sourceNodeId === link.attributes.edgeData?.from && destinationNodeId === link.attributes.edgeData?.to;
    };

export const getTransitionMatcher =
    (transition: NodeTransitionResult) =>
    ({ sourceNodeId, destinationNodeId }: TransitionEndpoints): boolean => {
        return sourceNodeId === transition?.sourceNodeId && destinationNodeId === transition?.destinationNodeId;
    };
