/* @refresh reset */
import { dia, g } from "jointjs";
import { throttle } from "lodash";
import { GlobalCursor } from "./GlobalCursor";
import { select, Selection } from "d3-selection";
import { D3ZoomEvent, zoom, ZoomBehavior, ZoomedElementBaseType, zoomIdentity, ZoomTransform } from "d3-zoom";
import { rafThrottle } from "./rafThrottle";

function isModified(event: MouseEvent | TouchEvent) {
    return event.shiftKey || event.ctrlKey || event.altKey || event.metaKey;
}

function clamp(number: number, max: number) {
    return Math.round(Math.min(max, Math.max(-max, number)));
}

function adjust(number: number, multi = 1, pow = 1) {
    return Math.round((number >= 0 ? Math.pow(number, pow) : -Math.pow(-number, pow)) * multi);
}

function transformToCSSMatrix({ x, y, k }: ZoomTransform): string {
    const matrix = [
        [k.toFixed(4), 0, x.toFixed(4)],
        [0, k.toFixed(4), y.toFixed(4)],
        [0, 0, 1],
    ];
    return `matrix(${matrix[0][0]}, ${matrix[1][0]}, ${matrix[0][1]}, ${matrix[1][1]}, ${matrix[0][2]}, ${matrix[1][2]})`;
}

export type Viewport = g.Rect;

export class PanZoomPlugin {
    private zoomBehavior: ZoomBehavior<ZoomedElementBaseType, unknown>;
    private paperSelection: Selection<SVGElement, unknown, null, undefined>;
    private panBy = rafThrottle(({ x, y }: { x: number; y: number }) => {
        this.paperSelection.interrupt("pan").transition("pan").duration(25).call(this.zoomBehavior.translateBy, x, y);
    });
    private globalCursor: GlobalCursor;
    private zoomTo = throttle((scale: number): void => {
        this.paperSelection.transition().duration(750).call(this.zoomBehavior.scaleTo, scale);
    }, 250);
    private _enabled = true;

    constructor(private paper: dia.Paper, viewport: Viewport) {
        this.viewport = viewport;
        this.globalCursor = new GlobalCursor();
        this.paperSelection = select(this.paper.svg);

        this.zoomBehavior = zoom()
            .scaleExtent([0.05, 6])
            .filter(this.filterEvents.bind(this))
            .on("start", this.initMove.bind(this))
            .on("zoom", rafThrottle(this.applyTransform.bind(this)))
            .on("end", this.cleanup.bind(this));

        const initialScale = 0.01;
        const center = this.paper.getContentArea().center();
        const initialTranslate = this.getTranslatedCenter(center, this.viewport, initialScale);
        const zoomTransform = zoomIdentity.translate(initialTranslate.x, initialTranslate.y).scale(initialScale);

        this.paperSelection.call(this.zoomBehavior.transform, zoomTransform);
        this.enable();
    }

    private _zoom = 1;

    get zoom(): number {
        return this._zoom;
    }

    private _viewport: Viewport;

    private get viewport(): Viewport {
        return this._viewport || new g.Rect(this.paper.el.getBoundingClientRect());
    }

    private set viewport(value: Viewport) {
        this._viewport = value || this._viewport;
    }

    private get layers() {
        return select(this.paper.viewport.parentElement);
    }

    toggle(enabled: boolean) {
        if (enabled === this._enabled) return;

        enabled ? this.enable() : this.disable();
        this._enabled = enabled;
    }

    fitContent(content: g.Rect, updatedViewport?: Viewport): void {
        this.viewport = updatedViewport;

        const transitionName = "fit";
        const scale = Math.min(2, 0.8 / Math.max(content.width / this.viewport.width, content.height / this.viewport.height));
        const center = content.center();
        const translate = this.getTranslatedCenter(center, this.viewport, scale);

        const zoomTransform = zoomIdentity.translate(translate.x, translate.y).scale(scale);

        this.paperSelection
            .interrupt(transitionName)
            .transition(transitionName)
            .duration(750)
            .call(this.zoomBehavior.transform, zoomTransform);
    }

    zoomIn(): void {
        this.zoomTo(this.zoom * 2);
    }

    zoomOut(): void {
        this.zoomTo(this.zoom * 0.5);
    }

    panToOverflow(cells: dia.Cell[], updatedViewport?: Viewport) {
        this.viewport = updatedViewport;

        const border = Math.min(100, adjust(this.viewport.width, 0.05), adjust(this.viewport.height, 0.05));
        const adjustedViewport = this.viewport.inflate(-border, -border).round();

        const cellsBBox = this.paper.localToClientRect(this.paper.model.getCellsBBox(cells)).round();

        if (adjustedViewport.containsRect(cellsBBox)) {
            return;
        }

        const top = Math.min(0, adjustedViewport.topLine().pointOffset(cellsBBox.topMiddle()));
        const bottom = Math.max(0, adjustedViewport.bottomLine().pointOffset(cellsBBox.bottomMiddle()));
        const left = Math.min(0, -adjustedViewport.leftLine().pointOffset(cellsBBox.leftMiddle()));
        const right = Math.max(0, -adjustedViewport.rightLine().pointOffset(cellsBBox.rightMiddle()));

        const x = -Math.round(left + right);
        const y = -Math.round(top + bottom);
        this.panBy({
            x: clamp(adjust(x, 0.2, 1.5), 60),
            y: clamp(adjust(y, 0.2, 1.5), 60),
        });
    }

    private getTranslatedCenter = (center: g.Point, viewport: Viewport, scale: number) => {
        const viewportRelativeCenter = viewport.translate(0, -viewport.y).center();
        return viewportRelativeCenter.translate(center.scale(-scale, -scale));
    };

    private enable() {
        this.paperSelection.call(this.zoomBehavior).on("dblclick.zoom", null);
    }

    private disable() {
        this.paperSelection.on(".zoom", null);
    }

    private filterEvents(event: MouseEvent | TouchEvent): boolean {
        if (!this._enabled) {
            return false;
        }

        if (isModified(event)) {
            return false;
        }

        if ("button" in event && event.button === 1) {
            return false;
        }

        if (event.type === "wheel") {
            return true;
        }

        if ("touches" in event && event.touches.length > 1) {
            return true;
        }

        return event.target === this.paper.svg;
    }

    private applyTransform<E extends ZoomedElementBaseType, D>(e: D3ZoomEvent<E, D>) {
        if (this._enabled) {
            this._zoom = e.transform.k;
            this.layers.attr("transform", transformToCSSMatrix(e.transform)).style("transform", transformToCSSMatrix(e.transform));
        }
    }

    private initMove(): void {
        this.globalCursor.enable("move");
    }

    private cleanup() {
        this.globalCursor.disable();
    }
}
