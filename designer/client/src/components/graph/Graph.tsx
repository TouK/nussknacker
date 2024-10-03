import { Theme } from "@mui/material";
import { dia, g, shapes } from "jointjs";
import "jointjs/dist/joint.min.css";
import { cloneDeep, debounce, isEmpty, isEqual, keys, sortBy, without } from "lodash";
import React from "react";
import { UseTranslationResponse } from "react-i18next";
import { Layout, NodePosition, Position } from "../../actions/nk";
import { isEdgeEditable } from "../../common/EdgeUtils";
import User from "../../common/models/User";
import ProcessUtils from "../../common/ProcessUtils";
import { EventTrackingSelector, EventTrackingType, TrackEventParams } from "../../containers/event-tracking";
import { isTouchEvent, LONG_PRESS_TIME } from "../../helpers/detectDevice";
import { batchGroupBy } from "../../reducers/graph/batchGroupBy";
import { UserSettings } from "../../reducers/userSettings";
import { Edge, NodeId, NodeType, ProcessDefinitionData, ScenarioGraph } from "../../types";
import { ComponentDragPreview } from "../ComponentDragPreview";
import { Scenario } from "../Process/types";
import { createUniqueArrowMarker } from "./arrowMarker";
import { updateNodeCounts } from "./EspNode/element";
import { getDefaultLinkCreator } from "./EspNode/link";
import { applyCellChanges, calcLayout, createPaper, isModelElement } from "./GraphPartialsInTS";
import { getCellsToLayout } from "./GraphPartialsInTS/calcLayout";
import { isEdgeConnected } from "./GraphPartialsInTS/EdgeUtils";
import { updateLayout } from "./GraphPartialsInTS/updateLayout";
import { dragHovered, nodeFocused, nodeValidationError } from "./graphStyledWrapper";
import NodeUtils from "./NodeUtils";
import { PanZoomPlugin } from "./PanZoomPlugin";
import { PaperContainer } from "./paperContainer";
import { rafThrottle } from "./rafThrottle";
import {
    RangeSelectedEventData,
    RangeSelectEvents,
    RangeSelectPlugin,
    RangeSelectStartEventData,
    SelectionMode,
} from "./RangeSelectPlugin";
import { prepareSvg } from "./svg-export/prepareSvg";
import { Events, GraphProps } from "./types";
import { filterDragHovered, getLinkNodes, setLinksHovered } from "./utils/dragHelpers";
import * as GraphUtils from "./utils/graphUtils";
import { handleGraphEvent } from "./utils/graphUtils";

function clamp(number: number, max: number) {
    return Math.round(Math.min(max, Math.max(-max, number)));
}

type Props = GraphProps & {
    processCategory: string;
    processDefinitionData: ProcessDefinitionData;
    loggedUser: Partial<User>;
    selectionState: NodeId[];
    userSettings: UserSettings;
    showModalNodeDetails: (node: NodeType, scenario: Scenario, readonly?: boolean) => void;
    isPristine?: boolean;
    theme: Theme;
    translation: UseTranslationResponse<any, any>;
    handleStatisticsEvent: (event: TrackEventParams) => void;
};

function handleActionOnLongPress<T extends dia.CellView>(
    shortPressAction: ((cellView: T, event: dia.Event) => void) | null,
    longPressAction: (cellView: T, event: dia.Event) => void,
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

            longPressAction(cellView, evt);
        }, longPressTime);
    };
}

export class Graph extends React.Component<Props> {
    redrawing = false;

    directedLayout = (cellsToLayout: dia.Cell[] = []): void => {
        this.redrawing = true;
        calcLayout(this.graph, cellsToLayout);
        this.redrawing = false;
        this.changeLayoutIfNeeded();
    };

    createPaper = (): dia.Paper => {
        const { theme } = this.props;

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
                    return {
                        vertexAdd: false,
                        labelMove: false,
                    };
                }
                return true;
            },
        });

        const uniqueArrowMarker = createUniqueArrowMarker(paper, theme);
        // arrow id from paper is needed, so we have to mutate this
        paper.options.defaultLink = (cellView, magnet) => {
            // actual props are needed when link is created
            const linkCreator = getDefaultLinkCreator(
                uniqueArrowMarker,
                this.props.scenario.scenarioGraph,
                this.props.processDefinitionData,
                this.props.theme,
            );
            return linkCreator(cellView, magnet);
        };

        return paper;
    };

    private bindMoveWithEdge(cellView: dia.CellView) {
        const { paper, model } = cellView;
        const cell = this.graph.getCell(model.id);
        const border = 80;

        const mousePosition = new g.Point(this.viewport.center());

        const updateMousePosition = (cellView: dia.CellView, event: dia.Event) => {
            mousePosition.update(event.clientX, event.clientY);
        };

        let frame: number;
        const panWithEdge = () => {
            const rect = this.viewport.clone().inflate(-border, -border);
            if (!rect.containsPoint(mousePosition)) {
                const distance = rect.pointNearestToPoint(mousePosition).difference(mousePosition);
                const x = clamp(distance.x / 2, border / 4);
                const y = clamp(distance.y / 2, border / 4);
                this.panAndZoom.panBy({
                    x,
                    y,
                });
                if (isModelElement(cell)) {
                    const p = cell.position();
                    cell.position(p.x - x, p.y - y);
                }
            }
            frame = requestAnimationFrame(panWithEdge);
        };
        frame = requestAnimationFrame(panWithEdge);

        paper.on(Events.CELL_POINTERMOVE, updateMousePosition);
        return () => {
            paper.off(Events.CELL_POINTERMOVE, updateMousePosition);
            cancelAnimationFrame(frame);
        };
    }

    private bindPaperEvents() {
        this.processGraphPaper
            //trigger new custom event on finished cell move
            .on(Events.CELL_POINTERDOWN, (cellView: dia.CellView) => {
                const { model, paper } = cellView;

                const moveCallback = () => {
                    cellView.once(Events.CELL_POINTERUP, () => {
                        cellView.trigger(Events.CELL_MOVED, cellView);
                        paper.trigger(Events.CELL_MOVED, cellView);
                    });
                };

                model.once(Events.CHANGE_POSITION, moveCallback);
                const cleanup = this.bindMoveWithEdge(cellView);

                cellView.once(Events.CELL_POINTERUP, () => {
                    model.off(Events.CHANGE_POSITION, moveCallback);
                    cleanup();
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
            });
    }

    private getLinkBelowCell() {
        const links = this.graph.getLinks();
        const [linkBelowCell] = filterDragHovered(links);
        return linkBelowCell;
    }

    drawGraph = (scenarioGraph: ScenarioGraph, layout: Layout, processDefinitionData: ProcessDefinitionData): void => {
        const { theme } = this.props;

        this.redrawing = true;

        applyCellChanges(this.processGraphPaper, scenarioGraph, processDefinitionData, theme);

        if (isEmpty(layout)) {
            this.forceLayout();
        } else {
            updateLayout(this.graph, layout);
            this.redrawing = false;
        }
    };

    setEspGraphRef = (instance: HTMLElement): void => {
        this.instance = instance;
        if (this.props.isFragment !== true && this.props.connectDropTarget && instance) {
            this.props.connectDropTarget(instance);
        }
    };
    graph: dia.Graph;
    processGraphPaper: dia.Paper;
    highlightHoveredLink = rafThrottle((forceDisable?: boolean) => {
        this.processGraphPaper.freeze();

        const links = this.graph.getLinks();
        links.forEach((l) => this.#unhighlightCell(l, dragHovered));

        if (!forceDisable) {
            const [active] = filterDragHovered(links);
            if (active) {
                this.#highlightCell(active, dragHovered);
                active.toBack();
            }
        }

        this.processGraphPaper.unfreeze();
    });
    private panAndZoom: PanZoomPlugin;

    fit = debounce((cellsToFit?: dia.Cell[]): void => {
        const area = cellsToFit?.length ? this.graph.getCellsBBox(cellsToFit) : this.processGraphPaper.getContentArea();
        this.panAndZoom.fitContent(area, this.viewport);
    }, 250);

    fitToNode = (nodeId: NodeId): void => {
        const cellToFit = this.graph.getCells().find((c) => c.id === nodeId);
        const area = cellToFit ? this.graph.getCellsBBox([cellToFit]) : this.processGraphPaper.getContentArea();

        const autoZoomThreshold = 0.63;
        const withCurrentZoomValue = this.zoom > autoZoomThreshold;
        this.panAndZoom.fitContent(area, this.viewport, withCurrentZoomValue ? this.zoom : autoZoomThreshold);
    };

    forceLayout = debounce((readOnly?: boolean) => {
        const cellsToLayout = getCellsToLayout(this.graph, this.props.selectionState);
        if (!readOnly) {
            this.directedLayout(cellsToLayout);
        }
        this.fit(cellsToLayout);
    }, 250);

    private _exportGraphOptions: Pick<dia.Paper, "options" | "defs">;
    private instance: HTMLElement;

    constructor(props: Props) {
        super(props);
        this.graph = new dia.Graph();
        this.bindNodeRemove();
        this.bindNodesMoving();
    }

    get zoom(): number {
        return this.panAndZoom?.zoom || 1;
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

        this.processGraphPaper.on(Events.CELL_POINTERDOWN, handleGraphEvent(handleActionOnLongPress(showNodeDetails, selectNode), null));
        this.processGraphPaper.on(
            Events.LINK_POINTERDOWN,
            handleGraphEvent(
                handleActionOnLongPress(null, ({ model }) => model.remove(), LONG_PRESS_TIME * 1.5),
                null,
            ),
        );

        this.processGraphPaper.on(Events.CELL_POINTERCLICK, handleGraphEvent(null, selectNode));
        this.processGraphPaper.on(Events.CELL_POINTERDBLCLICK, handleGraphEvent(null, showNodeDetails));

        this.hooverHandling();
    }

    componentDidMount(): void {
        this.processGraphPaper = this.createPaper();
        this.drawGraph(this.props.scenario.scenarioGraph, this.props.layout, this.props.processDefinitionData);
        this.processGraphPaper.unfreeze();
        this._prepareContentForExport();

        // event handlers binding below. order sometimes matters
        this.panAndZoom = new PanZoomPlugin(this.processGraphPaper, this.viewport);

        if (this.props.isFragment !== true && this.props.nodeSelectionEnabled) {
            const { toggleSelection, resetSelection, handleStatisticsEvent } = this.props;
            new RangeSelectPlugin(this.processGraphPaper, this.props.theme);
            this.processGraphPaper
                .on(RangeSelectEvents.START, (data: RangeSelectStartEventData) => {
                    handleStatisticsEvent({
                        selector: EventTrackingSelector.RangeSelectNodes,
                        event: data.source === "pointer" ? EventTrackingType.DoubleClick : EventTrackingType.KeyboardAndClick,
                    });

                    this.panAndZoom.toggle(false);
                })
                .on(RangeSelectEvents.STOP, () => {
                    this.panAndZoom.toggle(true);
                })
                .on(RangeSelectEvents.RESET, () => {
                    resetSelection();
                })
                .on(RangeSelectEvents.SELECTED, ({ elements, mode }: RangeSelectedEventData) => {
                    const nodes = elements.filter((el) => isModelElement(el)).map(({ id }) => id.toString());
                    if (mode === SelectionMode.toggle) {
                        toggleSelection(...nodes);
                    } else {
                        resetSelection(...nodes);
                    }
                });
        }

        this.bindEventHandlers();
        this.#highlightNodes();
        this.updateNodesCounts();
        this.bindPaperEvents();

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

        this.fit();
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
            this.#highlightNodes(nextProps.selectionState, nextProps.scenario);
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
        const { processCounts, userSettings, theme } = this.props;
        const nodes = this.graph.getElements().filter(isModelElement);
        nodes.forEach(updateNodeCounts(processCounts, userSettings, theme));
    }

    zoomIn(): void {
        this.panAndZoom.zoomIn();
    }

    zoomOut(): void {
        this.panAndZoom.zoomOut();
    }

    async exportGraph(): Promise<string> {
        return await prepareSvg(this._exportGraphOptions, this.props.theme);
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
        if (this.props.isFragment !== true && this.#graphContainsEdge(from, to)) {
            this.props.nodesDisconnected(from, to);
        }
    };

    #graphContainsEdge(from: NodeId, to: NodeId): boolean {
        return this.props.scenario.scenarioGraph.edges.some((edge) => edge.from === from && edge.to === to);
    }

    #findLinkForEdge(edge: Edge): dia.Link {
        if (!isEdgeConnected(edge) || !this.#graphContainsEdge(edge.from, edge.to)) {
            return null;
        }
        const links = this.graph.getLinks();
        return links.find(({ attributes: { edgeData } }) => edgeData.from === edge.from && edgeData.to === edge.to);
    }

    highlightEdge(edge: Edge, className: string): void {
        const link = this.#findLinkForEdge(edge);
        link?.toFront();
        this.#highlightCell(link, className);
    }

    unhighlightEdge(edge: Edge, className: string): void {
        const link = this.#findLinkForEdge(edge);
        this.#unhighlightCell(link, className);
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

    #highlightNodes = (selectedNodeIds: string[] = [], process = this.props.scenario): void => {
        this.processGraphPaper.freeze();
        const elements = this.graph.getElements();
        elements.forEach((cell) => {
            this.#unhighlightCell(cell, nodeValidationError);
            this.#unhighlightCell(cell, nodeFocused);
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

        invalidNodeIds.forEach((id) => this.highlightNode(id, nodeValidationError));
        selectedNodeIds.forEach((id) => this.highlightNode(id, nodeFocused));

        this.processGraphPaper.unfreeze();
    };

    #highlightCell(cell: dia.Cell, className: string): void {
        this.processGraphPaper.findViewByModel(cell)?.highlight(null, {
            highlighter: {
                name: "addClass",
                options: { className },
            },
        });
    }

    #unhighlightCell(cell: dia.Cell, className: string): void {
        this.processGraphPaper.findViewByModel(cell)?.unhighlight(null, {
            highlighter: {
                name: "addClass",
                options: { className },
            },
        });
    }

    highlightNode = (nodeId: NodeId, className: string): void => {
        const cell = this.graph.getCell(nodeId);
        cell?.toFront();
        this.#highlightCell(cell, className);
    };

    unhighlightNode = (nodeId: NodeId, className: string): void => {
        const cell = this.graph.getCell(nodeId);
        this.#unhighlightCell(cell, className);
    };

    changeLayoutIfNeeded = (): void => {
        if (this.props.isFragment === true) return;

        const { layout, layoutChanged } = this.props;

        const elements = this.graph.getElements().filter(isModelElement);
        const collection = elements.map((el) => {
            const { x, y } = el.get("position");
            return {
                id: el.id,
                position: {
                    x,
                    y,
                },
            };
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

    private moveSelectedNodesRelatively(movedNodeId: string, position: Position): dia.Cell[] {
        this.redrawing = true;
        const nodeIdsToBeMoved = without(this.props.selectionState, movedNodeId);
        const cellsToBeMoved = nodeIdsToBeMoved.map((nodeId) => this.graph.getCell(nodeId)).filter(isModelElement);
        const { position: originalPosition } = this.findNodeInLayout(movedNodeId);
        const offset = {
            x: position.x - originalPosition.x,
            y: position.y - originalPosition.y,
        };
        cellsToBeMoved.forEach((cell) => {
            const { position: originalPosition } = this.findNodeInLayout(cell.id.toString());
            cell.position(originalPosition.x + offset.x, originalPosition.y + offset.y, { group: true });
        });
        this.redrawing = false;
        return cellsToBeMoved;
    }

    findNodeInLayout(nodeId: NodeId): NodePosition {
        return this.props.layout.find((n) => n.id === nodeId);
    }

    render(): React.JSX.Element {
        const { divId, isFragment } = this.props;
        return (
            <>
                {/* for now this can't use theme nor other dynamic props to maintain reference with jointjs. */}
                <PaperContainer ref={this.setEspGraphRef} onResize={isFragment ? () => this.fit() : null} id={divId} />
                {!isFragment && <ComponentDragPreview scale={() => this.zoom} />}
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
        this.graph.on(
            Events.CHANGE_POSITION,
            rafThrottle((element: dia.Cell, position: dia.Point, options) => {
                if (this.redrawing || !isModelElement(element)) return;
                if (options.group) return;

                const movingCells: dia.Cell[] = [element];
                const nodeId = element.id.toString();

                if (this.props.selectionState?.includes(nodeId)) {
                    const movedNodes = this.moveSelectedNodesRelatively(nodeId, position);
                    movingCells.push(...movedNodes);
                }
            }),
        );
    }

    private viewportAdjustment: {
        left: number;
        right: number;
    } = {
        left: 0,
        right: 0,
    };
    adjustViewport: (adjustment?: { left?: number; right?: number }) => g.Rect = (
        adjustment: {
            left?: number;
            right?: number;
        } = {},
    ) => {
        this.viewportAdjustment = { ...this.viewportAdjustment, ...adjustment };
        const { y, height, width } = this.processGraphPaper.el.getBoundingClientRect();
        return new g.Rect({
            y,
            height,
            x: this.viewportAdjustment.left,
            width: width - this.viewportAdjustment.left - this.viewportAdjustment.right,
        });
    };

    get viewport(): g.Rect {
        return this.adjustViewport();
    }
}
