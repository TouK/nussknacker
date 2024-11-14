import Backbone from "backbone";
import { dia, g, shapes, util } from "jointjs";
import { debounce, throttle } from "lodash";
import React, { PropsWithChildren, useEffect, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { rafThrottle } from "../../../components/graph/rafThrottle";
import { useGraph } from "../GraphProvider";
import { usePanZoomBehavior } from "../paper/PanZoomBehavior";
import svg = util.svg;

type NodeProps = PropsWithChildren<{
    label: string;
    x?: number;
    y?: number;
    id: string;
}>;

class Form extends dia.Element {
    markup = svg`
        <rect @selector="box" />
        <text @selector="label" />
        <foreignObject @selector="foreignObject">
            <div xmlns="http://www.w3.org/1999/xhtml" @selector="body"/>
        </foreignObject>
    `;

    defaults() {
        return {
            ...super.defaults,
            type: "example.Form",
            attrs: {
                box: {
                    width: "calc(w)",
                    height: "calc(h)",
                },
                foreignObject: {
                    width: "calc(4*w)",
                    height: "calc(4*h)",
                    style: {
                        scale: 0.25,
                    },
                },
            },
        };
    }
}

class FormView extends dia.ElementView {
    initialize(options?: Backbone.ViewOptions<dia.Element, SVGElement>) {
        super.initialize(options);
    }

    registerView() {
        const setViews = this.model.get("setViews");
        setViews((views) => {
            return views.includes(this) ? views : [...views, this];
        });
    }

    unregisterView() {
        const setViews = this.model.get("setViews");
        setViews((views) => views.filter((v) => v !== this));
    }

    render(): this {
        super.render();
        this.paper.on(
            "transform",
            throttle(() => {
                const bBox = new g.Rect(this.el.getBoundingClientRect());
                const viewport = new g.Rect(this.paper.el.parentElement.getBoundingClientRect());
                const isBigEnough = Math.min(bBox.width, bBox.height) >= 300;
                const inView = viewport.containsPoint(bBox.center());
                if (isBigEnough && inView) {
                    this.registerView();
                } else {
                    this.unregisterView();
                }
            }, 250),
        );
        return this;
    }

    remove(): this {
        this.paper.off("transform");
        this.unregisterView();
        super.remove();
        return this;
    }
}

Object.assign(shapes, {
    example: {
        Form,
        FormView,
    },
});

export function Node({ label, id, x = 0, y = 0, children }: NodeProps) {
    const graph = useGraph();
    const shape = useRef<Form>();
    const [views, setViews] = useState<dia.ElementView[]>([]);
    useEffect(() => {
        shape.current = graph.replaceCell(
            shape.current,
            new Form({
                id,
                size: { width: 200, height: 150 },
                setViews,
            }),
        );
    }, [graph, id]);

    useEffect(() => {
        // shape.current.attr("label/text", label);
        shape.current.position(x, y);
    }, [label, x, y]);

    if (!views.length) return null;

    return <>{views.map((view) => createPortal(children, view["selectors"].body, view.id))}</>;
}
