/* eslint-disable i18next/no-literal-string */
import { dia, g, shapes } from "jointjs";
import styles from "jointjs/dist/joint.css";
import { cloneDeep, debounce, isEmpty, isEqual, keys, sortBy, without } from "lodash";
import React from "react";
import { findDOMNode } from "react-dom";
import { filterDragHovered, getLinkNodes, setLinksHovered } from "./utils/dragHelpers";
import { updateNodeCounts } from "./EspNode/element";
import { GraphPaperContainer } from "./focusable";
import { applyCellChanges, calcLayout, createPaper, isModelElement } from "./GraphPartialsInTS";
import { Events, GraphProps } from "./types";
import NodeUtils from "./NodeUtils";
import { PanZoomPlugin } from "./PanZoomPlugin";
import { RangeSelectedEventData, RangeSelectPlugin, SelectionMode } from "./RangeSelectPlugin";
import { prepareSvg } from "./svg-export/prepareSvg";
import * as GraphUtils from "./utils/graphUtils";
import { handleGraphEvent } from "./utils/graphUtils";
import { ComponentDragPreview } from "../ComponentDragPreview";
import { rafThrottle } from "./rafThrottle";
import { isEdgeEditable } from "../../common/EdgeUtils";
import { NodeId, NodeType, ScenarioGraph, ProcessDefinitionData } from "../../types";
import { Layout, NodePosition, Position } from "../../actions/nk";
import { UserSettings } from "../../reducers/userSettings";
import User from "../../common/models/User";
import { updateLayout } from "./GraphPartialsInTS/updateLayout";
import { getDefaultLinkCreator } from "./EspNode/link";
import ProcessUtils from "../../common/ProcessUtils";
import { isTouchEvent, LONG_PRESS_TIME } from "../../helpers/detectDevice";
import { batchGroupBy } from "../../reducers/graph/batchGroupBy";
import { createUniqueArrowMarker } from "./arrowMarker";
import { Scenario } from "../Process/types";

type Props = GraphProps & {
    processCategory: string;
    processDefinitionData: ProcessDefinitionData;
    loggedUser: Partial<User>;
    selectionState: NodeId[];
    userSettings: UserSettings;
    showModalNodeDetails: (node: NodeType, scenario: Scenario, readonly?: boolean) => void;
    isPristine?: boolean;
};

function handleActionOnLongPress<T extends dia.CellView>(
    shortPressAction: ((cellView: T, event: dia.Event) => void) | null,
    longPressAction: (cellView: T, event: dia.Event) => void,
    getPinchEventActive: () => boolean,
    longPressTime = LONG_PRESS_TIME,
) {
    let pressTimer;

    const releasePress = () => {
        clearTimeout(pressTimer);
    };

    return (cellView: T, evt: dia.Event) => {
        const { paper } = cellView;

        // let's clear all pointer click events on start
        if (shortPressAction) {
            paper.off(Events.CELL_POINTERCLICK, shortPressAction);
            paper.once(Events.CELL_POINTERCLICK, shortPressAction);
        }

        // Discard long press action on specific events
        paper.once(Events.CELL_POINTERUP, releasePress);
        paper.once(Events.CELL_POINTERMOVE, releasePress);

        pressTimer = window.setTimeout(() => {
            // Stop single click event when longPress fired
            if (shortPressAction) {
                paper.off(Events.CELL_POINTERCLICK, shortPressAction);
            }

            if (getPinchEventActive()) {
                return;
            }

            longPressAction(cellView, evt);
        }, longPressTime);
    };
}

export class Graph extends React.Component<Props> {
    redrawing = false;

    directedLayout = (selectedItems: string[] = []): void => {
        this.redrawing = true;
        calcLayout(this.graph, selectedItems);
        this.redrawing = false;
        this.changeLayoutIfNeeded();
    };

    createPaper = (): dia.Paper => {
        const canEditFrontend = this.props.loggedUser.canEditFrontend(this.props.processCategory) && !this.props.readonly;
        const paper = createPaper({
            frozen: true,
            model: this.graph,
            el: this.getEspGraphRef(),
            validateConnection: this.twoWayValidateConnection,
            validateMagnet: this.validateMagnet,
            interactive: (cellView: dia.CellView) => {
                const { model } = cellView;
                if (!canEditFrontend) {
                    return false;
                }
                if (model instanceof dia.Link) {
                    // Disable the default vertex add and label move functionality on pointerdown.
                    return { vertexAdd: false, labelMove: false };
                }
                return true;
            },
        });

        const uniqueArrowMarker = createUniqueArrowMarker(paper);
        // arrow id from paper is needed, so we have to mutate this
        paper.options.defaultLink = (cellView, magnet) => {
            // actual props are needed when link is created
            const linkCreator = getDefaultLinkCreator(
                uniqueArrowMarker,
                this.props.scenario.scenarioGraph,
                this.props.processDefinitionData,
            );
            return linkCreator(cellView, magnet);
        };

        return (
            paper
                //trigger new custom event on finished cell move
                .on(Events.CELL_POINTERDOWN, (cellView: dia.CellView) => {
                    const model = cellView.model;
                    const moveCallback = () => {
                        cellView.once(Events.CELL_POINTERUP, () => {
                            cellView.trigger(Events.CELL_MOVED, cellView);
                            paper.trigger(Events.CELL_MOVED, cellView);
                        });
                    };

                    model.once(Events.CHANGE_POSITION, moveCallback);
                    cellView.once(Events.CELL_POINTERUP, () => {
                        model.off(Events.CHANGE_POSITION, moveCallback);
                    });
                })
                //we want to inject node during 'Drag and Drop' from graph paper
                .on(Events.CELL_MOVED, (cell: dia.CellView) => {
                    if (isModelElement(cell.model)) {
                        const linkBelowCell = this.getLinkBelowCell();
                        const group = batchGroupBy.startOrContinue();
                        this.changeLayoutIfNeeded();
                        this.handleInjectBetweenNodes(cell.model, linkBelowCell);
                        batchGroupBy.end(group);
                    }
                })
                .on(Events.LINK_CONNECT, (linkView: dia.LinkView, evt: dia.Event, targetView: dia.CellView, targetMagnet: SVGElement) => {
                    if (this.props.isFragment === true) return;
                    const isReversed = targetMagnet?.getAttribute("port") === "Out";
                    const sourceView = linkView.getEndView("source");
                    const type = linkView.model.attributes.edgeData?.edgeType;
                    const from = sourceView?.model.attributes.nodeData;
                    const to = targetView?.model.attributes.nodeData;

                    if (from && to) {
                        isReversed ? this.props.nodesConnected(to, from, type) : this.props.nodesConnected(from, to, type);
                    }
                })
                .on(Events.LINK_DISCONNECT, ({ model }) => {
                    this.disconnectPreviousEdge(model.attributes.edgeData.from, model.attributes.edgeData.to);
                })
        );
    };

    private getLinkBelowCell() {
        const links = this.graph.getLinks();
        const [linkBelowCell] = filterDragHovered(links);
        return linkBelowCell;
    }

    drawGraph = (scenarioGraph: ScenarioGraph, layout: Layout, processDefinitionData: ProcessDefinitionData): void => {
        this.redrawing = true;

        applyCellChanges(this.processGraphPaper, scenarioGraph, processDefinitionData);

        if (isEmpty(layout)) {
            this.directedLayout();
        } else {
            updateLayout(this.graph, layout);
            this.redrawing = false;
        }
    };

    setEspGraphRef = (instance: HTMLElement): void => {
        this.instance = instance;
        if (this.props.isFragment !== true && this.props.connectDropTarget && instance) {
            // eslint-disable-next-line react/no-find-dom-node
            const node = findDOMNode(instance);
            this.props.connectDropTarget(node);
        }
    };
    graph: dia.Graph;
    processGraphPaper: dia.Paper;
    highlightHoveredLink = rafThrottle((forceDisable?: boolean) => {
        this.processGraphPaper.freeze();

        const links = this.graph.getLinks();
        links.forEach((l) => this.unhighlightCell(l, styles.dragHovered));

        if (!forceDisable) {
            const [active] = filterDragHovered(links);
            if (active) {
                this.highlightCell(active, styles.dragHovered);
                active.toBack();
            }
        }

        this.processGraphPaper.unfreeze();
    });
    private panAndZoom: PanZoomPlugin;
    forceLayout = debounce(() => {
        this.directedLayout(this.props.selectionState);
        this.panAndZoom.fitSmallAndLargeGraphs();
    }, 50);
    private _exportGraphOptions: Pick<dia.Paper, "options" | "defs">;
    private instance: HTMLElement;

    constructor(props: Props) {
        super(props);
        this.graph = new dia.Graph();
        this.bindNodeRemove();
        this.bindNodesMoving();
    }

    get zoom(): number {
        return this.panAndZoom?.zoom || 0;
    }

    getEspGraphRef = (): HTMLElement => this.instance;

    componentWillUnmount(): void {
        // force destroy event on model for plugins cleanup
        this.processGraphPaper.model.destroy();
    }

    bindEventHandlers(): void {
        const showNodeDetails = (cellView: dia.CellView) => {
            const { scenario, readonly, nodeIdPrefixForFragmentTests = "" } = this.props;
            const { nodeData, edgeData } = cellView.model.attributes;
            const nodeId = nodeData?.id || (isEdgeEditable(edgeData) ? edgeData.from : null);
            if (nodeId) {
                this.props.showModalNodeDetails(
                    {
                        ...NodeUtils.getNodeById(nodeId, scenario.scenarioGraph),
                        id: nodeIdPrefixForFragmentTests + nodeId,
                    },
                    scenario,
                    readonly,
                );
            }
        };

        const selectNode = (cellView: dia.CellView, evt: dia.Event) => {
            if (this.props.isFragment === true) return;
            if (this.props.nodeSelectionEnabled) {
                const nodeDataId = cellView.model.attributes.nodeData?.id;
                if (!nodeDataId) {
                    return;
                }

                if (evt.shiftKey || evt.ctrlKey || evt.metaKey || isTouchEvent(evt)) {
                    this.props.toggleSelection(nodeDataId);
                } else {
                    this.props.resetSelection(nodeDataId);
                }
            }
        };

        const deselectNodes = (event: JQuery.Event) => {
            if (event.isPropagationStopped()) {
                return;
            }
            if (this.props.isFragment !== true) {
                this.props.resetSelection();
            }
        };

        this.processGraphPaper.on(
            Events.CELL_POINTERDOWN,
            handleGraphEvent(handleActionOnLongPress(showNodeDetails, selectNode, this.panAndZoom.getPinchEventActive), null),
        );
        this.processGraphPaper.on(
            Events.LINK_POINTERDOWN,
            handleGraphEvent(
                handleActionOnLongPress(null, ({ model }) => model.remove(), this.panAndZoom.getPinchEventActive, LONG_PRESS_TIME * 1.5),
                null,
            ),
        );

        this.processGraphPaper.on(Events.CELL_POINTERCLICK, handleGraphEvent(null, selectNode));
        this.processGraphPaper.on(Events.CELL_POINTERDBLCLICK, handleGraphEvent(null, showNodeDetails));

        this.processGraphPaper.on(Events.BLANK_POINTERUP, deselectNodes);
        this.hooverHandling();
    }

    componentDidMount(): void {
        this.processGraphPaper = this.createPaper();
        this.drawGraph(this.props.scenario.scenarioGraph, this.props.layout, this.props.processDefinitionData);
        this.processGraphPaper.unfreeze();
        this._prepareContentForExport();

        // event handlers binding below. order sometimes matters
        this.panAndZoom = new PanZoomPlugin(this.processGraphPaper);

        if (this.props.isFragment !== true && this.props.nodeSelectionEnabled) {
            const { toggleSelection, resetSelection } = this.props;
            new RangeSelectPlugin(this.processGraphPaper, this.panAndZoom.getPinchEventActive);
            this.processGraphPaper.on("rangeSelect:selected", ({ elements, mode }: RangeSelectedEventData) => {
                const nodes = elements.filter((el) => isModelElement(el)).map(({ id }) => id.toString());
                if (mode === SelectionMode.toggle) {
                    toggleSelection(...nodes);
                } else {
                    resetSelection(...nodes);
                }
            });
        }

        this.bindEventHandlers();
        this.highlightNodes();
        this.updateNodesCounts();

        this.graph.on(Events.CHANGE_DRAG_OVER, () => {
            this.highlightHoveredLink();
        });

        //we want to inject node during 'Drag and Drop' from toolbox
        this.graph.on(Events.ADD, (cell: dia.Element) => {
            if (isModelElement(cell)) {
                const linkBelowCell = this.getLinkBelowCell();
                this.handleInjectBetweenNodes(cell, linkBelowCell);
                setLinksHovered(cell.graph);
            }
        });

        this.panAndZoom.fitSmallAndLargeGraphs();
    }

    addNode(node: NodeType, position: Position): void {
        if (this.props.isFragment === true) return;

        const canAddNode =
            this.props.capabilities.editFrontend && NodeUtils.isNode(node) && NodeUtils.isAvailable(node, this.props.processDefinitionData);

        if (canAddNode) {
            this.props.nodeAdded(node, position);
        }
    }

    // eslint-disable-next-line react/no-deprecated
    componentWillUpdate(nextProps: Props): void {
        const processChanged =
            !isEqual(this.props.scenario.scenarioGraph, nextProps.scenario.scenarioGraph) ||
            !isEqual(this.props.scenario.validationResult, nextProps.scenario.validationResult) ||
            !isEqual(this.props.layout, nextProps.layout) ||
            !isEqual(this.props.processDefinitionData, nextProps.processDefinitionData);
        if (processChanged) {
            this.drawGraph(nextProps.scenario.scenarioGraph, nextProps.layout, nextProps.processDefinitionData);
        }

        //when e.g. layout changed we have to remember to highlight nodes
        const selectedNodesChanged = !isEqual(this.props.selectionState, nextProps.selectionState);
        if (processChanged || selectedNodesChanged) {
            this.highlightNodes(nextProps.selectionState, nextProps.scenario);
        }
    }

    componentDidUpdate(prevProps: Props): void {
        const { processCounts } = this.props;
        if (!isEqual(processCounts, prevProps.processCounts)) {
            this.updateNodesCounts();
        }
        if (this.props.isFragment !== true && prevProps.isFragment !== true) {
            if (this.props.isDraggingOver !== prevProps.isDraggingOver) {
                this.highlightHoveredLink(!this.props.isDraggingOver);
            }
        }
        if (this.props.isPristine) {
            this._prepareContentForExport();
        }
    }

    updateNodesCounts(): void {
        const { processCounts, userSettings } = this.props;
        const nodes = this.graph.getElements().filter(isModelElement);
        nodes.forEach(updateNodeCounts(processCounts, userSettings));
    }

    zoomIn(): void {
        this.panAndZoom.zoomIn();
    }

    zoomOut(): void {
        this.panAndZoom.zoomOut();
    }

    async exportGraph(): Promise<string> {
        return await prepareSvg(this._exportGraphOptions);
    }

    twoWayValidateConnection = (
        { model: source }: dia.CellView,
        magnetS: SVGElement,
        { model: target }: dia.CellView,
        magnetT: SVGElement,
        end: dia.LinkEnd,
        linkView: dia.LinkView,
    ): boolean => {
        if (source === target) {
            return false;
        }
        if (magnetS.getAttribute("port") === magnetT.getAttribute("port")) {
            return false;
        }
        return this.validateConnection(source, target, magnetT, linkView) || this.validateConnection(target, source, magnetS, linkView);
    };

    validateConnection = (source: dia.Cell, target: dia.Cell, magnetT: SVGElement, linkView: dia.LinkView): boolean => {
        if (magnetT.getAttribute("port") !== "In") {
            return false;
        }
        if (this.graph.getPredecessors(source as dia.Element).includes(target as dia.Element)) {
            return false;
        }
        const from = source.id.toString();
        const to = target.id.toString();
        const previousEdge = linkView.model.attributes.edgeData || {};
        const { scenario, processDefinitionData } = this.props;
        return NodeUtils.canMakeLink(from, to, scenario.scenarioGraph, processDefinitionData, previousEdge);
    };

    validateMagnet = ({ model }: dia.CellView, magnet: SVGElement) => {
        const { scenario, processDefinitionData } = this.props;
        const from = NodeUtils.getNodeById(model.id.toString(), scenario.scenarioGraph);
        const port = magnet.getAttribute("port");
        if (port === "Out") {
            const nodeOutputs = NodeUtils.nodeOutputs(from.id, scenario.scenarioGraph);
            return NodeUtils.canHaveMoreOutputs(from, nodeOutputs, processDefinitionData);
        }
        if (port === "In") {
            const nodeInputs = NodeUtils.nodeInputs(from.id, scenario.scenarioGraph);
            return NodeUtils.canHaveMoreInputs(from, nodeInputs, processDefinitionData);
        }
    };

    disconnectPreviousEdge = (from: NodeId, to: NodeId): void => {
        if (this.props.isFragment !== true && this.graphContainsEdge(from, to)) {
            this.props.nodesDisconnected(from, to);
        }
    };

    graphContainsEdge(from: NodeId, to: NodeId): boolean {
        return this.props.scenario.scenarioGraph.edges.some((edge) => edge.from === from && edge.to === to);
    }

    handleInjectBetweenNodes = (middleMan: shapes.devs.Model, linkBelowCell?: dia.Link): void => {
        if (this.props.isFragment === true) return;

        const { scenario, injectNode, processDefinitionData } = this.props;

        if (linkBelowCell && middleMan) {
            const { sourceNode, targetNode } = getLinkNodes(linkBelowCell);
            const middleManNode = middleMan.get("nodeData");

            const canInjectNode = GraphUtils.canInjectNode(
                scenario.scenarioGraph,
                sourceNode.id,
                middleManNode.id,
                targetNode.id,
                processDefinitionData,
            );

            if (canInjectNode) {
                injectNode(sourceNode, middleManNode, targetNode, linkBelowCell.attributes.edgeData);
            }
        }
    };

    _prepareContentForExport = (): void => {
        const { options, defs } = this.processGraphPaper;
        this._exportGraphOptions = {
            options: cloneDeep(options),
            defs: defs.cloneNode(true) as SVGDefsElement,
        };
    };

    highlightNodes = (selectedNodeIds: string[] = [], process = this.props.scenario): void => {
        this.processGraphPaper.freeze();
        const elements = this.graph.getElements();
        elements.forEach((cell) => {
            this.unhighlightCell(cell, "node-validation-error");
            this.unhighlightCell(cell, "node-focused");
            this.unhighlightCell(cell, "node-focused-with-validation-error");
        });

        const validationErrors = ProcessUtils.getValidationErrors(process);
        const invalidNodeIds = [...keys(validationErrors?.invalidNodes), ...validationErrors.globalErrors.flatMap((e) => e.nodeIds)];

        // fast indicator for loose nodes, faster than async validation
        elements.forEach((el) => {
            const nodeId = el.id.toString();
            if (!invalidNodeIds.includes(nodeId) && el.getPort("In") && !this.graph.getNeighbors(el, { inbound: true }).length) {
                invalidNodeIds.push(nodeId);
            }
        });

        invalidNodeIds.forEach((id) =>
            selectedNodeIds.includes(id)
                ? this.highlightNode(id, "node-focused-with-validation-error")
                : this.highlightNode(id, "node-validation-error"),
        );

        selectedNodeIds.forEach((id) => {
            if (!invalidNodeIds.includes(id)) {
                this.highlightNode(id, "node-focused");
            }
        });
        this.processGraphPaper.unfreeze();
    };

    highlightCell(cell: dia.Cell, className: string): void {
        this.processGraphPaper.findViewByModel(cell).highlight(null, {
            highlighter: {
                name: "addClass",
                options: { className },
            },
        });
    }

    unhighlightCell(cell: dia.Cell, className: string): void {
        this.processGraphPaper.findViewByModel(cell).unhighlight(null, {
            highlighter: {
                name: "addClass",
                options: { className },
            },
        });
    }

    highlightNode = (nodeId: NodeId, highlightClass: string): void => {
        const cell = this.graph.getCell(nodeId);
        if (cell) {
            //prevent `properties` node highlighting
            this.highlightCell(cell, highlightClass);
        }
    };

    changeLayoutIfNeeded = (): void => {
        if (this.props.isFragment === true) return;

        const { layout, layoutChanged } = this.props;

        const elements = this.graph.getElements().filter(isModelElement);
        const collection = elements.map((el) => {
            const { x, y } = el.get("position");
            return { id: el.id, position: { x, y } };
        });

        const iteratee = (e) => e.id;
        const newLayout = sortBy(collection, iteratee);
        const oldLayout = sortBy(layout, iteratee);

        if (!isEqual(oldLayout, newLayout)) {
            layoutChanged(newLayout);
        }
    };

    hooverHandling(): void {
        this.processGraphPaper.on(Events.CELL_MOUSEOVER, (cellView: dia.CellView) => {
            const model = cellView.model;
            this.showLabelOnHover(model);
        });
    }

    //needed for proper switch/filter label handling
    showLabelOnHover(model: dia.Cell): dia.Cell {
        model.toFront();
        return model;
    }

    moveSelectedNodesRelatively(movedNodeId: string, position: Position): dia.Cell[] {
        this.redrawing = true;
        const nodeIdsToBeMoved = without(this.props.selectionState, movedNodeId);
        const cellsToBeMoved = nodeIdsToBeMoved.map((nodeId) => this.graph.getCell(nodeId));
        const { position: originalPosition } = this.findNodeInLayout(movedNodeId);
        const offset = { x: position.x - originalPosition.x, y: position.y - originalPosition.y };
        cellsToBeMoved.filter(isModelElement).forEach((cell) => {
            const { position: originalPosition } = this.findNodeInLayout(cell.id.toString());
            cell.position(originalPosition.x + offset.x, originalPosition.y + offset.y);
        });
        this.redrawing = false;
        return cellsToBeMoved;
    }

    findNodeInLayout(nodeId: NodeId): NodePosition {
        return this.props.layout.find((n) => n.id === nodeId);
    }

    render(): JSX.Element {
        const { divId, isFragment } = this.props;
        return (
            <>
                <GraphPaperContainer
                    ref={this.setEspGraphRef}
                    onResize={isFragment ? () => this.panAndZoom.fitSmallAndLargeGraphs() : null}
                    id={divId}
                />
                {!isFragment && <ComponentDragPreview scale={this.zoom} />}
            </>
        );
    }

    private bindNodeRemove() {
        this.graph.on(Events.REMOVE, (e: dia.Cell) => {
            if (this.props.isFragment !== true && !this.redrawing && e.isLink()) {
                this.props.nodesDisconnected(e.attributes.source.id, e.attributes.target.id);
            }
        });
    }

    private bindNodesMoving(): void {
        this.graph.on(Events.CHANGE_POSITION, (element: dia.Cell, position: Position) => {
            if (this.redrawing || !isModelElement(element)) {
                return;
            }

            const movingCells: dia.Cell[] = [element];
            const nodeId = element.id.toString();

            if (this.props.selectionState?.includes(nodeId)) {
                const movedNodes = this.moveSelectedNodesRelatively(nodeId, position);
                movingCells.push(...movedNodes);
            }

            this.panAndZoom.panToCells(movingCells, this.adjustViewport());
        });
    }

    private viewportAdjustment: { left: number; right: number } = { left: 0, right: 0 };
    adjustViewport = (adjustment: { left?: number; right?: number } = {}) => {
        this.viewportAdjustment = { ...this.viewportAdjustment, ...adjustment };
        const { x, y, height, width } = this.processGraphPaper.el.getBoundingClientRect();
        return new g.Rect({
            y,
            height,
            x: x + this.viewportAdjustment.left,
            width: width - this.viewportAdjustment.left - this.viewportAdjustment.right,
        });
    };
}
