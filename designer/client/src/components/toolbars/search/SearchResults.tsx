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

        const nodeIds = nodes.map(({ data: [{ id }] }) => id);
        graph.panToNodes(nodeIds);
        nodeIds.forEach((id) => {
            graph.highlightNode(id, nodeFound);
            if (hoveredNodes.includes(id)) {
                graph.highlightNode(id, nodeFoundHover);
            }
        });

        return () => {
            nodeIds.forEach((id) => graph.unhighlightNode(id, nodeFound));
            hoveredNodes.forEach((id) => graph.unhighlightNode(id, nodeFoundHover));
        };
    }, [nodes, graphGetter, hoveredNodes, clearHighlight]);

    return (
        <MenuList onFocus={() => setHasFocus(true)} onBlur={() => setHasFocus(false)} tabIndex={-1} sx={{ padding: 0 }}>
            {nodes.map(({ data: [node, edges], groups }) => (
                <MenuItem
                    key={node.id}
                    tabIndex={hasFocus ? -1 : 0}
                    selected={isNodeSelected(node)}
                    onClick={selectOrOpen(node)}
                    onMouseEnter={highlightNode(node)}
                    onFocus={highlightNode(node)}
                    onMouseLeave={clearHighlight}
                    onBlur={clearHighlight}
                >
                    <FoundNode node={node} edges={edges} highlights={filterValues} names={groups} />
                </MenuItem>
            ))}
        </MenuList>
    );
}
