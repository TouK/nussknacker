/* eslint-disable i18next/no-literal-string */
import {dia, shapes} from "jointjs"
import {cloneDeep, debounce, isEmpty, isEqual, keys, sortBy, without} from "lodash"
import React from "react"
import {findDOMNode} from "react-dom"
import "../../stylesheets/graph.styl"
import {filterDragHovered, getLinkNodes, setLinksHovered} from "./dragHelpers"
import {updateNodeCounts} from "./EspNode/element"
import {GraphPaperContainer} from "./focusable"
import {applyCellChanges, calcLayout, createPaper, isBackgroundObject, isModelElement} from "./GraphPartialsInTS"
import styles from "./graphTheme.styl"
import {Events} from "./joint-events"
import NodeUtils from "./NodeUtils"
import {PanZoomPlugin} from "./PanZoomPlugin"
import {RangeSelectedEventData, RangeSelectPlugin, SelectionMode} from "./RangeSelectPlugin"
import "./svg-export/export.styl"
import {prepareSvg} from "./svg-export/prepareSvg"
import * as GraphUtils from "./GraphUtils"
import {ComponentDragPreview} from "../ComponentDragPreview"
import {rafThrottle} from "./rafThrottle"
import {isEdgeEditable} from "../../common/EdgeUtils"
import {NodeId, NodeType, Process, ProcessDefinitionData} from "../../types"
import {Layout, NodePosition, Position} from "../../actions/nk"
import {UserSettings} from "../../reducers/userSettings"
import {GraphProps} from "./GraphWrapped"
import User from "../../common/models/User"
import {updateLayout} from "./GraphPartialsInTS/updateLayout"
import ProcessUtils from "../../common/ProcessUtils"

interface Props extends GraphProps {
  processCategory: string,
  processDefinitionData: ProcessDefinitionData,
  loggedUser: Partial<User>,
  selectionState: NodeId[],
  userSettings: UserSettings,
  showModalNodeDetails: (node: NodeType, process: Process, readonly?: boolean) => void,
  isPristine?: boolean,
}

export class Graph extends React.Component<Props> {

  redrawing = false

  directedLayout = (selectedItems: string[] = []): void => {
    this.redrawing = true
    calcLayout(this.graph, selectedItems)
    this.redrawing = false
    this.changeLayoutIfNeeded()
  }

  createPaper = (): dia.Paper => {
    const canEditFrontend = this.props.loggedUser.canEditFrontend(this.props.processCategory) && !this.props.readonly
    const paper = createPaper({
      model: this.graph,
      el: this.getEspGraphRef(),
      validateConnection: this.validateConnection,
      interactive: (cellView: dia.CellView) => {
        const {model} = cellView
        if (!canEditFrontend) {
          return false
        }
        if (model instanceof dia.Link) {
          // Disable the default vertex add and label move functionality on pointerdown.
          return {vertexAdd: false, labelMove: false}
        }
        if (isBackgroundObject(model)) {
          //Disable moving group rect
          return false
        }
        return true
      },
    })

    return paper
      //we want to inject node during 'Drag and Drop' from graph paper
      .on(Events.CELL_POINTERUP, (cell: dia.CellView) => {
        this.changeLayoutIfNeeded()
        if (isModelElement(cell.model)) {
          this.handleInjectBetweenNodes(cell.model)
        }
      })
      .on(Events.LINK_CONNECT, ({sourceView, targetView, model}) => {
        const from = sourceView?.model.attributes.nodeData
        const to = targetView?.model.attributes.nodeData
        if (from && to) {
          const type = model.attributes.edgeData?.edgeType
          this.props.nodesConnected(from, to, type)
        }
      })
      .on(Events.LINK_DISCONNECT, ({model}) => {
        this.disconnectPreviousEdge(model.attributes.edgeData.from, model.attributes.edgeData.to)
      })
  }

  drawGraph = (process: Process, layout: Layout, processDefinitionData: ProcessDefinitionData): void => {
    this.redrawing = true

    applyCellChanges(this.processGraphPaper, process, processDefinitionData)

    if (isEmpty(layout)) {
      this.directedLayout()
    } else {
      updateLayout(this.graph, layout)
      this.redrawing = false
    }

  }

  setEspGraphRef = ((instance: HTMLElement): void => {
    const {connectDropTarget} = this.props
    this.instance = instance
    if (connectDropTarget && instance) {
      // eslint-disable-next-line react/no-find-dom-node
      const node = findDOMNode(instance)
      connectDropTarget(node)
    }
  })
  graph: dia.Graph
  processGraphPaper: dia.Paper
  highlightHoveredLink = rafThrottle((forceDisable?: boolean) => {
    this.processGraphPaper.freeze()

    const links = this.graph.getLinks()
    links.forEach(l => this.unhighlightCell(l, styles.dragHovered))

    if (!forceDisable) {
      const [active] = filterDragHovered(links)
      if (active) {
        this.highlightCell(active, styles.dragHovered)
        active.toBack()
      }
    }

    this.processGraphPaper.unfreeze()
  })
  private panAndZoom: PanZoomPlugin
  forceLayout = debounce(() => {
    this.directedLayout(this.props.selectionState)
    this.panAndZoom.fitSmallAndLargeGraphs()
  }, 50)
  private _exportGraphOptions: Pick<dia.Paper, "options" | "defs">
  private instance: HTMLElement

  constructor(props: Props) {
    super(props)
    this.graph = new dia.Graph()
    this.bindNodeRemove()
    this.bindNodesMoving()
  }

  get zoom(): number {
    return this.panAndZoom?.zoom || 0
  }

  getEspGraphRef = (): HTMLElement => this.instance

  componentWillUnmount(): void {
    // force destroy event on model for plugins cleanup
    this.processGraphPaper.model.destroy()
  }

  bindEventHandlers(): void {
    const showNodeDetails = (cellView: dia.CellView) => {
      const {processToDisplay, readonly, nodeIdPrefixForSubprocessTests = ""} = this.props
      const {nodeData, edgeData} = cellView.model.attributes
      const nodeId = nodeData?.id || (isEdgeEditable(edgeData) ? edgeData.from : null)
      if (nodeId) {
        this.props.showModalNodeDetails({
          ...NodeUtils.getNodeById(nodeId, processToDisplay),
          id: nodeIdPrefixForSubprocessTests + nodeId,
        }, processToDisplay, readonly)
      }
    }
    const selectNode = (cellView, evt) => {
      if (this.props.nodeSelectionEnabled) {
        const nodeDataId = cellView.model.attributes.nodeData?.id
        if (!nodeDataId) {
          return
        }

        if (evt.shiftKey || evt.ctrlKey || evt.metaKey) {
          this.props.toggleSelection(nodeDataId)
        } else {
          this.props.resetSelection(nodeDataId)
        }
      }
    }
    const deselectNodes = (event: JQuery.Event) => {
      if (event.isPropagationStopped()) {
        return
      }
      if (this.props.fetchedProcessDetails) {
        this.props.resetSelection()
      }
    }

    this.processGraphPaper.on(Events.CELL_POINTERDBLCLICK, showNodeDetails)
    this.processGraphPaper.on(Events.CELL_POINTERCLICK, selectNode)
    this.processGraphPaper.on(Events.BLANK_POINTERUP, deselectNodes)
    this.hooverHandling()
  }

  componentDidMount(): void {
    this.processGraphPaper = this.createPaper()
    this.processGraphPaper.freeze()
    this.drawGraph(
      this.props.processToDisplay,
      this.props.layout,
      this.props.processDefinitionData,
    )
    this.processGraphPaper.unfreeze()
    this._prepareContentForExport()

    // event handlers binding below. order sometimes matters
    this.panAndZoom = new PanZoomPlugin(this.processGraphPaper)

    if (this.props.nodeSelectionEnabled) {
      new RangeSelectPlugin(this.processGraphPaper)
      this.processGraphPaper.on("rangeSelect:selected", ({elements, mode}: RangeSelectedEventData) => {
        const nodes = elements
          .filter(el => isModelElement(el))
          .map(({id}) => id.toString())
        if (mode === SelectionMode.toggle) {
          this.props.toggleSelection(...nodes)
        } else {
          this.props.resetSelection(...nodes)
        }
      })
    }

    this.bindEventHandlers()
    this.highlightNodes()
    this.updateNodesCounts()

    this.graph.on(Events.CHANGE_DRAG_OVER, () => {
      this.highlightHoveredLink()
    })

    //we want to inject node during 'Drag and Drop' from toolbox
    this.graph.on(Events.ADD, (cell: dia.Element) => {
      if (isModelElement(cell)) {
        this.handleInjectBetweenNodes(cell)
        setLinksHovered(cell.graph)
      }
    })

    this.panAndZoom.fitSmallAndLargeGraphs()
  }

  canAddNode(node: NodeType): boolean {
    return this.props.capabilities.editFrontend &&
      NodeUtils.isNode(node) &&
      NodeUtils.isAvailable(node, this.props.processDefinitionData, this.props.processCategory)
  }

  addNode(node: NodeType, position: Position): void {
    if (this.canAddNode(node)) {
      this.props.nodeAdded(node, position)
    }
  }

  // eslint-disable-next-line react/no-deprecated
  componentWillUpdate(nextProps: Props): void {
    const processChanged = !isEqual(this.props.processToDisplay, nextProps.processToDisplay) ||
      !isEqual(this.props.layout, nextProps.layout) ||
      !isEqual(this.props.processDefinitionData, nextProps.processDefinitionData)
    if (processChanged) {
      this.drawGraph(
        nextProps.processToDisplay,
        nextProps.layout,
        nextProps.processDefinitionData,
      )
    }

    //when e.g. layout changed we have to remember to highlight nodes
    const selectedNodesChanged = !isEqual(this.props.selectionState, nextProps.selectionState)
    if (processChanged || selectedNodesChanged) {
      this.highlightNodes(nextProps.selectionState, nextProps.processToDisplay)
    }
  }

  componentDidUpdate(prevProps: Props): void {
    const {processCounts} = this.props
    if (!isEqual(processCounts, prevProps.processCounts)) {
      this.updateNodesCounts()
    }
    if (this.props.isDraggingOver !== prevProps.isDraggingOver) {
      this.highlightHoveredLink(!this.props.isDraggingOver)
    }
    if (this.props.isPristine) {
      this._prepareContentForExport()
    }
  }

  updateNodesCounts(): void {
    const {processCounts, userSettings} = this.props
    const nodes = this.graph.getElements().filter(isModelElement)
    nodes.forEach(updateNodeCounts(processCounts, userSettings))
  }

  zoomIn(): void {
    this.panAndZoom.zoomIn()
  }

  zoomOut(): void {
    this.panAndZoom.zoomOut()
  }

  async exportGraph(): Promise<string> {
    return await prepareSvg(this._exportGraphOptions)
  }

  validateConnection = (cellViewS: dia.CellView, magnetS: SVGElement, cellViewT: dia.CellView, magnetT: SVGElement, end: dia.LinkEnd, linkView: dia.LinkView): boolean => {
    const from = cellViewS.model.id.toString()
    const to = cellViewT.model.id.toString()
    const previousEdge = linkView.model.attributes.edgeData || {}
    const {processToDisplay, processDefinitionData} = this.props
    return magnetT.getAttribute("port") === "In" && NodeUtils.canMakeLink(from, to, processToDisplay, processDefinitionData, previousEdge)
  }

  disconnectPreviousEdge = (from: NodeId, to: NodeId): void => {
    if (this.graphContainsEdge(from, to)) {
      this.props.nodesDisconnected(from, to)
    }
  }

  graphContainsEdge(from: NodeId, to: NodeId): boolean {
    return this.props.processToDisplay.edges.some(edge => edge.from === from && edge.to === to)
  }

  handleInjectBetweenNodes = (middleMan: shapes.devs.Model): void => {
    const {processToDisplay, injectNode, processDefinitionData} = this.props
    const links = this.graph.getLinks()
    const [linkBelowCell] = filterDragHovered(links)

    if (linkBelowCell && middleMan) {
      const {sourceNode, targetNode} = getLinkNodes(linkBelowCell)
      const middleManNode = middleMan.get("nodeData")

      const canInjectNode = GraphUtils.canInjectNode(
        processToDisplay,
        sourceNode.id,
        middleManNode.id,
        targetNode.id,
        processDefinitionData,
      )

      if (canInjectNode) {
        injectNode(
          sourceNode,
          middleManNode,
          targetNode,
          linkBelowCell.attributes.edgeData,
        )
      }
    }
  }

  _prepareContentForExport = (): void => {
    const {options, defs} = this.processGraphPaper
    this._exportGraphOptions = {
      options: cloneDeep(options),
      defs: defs.cloneNode(true) as SVGDefsElement,
    }
  }

  highlightNodes = (selectedNodeIds: string[] = [], process = this.props.processToDisplay): void => {
    this.processGraphPaper.freeze()
    this.graph.getCells().forEach(cell => {
      this.unhighlightCell(cell, "node-validation-error")
      this.unhighlightCell(cell, "node-focused")
      this.unhighlightCell(cell, "node-focused-with-validation-error")
    })

    const invalidNodeIds = keys(ProcessUtils.getValidationErrors(process)?.invalidNodes)

    invalidNodeIds.forEach(id => selectedNodeIds.includes(id) ?
      this.highlightNode(id, "node-focused-with-validation-error") :
      this.highlightNode(id, "node-validation-error"))

    selectedNodeIds.forEach(id => {
      if (!invalidNodeIds.includes(id)) {
        this.highlightNode(id, "node-focused")
      }
    })
    this.processGraphPaper.unfreeze()
  }

  highlightCell(cell: dia.Cell, className: string): void {
    this.processGraphPaper.findViewByModel(cell).highlight(null, {
      highlighter: {
        name: "addClass",
        options: {className: className},
      },
    })
  }

  unhighlightCell(cell: dia.Cell, className: string): void {
    this.processGraphPaper.findViewByModel(cell).unhighlight(null, {
      highlighter: {
        name: "addClass",
        options: {className: className},
      },
    })
  }

  highlightNode = (nodeId: NodeId, highlightClass: string): void => {
    const cell = this.graph.getCell(nodeId)
    if (cell) { //prevent `properties` node highlighting
      this.highlightCell(cell, highlightClass)
    }
  }

  changeLayoutIfNeeded = (): void => {
    const {layout, layoutChanged, isSubprocess} = this.props

    if (isSubprocess) {
      return
    }

    const elements = this.graph.getElements().filter(isModelElement)
    const collection = elements.map(el => {
      const {x, y} = el.get("position")
      return {id: el.id, position: {x, y}}
    })

    const iteratee = e => e.id
    const newLayout = sortBy(collection, iteratee)
    const oldLayout = sortBy(layout, iteratee)

    if (!isEqual(oldLayout, newLayout)) {
      layoutChanged(newLayout)
    }
  }

  hooverHandling(): void {
    this.processGraphPaper.on(Events.CELL_MOUSEOVER, (cellView: dia.CellView) => {
      const model = cellView.model
      this.showLabelOnHover(model)
      this.showBackgroundIcon(model)
    })
    this.processGraphPaper.on(Events.BLANK_MOUSEOVER, () => {
      this.hideBackgroundsIcons()
    })
  }

  //needed for proper switch/filter label handling
  showLabelOnHover(model: dia.Cell): dia.Cell {
    if (!isBackgroundObject(model)) {
      model.toFront()
    }
    return model
  }

  //background is below normal node, we cannot use normal hover/mouseover/mouseout...
  showBackgroundIcon(model: dia.Cell): void {
    if (isBackgroundObject(model)) {
      const el = model.findView(this.processGraphPaper).vel
      el.toggleClass("forced-hover", true)
    }
  }

  hideBackgroundsIcons(): void {
    this.graph.getElements().filter(isBackgroundObject).forEach(model => {
      const el = model.findView(this.processGraphPaper).vel
      el.toggleClass("forced-hover", false)
    })
  }

  moveSelectedNodesRelatively(element: shapes.devs.Model, position: Position): void {
    this.redrawing = true
    const movedNodeId = element.id.toString()
    const nodeIdsToBeMoved = without(this.props.selectionState, movedNodeId)
    const cellsToBeMoved = nodeIdsToBeMoved.map(nodeId => this.graph.getCell(nodeId))
    const {position: originalPosition} = this.findNodeInLayout(movedNodeId)
    const offset = {x: position.x - originalPosition.x, y: position.y - originalPosition.y}
    cellsToBeMoved.filter(isModelElement).forEach(cell => {
      const {position: originalPosition} = this.findNodeInLayout(cell.id.toString())
      cell.position(originalPosition.x + offset.x, originalPosition.y + offset.y)
    })
    this.redrawing = false
  }

  findNodeInLayout(nodeId: NodeId): NodePosition {
    return this.props.layout.find(n => n.id === nodeId)
  }

  render(): JSX.Element {
    const {divId, isSubprocess} = this.props
    return (
      <>
        <GraphPaperContainer
          ref={this.setEspGraphRef}
          onResize={isSubprocess ? () => this.panAndZoom.fitSmallAndLargeGraphs() : null}
          id={divId}
        />
        <ComponentDragPreview scale={this.zoom}/>
      </>
    )
  }

  private bindNodeRemove() {
    this.graph.on(Events.REMOVE, (e: dia.Cell) => {
      if (e.isLink() && !this.redrawing) {
        this.props.nodesDisconnected(e.attributes.source.id, e.attributes.target.id)
      }
    })
  }

  private bindNodesMoving(): void {
    this.graph.on(Events.CHANGE_POSITION, (element: dia.Cell, position: Position) => {
      if (!this.redrawing && this.props.selectionState?.includes(element.id.toString()) && isModelElement(element)) {
        this.moveSelectedNodesRelatively(element, position)
      }
    })
  }
}
