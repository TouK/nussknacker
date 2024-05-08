/* @refresh reset */
import { dia, g } from "jointjs";
import { throttle } from "lodash";
import { GlobalCursor } from "./GlobalCursor";
import { select, Selection } from "d3-selection";
import { D3ZoomEvent, zoom, ZoomBehavior, ZoomedElementBaseType, zoomIdentity, ZoomTransform } from "d3-zoom";
import { rafThrottle } from "./rafThrottle";
import { isVisualTesting } from "../toolbarSettings/DEV_TOOLBARS";

function isModified(event: MouseEvent | TouchEvent) {
    return event.shiftKey || event.ctrlKey || event.altKey || event.metaKey;
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

const FIT_TRANSITION_NAME = "fit";

export class PanZoomPlugin {
    private zoomBehavior: ZoomBehavior<ZoomedElementBaseType, unknown>;
    private paperSelection: Selection<SVGElement, unknown, null, undefined>;
    panBy = ({ x, y }: { x: number; y: number }) => {
        this.paperSelection.call(this.zoomBehavior.translateBy, x, y);
    };
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

    fitContent(content: g.Rect, updatedViewport?: Viewport, predefinedScale?: number | undefined): void {
        this.viewport = updatedViewport;

        const zoomThreshold = 0.78;
        const autoScaleValue = Math.min(
            1,
            zoomThreshold / Math.max(content.width / this.viewport.width, content.height / this.viewport.height),
        );
        const scale = predefinedScale || autoScaleValue;
        const center = content.center();
        const translate = this.getTranslatedCenter(center, this.viewport, scale);

        const zoomTransform = zoomIdentity.translate(translate.x, translate.y).scale(scale);

        this.paperSelection.interrupt(FIT_TRANSITION_NAME);

        (isVisualTesting ? this.paperSelection : this.paperSelection.transition(FIT_TRANSITION_NAME).duration(750)).call(
            this.zoomBehavior.transform,
            zoomTransform,
        );
    }

    zoomIn(): void {
        this.zoomTo(this.zoom * 2);
    }

    zoomOut(): void {
        this.zoomTo(this.zoom * 0.5);
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

    private initMove<E extends ZoomedElementBaseType, D>(e: D3ZoomEvent<E, D>): void {
        if (!e.sourceEvent) return;

        this.globalCursor.enable("move");
        this.paperSelection.interrupt(FIT_TRANSITION_NAME);
    }

    private cleanup() {
        this.globalCursor.disable();
    }
}
