import { css } from "@emotion/css";
import { dia, g } from "jointjs";
import { debounce, throttle } from "lodash";
import { isTouchDevice, isTouchEvent, LONG_PRESS_TIME } from "../../helpers/detectDevice";
import svgPanZoom from "svg-pan-zoom";
import { CursorMask } from "./CursorMask";
import { Events } from "./types";
import Hammer from "hammerjs";

const getAnimationClass = (disabled?: boolean) =>
    css({
        ".svg-pan-zoom_viewport": {
            transition: disabled ? "none" : `transform .5s cubic-bezier(0.86, 0, 0.07, 1)`,
        },
    });

export class PanZoomPlugin {
    private cursorMask: CursorMask;
    private instance: SvgPanZoom.Instance;
    private animationClassHolder: HTMLElement;
    private panStart: {
        x: number;
        y: number;
        touched?: boolean;
    };

    constructor(private paper: dia.Paper) {
        this.cursorMask = new CursorMask();
        this.instance = svgPanZoom(paper.svg, {
            fit: false,
            contain: false,
            zoomScaleSensitivity: 0.4,
            controlIconsEnabled: false,
            panEnabled: false,
            dblClickZoomEnabled: false,
            minZoom: 0.005,
            maxZoom: 500,
        });

        //appear animation starting point, fitSmallAndLargeGraphs will set animation end point in componentDidMount
        this.instance.zoom(0.001);

        if (isTouchDevice()) {
            this.initPinchZooming(this.paper);
        }

        this.animationClassHolder = paper.el;
        this.animationClassHolder.addEventListener(
            "transitionend",
            debounce(() => {
                this.setAnimationClass({ enabled: false });
            }, 500),
        );

        paper.on(Events.BLANK_POINTERDOWN, this.handleBlankPointerDown);

        this.initPanMove(paper);
    }

    private handleBlankPointerDown = (event: dia.Event) => {
        if (isTouchEvent(event)) {
            const pressTimer = setTimeout(() => this.cleanup(), LONG_PRESS_TIME);
            this.paper.once(Events.BLANK_POINTERUP, () => clearTimeout(pressTimer));
            this.paper.once(Events.BLANK_POINTERMOVE, () => clearTimeout(pressTimer));
        }
    };

    private initMove(event: MouseEvent): void {
        const isModified = event.shiftKey || event.ctrlKey || event.altKey || event.metaKey;
        if (!isModified) {
            this.cursorMask.enable("move");
            this.panStart = {
                x: event.clientX,
                y: event.clientY,
            };
        }
    }

    private setAnimationClass({ enabled }: { enabled: boolean }) {
        if (window["Cypress"]) {
            return;
        }
        this.animationClassHolder.classList.remove(getAnimationClass(enabled));
        this.animationClassHolder.classList.add(getAnimationClass(!enabled));
    }

    get zoom(): number {
        return this.instance.getZoom();
    }

    private cleanup() {
        this.panStart = null;
        this.cursorMask.disable();
    }

    fitSmallAndLargeGraphs = debounce((): void => {
        this.setAnimationClass({ enabled: true });
        this.instance.zoom(1);
        this.instance.resize();
        this.instance.updateBBox();
        this.instance.fit();
        const { realZoom } = this.instance.getSizes();
        const toZoomBy = realZoom > 1.2 ? 1 / realZoom : 0.78; //the bigger zoom, the further we get
        this.instance.zoomBy(toZoomBy);
        this.instance.center();
    }, 100);

    zoomIn = throttle((): void => {
        this.setAnimationClass({ enabled: true });
        this.instance.zoomIn();
    }, 500);

    zoomOut = throttle((): void => {
        this.setAnimationClass({ enabled: true });
        this.instance.zoomOut();
    }, 500);

    private pan = throttle((point: { x: number; y: number }) => {
        requestAnimationFrame(() => {
            this.instance.panBy(point);
        });
    }, 16 /*~60 pleasant fps*/);

    panToCells = (cells: dia.Cell[], viewport: g.Rect = new g.Rect(this.paper.el.getBoundingClientRect())) => {
        const localViewport = this.paper.clientToLocalRect(viewport);
        const cellsBBox = this.paper.model.getCellsBBox(cells).inflate(10, 50);

        if (localViewport.containsRect(cellsBBox)) {
            return;
        }

        const [top, right, bottom, left] = [
            -localViewport.topLine().pointOffset(cellsBBox.topMiddle()),
            -localViewport.rightLine().pointOffset(cellsBBox.rightMiddle()),
            localViewport.bottomLine().pointOffset(cellsBBox.bottomMiddle()),
            localViewport.leftLine().pointOffset(cellsBBox.leftMiddle()),
        ].map((offset) => Math.min(20, Math.max(0, offset) / 20));

        const panOffset = {
            y: Math.round(top - bottom),
            x: Math.round(left - right),
        };

        this.pan(panOffset);
        requestAnimationFrame(() => {
            this.panToCells(cells, viewport);
        });
    };

    private initPinchZooming(paper: dia.Paper) {
        let lastScale = 1;

        const hammer = new Hammer(paper.el);
        hammer.get("pinch").set({ enable: true });

        hammer.on("pinchstart", () => {
            this.paper.setInteractivity(false);
            this.instance.setZoomScaleSensitivity(0.01);
        });

        hammer.on("pinchend", () => {
            this.instance.setZoomScaleSensitivity(0.4);
        });

        hammer.on("pinchin pinchout", (e) => {
            if (e.scale < lastScale) {
                this.instance.zoomIn();
            } else if (e.scale > lastScale) {
                this.instance.zoomOut();
            }

            lastScale = e.scale;
        });
    }

    private initPanMove = (paper: dia.Paper) => {
        const hammer = new Hammer(paper.el);
        hammer.get("pan").set({ threshold: 2 });

        hammer.on("panstart", (event) => {
            const isPaperElementTargeting = event.target.parentElement.id === paper.el.id;
            if (!isPaperElementTargeting) {
                this.cleanup();
                return;
            }

            this.initMove(event.pointers[0]);
        });

        hammer.on("panmove", (event) => {
            const isModified = event.srcEvent.shiftKey || event.srcEvent.ctrlKey || event.srcEvent.altKey || event.srcEvent.metaKey;

            if (!isModified && this.panStart) {
                const panStart = this.panStart;
                this.instance.panBy({
                    x: event.pointers[0].clientX - panStart.x,
                    y: event.pointers[0].clientY - panStart.y,
                });

                this.panStart = {
                    x: event.pointers[0].clientX,
                    y: event.pointers[0].clientY,
                    touched: true,
                };
            }
        });

        hammer.on("panend", (event) => {
            event.srcEvent.stopPropagation();

            if (this.panStart?.touched) {
                event.pointers[0].stopImmediatePropagation();
            }
            this.cleanup();
        });
    };
}
