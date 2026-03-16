import { MenuItem, MenuList } from "@mui/material";
import React, { useCallback, useEffect, useMemo, useState } from "react";

import { resetSelection } from "../../../actions/nk/selection";
import { getScenario, getSelectionState } from "../../../reducers/selectors/graph";
import { useAppDispatch, useAppSelector } from "../../../store/storeHelpers";
import type { NodeType } from "../../../types/node";
import { useWindows } from "../../../windowManager/useWindows";
import { useGraph } from "../../graph/GraphContext";
import { nodeFound, nodeFoundHover } from "../../graph/graphStyledWrapper";
import { FoundNode } from "./FoundNode";
import { resolveSearchQuery, useFilteredNodes } from "./utils";

export type SearchQuery = {
    id?: string[];
    name?: string[];
    description?: string[];
    type?: string[];
    label?: string[];
    value?: string[];
    output?: string[];
    edge?: string[];
    plainQuery?: string;
};

export function SearchResults({ filterRawText }: { filterRawText?: string }) {
    const searchQuery: SearchQuery = useMemo(() => resolveSearchQuery(filterRawText), [filterRawText]);
    const nodes = useFilteredNodes(searchQuery);

    const [hasFocus, setHasFocus] = useState(false);
    const [hoveredNodes, setHoveredNodes] = useState<string[]>([]);

    const graphGetter = useGraph();
    const { openNodeWindow } = useWindows();
    const selectionState = useAppSelector(getSelectionState);
    const scenario = useAppSelector(getScenario);
    const dispatch = useAppDispatch();

    const isNodeSelected = useCallback((node: NodeType) => selectionState.includes(node.id), [selectionState]);
    const selectOrOpen = useCallback(
        (node: NodeType) => () => {
            if (isNodeSelected(node)) {
                openNodeWindow(node, scenario);
            } else {
                const graph = graphGetter();
                graph.fitToNode(node.id);
                dispatch(resetSelection(node.id));
            }
        },
        [dispatch, graphGetter, isNodeSelected, openNodeWindow, scenario],
    );
    const highlightNode = useCallback((node: NodeType) => () => setHoveredNodes([node.id]), []);
    const clearHighlight = useCallback(() => setHoveredNodes((current) => (!current?.length ? current : [])), []);

    useEffect(() => {
        const graph = graphGetter();

        if (!graph || !nodes.length) {
            setHasFocus(false);
            clearHighlight();
            return;
        }

        nodes.forEach(({ node, edges }) => {
            graph.highlightNode(node.id, nodeFound);
            edges.forEach((e) => graph.highlightEdge(e, nodeFound));
            if (hoveredNodes.includes(node.id)) {
                edges.forEach((e) => graph.highlightEdge(e, nodeFoundHover));
                graph.highlightNode(node.id, nodeFoundHover);
            }
        });

        return () => {
            nodes.forEach(({ node, edges }) => {
                graph.unhighlightNode(node.id, nodeFound);
                graph.unhighlightNode(node.id, nodeFoundHover);
                edges.forEach((e) => {
                    graph.unhighlightEdge(e, nodeFound);
                    graph.unhighlightEdge(e, nodeFoundHover);
                });
            });
        };
    }, [nodes, graphGetter, hoveredNodes, clearHighlight]);

    return (
        <MenuList onFocus={() => setHasFocus(true)} onBlur={() => setHasFocus(false)} tabIndex={-1} sx={{ padding: 0 }}>
            {nodes.map(({ node, groups }) => (
                <MenuItem
                    key={node.id}
                    tabIndex={hasFocus ? -1 : 0}
                    selected={isNodeSelected(node)}
                    onClick={selectOrOpen(node)}
                    onMouseEnter={highlightNode(node)}
                    onFocus={highlightNode(node)}
                    onMouseLeave={clearHighlight}
                    onBlur={clearHighlight}
                    disableGutters
                    divider
                >
                    <FoundNode node={node} highlights={[searchQuery.plainQuery]} fields={groups} />
                </MenuItem>
            ))}
        </MenuList>
    );
}
