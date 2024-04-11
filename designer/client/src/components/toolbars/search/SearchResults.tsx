import { NodeType } from "../../../types";
import { useDispatch, useSelector } from "react-redux";
import { getScenario, getSelectionState } from "../../../reducers/selectors/graph";
import { MenuItem, MenuList } from "@mui/material";
import { FoundNode } from "./FoundNode";
import React, { useCallback, useEffect, useState } from "react";
import { useFilteredNodes } from "./utils";
import { useGraph } from "../../graph/GraphContext";
import { nodeFound, nodeFoundHover } from "../../graph/focusableStyled";
import { resetSelection } from "../../../actions/nk";
import { useWindows } from "../../../windowManager";

export function SearchResults({ filterValues = [] }: { filter?: string; filterValues?: string[] }) {
    const nodes = useFilteredNodes(filterValues);

    const [hasFocus, setHasFocus] = useState(false);
    const [hoveredNodes, setHoveredNodes] = useState<string[]>([]);

    const graphGetter = useGraph();
    const { openNodeWindow } = useWindows();
    const selectionState = useSelector(getSelectionState);
    const scenario = useSelector(getScenario);
    const dispatch = useDispatch();

    const isNodeSelected = useCallback((node: NodeType) => selectionState.includes(node.id), [selectionState]);
    const selectOrOpen = useCallback(
        (node: NodeType) => () => {
            if (isNodeSelected(node)) {
                openNodeWindow(node, scenario);
            } else {
                dispatch(resetSelection(node.id));
            }
        },
        [dispatch, isNodeSelected, openNodeWindow, scenario],
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

        const nodeIds = nodes.map((n) => n.node.id);
        graph.fitToNodes(nodeIds);

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
                    <FoundNode node={node} highlights={filterValues} fields={groups} />
                </MenuItem>
            ))}
        </MenuList>
    );
}
