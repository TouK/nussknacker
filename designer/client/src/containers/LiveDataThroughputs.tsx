import { GlobalStyles } from "@mui/material";
import { uniqBy } from "lodash";
import React, { useEffect } from "react";
import { useSelector } from "react-redux";

import { useGraph } from "../components/graph/GraphContext";
import { getNodeTransitionResults, getNodeTransitionThroughput } from "../reducers/selectors/getLiveData";

const CLASS_NAME = "live-data";

export function LiveDataThroughputs() {
    const liveData = useSelector(getNodeTransitionThroughput);
    const graphGetter = useGraph();

    useEffect(() => {
        const graphInstance = graphGetter();
        if (!graphInstance) return;

        const linksModels = graphInstance.graph.getLinks() || [];

        const maxThroughput = liveData.reduce((max, link) => {
            return Math.max(max, link.throughput);
        }, 0);

        const links = linksModels.map((model) => {
            const {
                attributes: { edgeData },
            } = model;
            const live = liveData.find(
                ({ sourceNodeId, destinationNodeId }) => sourceNodeId === edgeData.from && destinationNodeId === edgeData.to,
            );
            const [el] = graphInstance.processGraphPaper.findViewByModel(model).findBySelector(".connection");
            return {
                el,
                throughput: live ? live.throughput / maxThroughput : 0,
            };
        });

        links.forEach(({ el, throughput }) => {
            el.classList.toggle(CLASS_NAME, throughput > 0);
            el.style.animationDuration = `${(1 / (5 * throughput)).toFixed(6)}s`;
        });
    }, [graphGetter, liveData]);

    return (
        <GlobalStyles
            styles={(theme) => ({
                "@keyframes dash-animation": {
                    to: {
                        strokeDashoffset: "-20",
                    },
                },

                ".joint-theme-default.joint-link": {
                    "&& .connection": {
                        [`&.${CLASS_NAME}`]: {
                            stroke: theme.palette.text.primary,
                            strokeWidth: 2,
                            strokeDasharray: "5 5",
                            strokeDashoffset: 0,
                            animationTimingFunction: "linear",
                            animationName: "dash-animation",
                            animationIterationCount: "infinite",
                        },
                    },
                },
            })}
        />
    );
}

export function LiveDataNodePulse() {
    const liveData = useSelector(getNodeTransitionResults);
    const graphGetter = useGraph();

    useEffect(() => {
        const graphInstance = graphGetter();
        if (!graphInstance) return;

        const now = new Date().getTime();
        const nodes = liveData
            .filter((v) => v.sourceNodeId)
            .flatMap(({ sourceNodeId, results }) => {
                return results
                    .map((r) => {
                        return {
                            timestamp: new Date(r.timestamp).getTime(),
                            sourceNodeId,
                        };
                    })
                    .sort((b, a) => a.timestamp - b.timestamp)
                    .slice(0, 1);
            })
            .sort((b, a) => a.timestamp - b.timestamp)
            .map(({ timestamp, sourceNodeId }) => {
                const model = graphInstance.graph.getCell(sourceNodeId);
                const el = graphInstance.processGraphPaper.findViewByModel(model)?.findBySelector(".background")[0];
                return {
                    delay: Math.abs(now - timestamp),
                    el,
                };
            });
        uniqBy(nodes, "el")
            .filter((n) => n.el)
            .forEach(({ el, delay }) => {
                const number = Math.min(1, Math.max(0, 1 - delay / 2000));
                const opacity = number.toFixed(3);
                el.style.transitionDuration = `${(1 - number) * 1000}ms`;
                el.style.opacity = opacity;
            });
    }, [graphGetter, liveData]);

    return null;
}
