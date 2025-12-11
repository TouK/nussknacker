import type { Theme } from "@mui/material";
import { dia, g, shapes } from "jointjs";
import "jointjs/dist/joint.min.css";
import { cloneDeep, debounce, isEmpty, isEqual, keys, without } from "lodash";
import React from "react";
import type { UseTranslationResponse } from "react-i18next";

import { moveNodeInject, moveNodePlain, moveNodeReplace } from "../../actions/nk/editNode";
import { nodesConnected, nodesDisconnected, stickyNoteSetErrors, stickyNoteUpdated } from "../../actions/nk/node";
import { resetSelection, toggleSelection } from "../../actions/nk/selection";
import type { Layout, NodePosition, Position } from "../../actions/nk/ui/layout";
import { layoutChanged } from "../../actions/nk/ui/layout";
import { isEdgeEditable } from "../../common/EdgeUtils";
import type User from "../../common/models/User";
import ProcessUtils from "../../common/ProcessUtils";
import type { TrackEventParams } from "../../containers/event-tracking/use-event-tracking";
import { EventTrackingSelector, EventTrackingType } from "../../containers/event-tracking/use-register-tracking-events";
import { isTouchEvent, LONG_PRESS_TIME } from "../../helpers/detectDevice";
import { batchGroupBy } from "../../reducers/graph/batchGroupBy";
import type { UserSettings } from "../../reducers/userSettings";
import type { Edge } from "../../types/edge";
import type { NodeId, NodeType } from "../../types/node";
import type { ProcessDefinitionData, ScenarioGraph } from "../../types/scenarioGraph";
import type { NodeValidationError } from "../../types/validation";
import { ComponentDragPreview } from "../ComponentDragPreview";
import type { Scenario } from "../Process/types";
import type { AlignCellsVariant } from "./alignCells";
import { alignCells } from "./alignCells";
import { createUniqueArrowMarker } from "./arrowMarker";
import { updateNodeCounts } from "./EspNode/element";
import { getDefaultLinkCreator } from "./EspNode/link";
import { applyCellChanges } from "./GraphPartialsInTS/applyCellChanges";
import { calcLayout, getCellsToLayout } from "./GraphPartialsInTS/calcLayout";
import { isConnected, isModelElement, isModelOrStickyNote, isStickyNoteElement } from "./GraphPartialsInTS/cellUtils";
import { createPaper } from "./GraphPartialsInTS/createPaper";
import { isEdgeConnected } from "./GraphPartialsInTS/EdgeUtils";
import { updateLayout } from "./GraphPartialsInTS/updateLayout";
import { dragHovered, nodeFocused, nodeValidationError } from "./graphStyledWrapper";
import NodeUtils from "./NodeUtils";
import { PanZoomPlugin } from "./PanZoomPlugin";
import { PaperContainer } from "./paperContainer";
import { rafThrottle } from "./rafThrottle";
import type { RangeSelectedEventData, RangeSelectStartEventData } from "./RangeSelectPlugin";
import { RangeSelectEvents, RangeSelectPlugin, SelectionMode } from "./RangeSelectPlugin";
import { StickyNoteElement, StickyNoteElementView } from "./StickyNoteElement";
import { prepareSvg } from "./svg-export/prepareSvg";
import type { GraphProps } from "./types";
import { Events } from "./types";
import { filterDragHovered, setDraggedOver } from "./utils/dragHelpers";
import { handleGraphEvent } from "./utils/graphUtils";

/**
 * WARNING: DO NOT EXTEND OR MODIFY THIS COMPONENT!
 *
 * This is an old, legacy **class-based** React component.
 * We are migrating our codebase to **functional components** with Hooks.
 *
 * Any new development should follow the functional + hooks approach.
 * This file exists only for backward compatibility until full refactor.
 *
 * If you need to add features, please:
 *  1. Create a new functional component instead.
 *  2. Use modern React best practices (hooks, context, etc.).
 *  3. Plan for replacing this component entirely.
 *
 * ⚠️ Editing this file will only increase technical debt.
 */

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

export function getNodeData(cell: dia.Cell, graph?: ScenarioGraph): NodeType {
    const nodeData = cell?.get(`nodeData`);
    return NodeUtils.getNodeById(nodeData?.id, graph) || nodeData;
}

export class Graph extends React.Component<Props> {
    redrawing = false;
    graph: dia.Graph;
    processGraphPaper: dia.Paper;
    highlightHoveredLink = rafThrottle((forceDisable?: boolean) => {
        const cells = this.graph.getCells();
        cells.forEach((l) => this.#unhighlightCell(l, dragHovered));
        this.lastHoveredCell = null;

        if (!forceDisable) {
            const [active] = filterDragHovered(cells);
            if (active) {
                this.#highlightCell(active, dragHovered);
                this.lastHoveredCell = active.toBack();
            }
        }
    });
    private panAndZoom: PanZoomPlugin;
    fit = debounce((cellsToFit?: dia.Cell[]): void => {
        const area = cellsToFit?.length ? this.graph.getCellsBBox(cellsToFit) : this.processGraphPaper.getContentArea();
        this.panAndZoom.fitContent(area, this.viewport);
    }, 250);
    private _exportGraphOptions: Pick<dia.Paper, "options" | "defs">;
    private instance: HTMLElement;
    private viewportAdjustment: {
        left: number;
        right: number;
    } = {
        left: 0,
        right: 0,
    };
    lastHoveredCell: dia.Cell;
    private readonly nuGraphNamespace: Record<string, any>;

    constructor(props: Props) {
        super(props);
        this.nuGraphNamespace = {
            ...shapes,
            stickyNote: {
                StickyNoteElement,
                StickyNoteElementView: StickyNoteElementView(props.userSettings),
            },
        };
        this.graph = new dia.Graph({}, { cellNamespace: this.nuGraphNamespace });
        this.bindNodeRemove();
        this.bindNodesMoving();
    }

    get zoom(): number {
        return this.panAndZoom?.zoom || 1;
    }

    get viewport(): g.Rect {
        return this.adjustViewport();
    }

    directedLayout = (cellsToLayout: dia.Cell[] = []): void => {
        this.redrawing = true;
        calcLayout(this.graph, cellsToLayout);
        this.redrawing = false;
        this.changeLayoutIfNeeded();
    };

    forceLayout = debounce((variant?: AlignCellsVariant, altMode?: boolean) => {
        const cellsToLayout = getCellsToLayout(this.graph, this.props.selectionState);
        if (!variant) {
            if (!altMode) {
                const nodesAndLinks = cellsToLayout.filter((cell) => !isStickyNoteElement(cell));
                this.directedLayout(nodesAndLinks);
            }
            return this.fit(cellsToLayout);
        }

        const elements = cellsToLayout.filter((cell) => cell.isElement());
        alignCells(variant, elements);
        this.changeLayoutIfNeeded();
        if (altMode) {
            this.fit(cellsToLayout);
        }
    }, 250);

    createPaper = (): dia.Paper => {
        const { theme } = this.props;

        const canEditFrontend = this.props.loggedUser.canEditFrontend(this.props.processCategory) && !this.props.readonly;
        const paper = createPaper({
            frozen: true,
            model: this.graph,
            el: this.getEspGraphRef(),
            validateConnection: this.twoWayValidateConnection,
            cellViewNamespace: this.nuGraphNamespace,
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

    componentDidMount(): void {
        this.processGraphPaper = this.createPaper();
        this.drawGraph(this.props.scenario.scenarioGraph, this.props.layout, this.props.processDefinitionData);
        this.processGraphPaper.unfreeze();
        this.processGraphPaper.hideTools();
        this._prepareContentForExport();

        // event handlers binding below. order sometimes matters
        this.panAndZoom = new PanZoomPlugin(this.processGraphPaper, this.viewport);

        if (this.props.isFragment !== true && this.props.nodeSelectionEnabled) {
            const { dispatch, handleStatisticsEvent } = this.props;
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
                    dispatch(resetSelection());
                })
                .on(RangeSelectEvents.SELECTED, ({ elements, mode }: RangeSelectedEventData) => {
                    const nodes = elements.filter((el) => isModelOrStickyNote(el)).map(({ id }) => id.toString());
                    if (mode === SelectionMode.toggle) {
                        dispatch(toggleSelection(nodes));
                    } else {
                        dispatch(resetSelection(...nodes));
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

        this.graph.on(Events.CELL_RESIZED, (cell: dia.Element) => {
            if (this.props.isFragment === true) return;
            if (isStickyNoteElement(cell)) {
                this.props.dispatch(stickyNoteUpdated(cell, null));
            }
        });

        this.graph.on(Events.CELL_CONTENT_UPDATED, (cell: dia.Element, content: string) => {
            if (this.props.isFragment === true) return;
            if (isStickyNoteElement(cell)) {
                this.props.dispatch(stickyNoteUpdated(cell, content));
            }
        });

        this.fit();
    }

    componentWillUnmount(): void {
        // force destroy event on model for plugins cleanup
        this.processGraphPaper.model.destroy();
    }

    drawGraph = (scenarioGraph: ScenarioGraph, layout: Layout, processDefinitionData: ProcessDefinitionData): void => {
        const { theme } = this.props;

        this.redrawing = true;
        applyCellChanges(this.processGraphPaper, scenarioGraph, processDefinitionData, theme, this.props.userSettings);

        if (isEmpty(layout)) {
            if (scenarioGraph.nodes.length > 0) {
                this.forceLayout();
            }
            return;
        }

        updateLayout(this.graph, layout);
        this.redrawing = false;
    };

    setEspGraphRef = (instance: HTMLElement): void => {
        this.instance = instance;
        if (this.props.isFragment !== true && this.props.connectDropTarget && instance) {
            this.props.connectDropTarget(instance);
        }
    };

    fitToNode = (nodeId: NodeId): void => {
        const cellToFit = this.graph.getCells().find((c) => c.id === nodeId);
        const area = cellToFit ? this.graph.getCellsBBox([cellToFit]) : this.processGraphPaper.getContentArea();

        const autoZoomThreshold = 0.63;
        const withCurrentZoomValue = this.zoom > autoZoomThreshold;
        this.panAndZoom.fitContent(area, this.viewport, withCurrentZoomValue ? this.zoom : autoZoomThreshold);
    };

    getEspGraphRef = (): HTMLElement => this.instance;

    bindEventHandlers(): void {
        const showNodeDetails = (cellView: dia.CellView, event) => {
            if (event.target.closest("[port]")) return;
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
            if (evt.target.closest("[port]")) return;
            if (this.props.isFragment === true) return;
            if (this.props.nodeSelectionEnabled) {
                const nodeDataId = cellView.model.attributes.nodeData?.id;
                if (!nodeDataId) {
                    return;
                }

                if (evt.shiftKey || evt.ctrlKey || evt.metaKey || isTouchEvent(evt)) {
                    this.props.dispatch(toggleSelection(nodeDataId));
                } else {
                    this.props.dispatch(resetSelection(nodeDataId));
                }
            }
        };

        this.processGraphPaper.on(
            Events.CELL_POINTERDOWN,
            handleGraphEvent(handleActionOnLongPress(showNodeDetails, selectNode), null, (view) => !isStickyNoteElement(view.model)),
        );
        this.processGraphPaper.on(
            Events.LINK_POINTERDOWN,
            handleGraphEvent(
                handleActionOnLongPress(null, ({ model }) => model.remove(), LONG_PRESS_TIME * 1.5),
                null,
            ),
        );

        this.processGraphPaper.on(
            Events.CELL_POINTERCLICK,
            handleGraphEvent(null, selectNode, (view) => !isStickyNoteElement(view.model)),
        );
        this.processGraphPaper.on(Events.CELL_POINTERDBLCLICK, handleGraphEvent(null, showNodeDetails));

        this.hooverHandling();
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
        if (!isEmpty(this.props.processCounts) || !isEmpty(prevProps.processCounts)) {
            this.updateNodesCounts(); // TODO: look here for live data animations lag
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
            this.props.dispatch(nodesDisconnected(from, to));
        }
    };

    highlightEdge(edge: Edge, className: string): void {
        const link = this.#findLinkForEdge(edge);
        link?.toFront();
        this.#highlightCell(link, className);
    }

    unhighlightEdge(edge: Edge, className: string): void {
        const link = this.#findLinkForEdge(edge);
        this.#unhighlightCell(link, className);
    }

    _prepareContentForExport = (): void => {
        const { options, defs } = this.processGraphPaper;
        this._exportGraphOptions = {
            options: cloneDeep(options),
            defs: defs.cloneNode(true) as SVGDefsElement,
        };
    };

    highlightNode = (nodeId: NodeId, className: string): void => {
        const cell = this.graph.getCell(nodeId);
        if (!isStickyNoteElement(cell)) {
            cell?.toFront();
        }
        this.#highlightCell(cell, className);
    };

    unhighlightNode = (nodeId: NodeId, className: string): void => {
        const cell = this.graph.getCell(nodeId);
        this.#unhighlightCell(cell, className);
    };

    changeLayoutIfNeeded = (): void => {
        if (this.props.isFragment === true) return;
        const layout = this.graph
            .getElements()
            .filter(isModelOrStickyNote)
            .map((el) => ({
                id: el.id.toString(),
                position: el.position().toJSON(),
            }));
        this.props.dispatch(layoutChanged(layout));
    };

    hooverHandling(): void {
        this.processGraphPaper.on(Events.CELL_MOUSEOVER, (cellView: dia.CellView) => {
            const model = cellView.model;
            this.showLabelOnHover(model);
        });
    }

    //needed for proper switch/filter label handling
    showLabelOnHover(model: dia.Cell): dia.Cell {
        if (isModelElement(model)) {
            model.toFront();
        }
        return model;
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
                if (isModelOrStickyNote(cell)) {
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

    private bindPaperEvents() {
        this.processGraphPaper
            //trigger new custom event on finished cell move
            .on(Events.CELL_POINTERDOWN, (cellView: dia.CellView) => {
                const { model, paper } = cellView;
                const context = {};

                model.once(
                    Events.CHANGE_POSITION,
                    () => {
                        cellView.once(Events.CELL_POINTERUP, () => {
                            cellView.trigger(Events.CELL_MOVED, cellView);
                            paper.trigger(Events.CELL_MOVED, cellView);
                        });
                    },
                    context,
                );

                // pointerDown initiated move only
                model.on(
                    Events.CHANGE_POSITION,
                    (el: dia.Element) => {
                        if (isModelElement(el) && !isConnected(el) && (el.hasPort("In") || el.hasPort("Out"))) {
                            setDraggedOver(el.graph, el.getBBox(), el);
                        }
                    },
                    context,
                );

                const cleanup = this.bindMoveWithEdge(cellView);

                cellView.once(Events.CELL_POINTERUP, () => {
                    setDraggedOver(this.graph);
                    model.off(null, null, context);
                    cleanup();
                });
            })
            //we want to inject node during 'Drag and Drop' from graph paper
            .on(Events.CELL_MOVED, (cellView: dia.CellView) => {
                if (this.props.isFragment === true) return;
                const { dispatch, scenario } = this.props;
                const { scenarioGraph } = scenario;
                const cellBelow = this.lastHoveredCell;
                if (isModelElement(cellView.model)) {
                    batchGroupBy.startOrExtend();
                    this.changeLayoutIfNeeded();
                    const nodeData = getNodeData(cellView.model, scenarioGraph);
                    const offset = cellView.model.position();
                    if (isModelElement(cellBelow)) {
                        dispatch(moveNodeReplace(nodeData, getNodeData(cellBelow, scenarioGraph)));
                    } else if (cellBelow?.isLink()) {
                        dispatch(moveNodeInject(nodeData, cellBelow.attributes.edgeData, offset));
                    } else {
                        dispatch(moveNodePlain(nodeData, offset));
                    }
                    cellView.model.toFront();
                }
                if (isStickyNoteElement(cellView.model)) this.changeLayoutIfNeeded();
            })
            .on(Events.LINK_CONNECT, (linkView: dia.LinkView, evt: dia.Event, targetView: dia.CellView, targetMagnet: SVGElement) => {
                if (this.props.isFragment === true) return;
                const isReversed = targetMagnet?.getAttribute("port") === "Out";
                const sourceView = linkView.getEndView("source");
                const type = linkView.model.attributes.edgeData?.edgeType;
                const from = sourceView?.model.attributes.nodeData;
                const to = targetView?.model.attributes.nodeData;

                if (from && to) {
                    const dispatch = this.props.dispatch;
                    dispatch(isReversed ? nodesConnected(to, from, type) : nodesConnected(from, to, type));
                }
            })
            .on(Events.LINK_DISCONNECT, ({ model }) => {
                this.disconnectPreviousEdge(model.attributes.edgeData.from, model.attributes.edgeData.to);
            });
    }

    #highlightNodes = (selectedNodeIds: string[] = [], scenario = this.props.scenario): void => {
        this.processGraphPaper.freeze();
        this.processGraphPaper.hideTools();
        const elements = this.graph.getElements();
        elements.forEach((cell) => {
            this.#unhighlightCell(cell, nodeValidationError);
            this.#unhighlightCell(cell, nodeFocused);
            if (isStickyNoteElement(cell) && selectedNodeIds.includes(cell.id.toString())) {
                cell.findView(this.processGraphPaper).showTools();
            }
        });

        const validationErrors = ProcessUtils.getValidationErrors(scenario);
        const invalidNodeKeys = [...keys(validationErrors?.invalidNodes)];
        const invalidFragmentNodes = this.#getInvalidFragmentNodes(invalidNodeKeys, scenario.scenarioGraph);
        const invalidNodeIds = [...invalidNodeKeys, ...validationErrors.globalErrors.flatMap((e) => e.nodeIds), ...invalidFragmentNodes];

        if (this.props.isFragment !== true) {
            const notes = elements.filter((el) => isModelOrStickyNote(el));
            if (notes.length > 0) {
                const stickyNotesErrors: Record<string, NodeValidationError[]> = Object.fromEntries(
                    notes.map(({ id }) => id.toString()).map((id) => [id, validationErrors?.invalidNodes[id]]),
                );
                this.props.dispatch(stickyNoteSetErrors(stickyNotesErrors));
            }
        }

        // fast indicator for loose nodes, faster than async validation
        elements.forEach((el) => {
            const nodeId = el.id.toString();
            if (!invalidNodeIds.includes(nodeId) && el.getPort("In") && !el.graph.getNeighbors(el, { inbound: true }).length) {
                invalidNodeIds.push(nodeId);
            }
        });

        invalidNodeIds.forEach((id) => this.highlightNode(id, nodeValidationError));
        selectedNodeIds.forEach((id) => this.highlightNode(id, nodeFocused));

        this.processGraphPaper.unfreeze();
    };

    #getInvalidFragmentNodes = (invalidNodeIds: string[], scenarioGraph: ScenarioGraph): string[] => {
        const invalidFragmentNodeIds: Set<string> = new Set();
        invalidNodeIds.forEach((invalidNodeId) => {
            if (NodeUtils.isFragmentNodeReference(invalidNodeId, scenarioGraph)) {
                const fragmentId = scenarioGraph.nodes.find((n) => invalidNodeId.startsWith(n.id))?.id;
                invalidFragmentNodeIds.add(fragmentId);
            }
        });
        return [...invalidFragmentNodeIds];
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

    private moveSelectedNodesRelatively(movedNodeId: string, position: Position): dia.Cell[] {
        this.redrawing = true;
        const nodeIdsToBeMoved = without(this.props.selectionState, movedNodeId);
        const cellsToBeMoved = nodeIdsToBeMoved.map((nodeId) => this.graph.getCell(nodeId)).filter(isModelOrStickyNote);
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

    private bindNodeRemove() {
        this.graph.on(Events.REMOVE, (e: dia.Cell) => {
            if (this.props.isFragment !== true && !this.redrawing && e.isLink()) {
                this.props.dispatch(nodesDisconnected(e.attributes.source.id, e.attributes.target.id));
            }
        });
    }

    private bindNodesMoving(): void {
        this.graph.on(
            Events.CHANGE_POSITION,
            rafThrottle((element: dia.Cell, position: dia.Point, options) => {
                if (this.redrawing || !isModelOrStickyNote(element)) return;
                const nodeId = element.id.toString();
                if (this.props.selectionState?.includes(nodeId)) {
                    this.moveSelectedNodesRelatively(nodeId, position);
                }
            }),
        );
    }
}
