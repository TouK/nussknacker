import { GlobalStyles } from "@mui/material";
import React, { useEffect, useMemo, useRef } from "react";
import { useSelector } from "react-redux";

import { useGraph } from "../../components/graph/GraphContext";
import { getLiveDataRefresh, getNodeTransitionResults, getNodeTransitionThroughput } from "../../reducers/selectors/getLiveData";
import { CLASS_NAME } from "./useLiveDataRefreshEnabled";

const PULSE_KEYFRAMES: Keyframe[] = [
    { offset: 0, filter: "brightness(1)" },
    { offset: 0.025, filter: "brightness(1.5) hue-rotate(20deg)" },
    { offset: 1, filter: "brightness(1)" },
];
const PULSE2_KEYFRAMES: Keyframe[] = [
    { filter: "brightness(1.5) hue-rotate(20deg)" },
    { filter: "brightness(2.0) hue-rotate(30deg)" },
    { filter: "brightness(1.5) hue-rotate(20deg)" },
];
const DASH_KEYFRAMES: Keyframe[] = [{ strokeDashoffset: 0 }, { strokeDashoffset: 20 }];

export function LiveDataThroughputs() {
    const throughputData = useSelector(getNodeTransitionThroughput);
    const transitionResults = useSelector(getNodeTransitionResults);
    const liveDataRefresh = useSelector(getLiveDataRefresh);
    const graphGetter = useGraph();
    const lastSeen = useRef(new Date().getTime());

    const enabled = useMemo(() => Boolean(liveDataRefresh?.nextIn), [liveDataRefresh?.nextIn]);

    useEffect(() => {
        graphGetter()?.processGraphPaper.el.classList.toggle(CLASS_NAME, enabled);
    }, [graphGetter, enabled]);

    const flatEvents = useMemo(() => {
        if (!enabled) return [];
        return transitionResults
            .filter(({ sourceNodeId }) => sourceNodeId)
            .flatMap(({ sourceNodeId, destinationNodeId, results }) =>
                results.map(({ id, timestamp }) => ({
                    id,
                    timestamp: new Date(timestamp).getTime(),
                    sourceNodeId,
                    destinationNodeId,
                })),
            )
            .sort((b, a) => a.timestamp - b.timestamp);
    }, [enabled, transitionResults]);

    const maxThroughput = useMemo(
        () =>
            throughputData.reduce((max, link) => {
                return Math.max(max, link.throughput, 0.1);
            }, 0),
        [throughputData],
    );

    const newEvents = useMemo(() => {
        const newEvents = flatEvents.filter(({ timestamp }) => timestamp > lastSeen.current);
        lastSeen.current = newEvents[0]?.timestamp || lastSeen.current;
        return newEvents;
    }, [flatEvents]);

    useEffect(() => {
        const graphInstance = graphGetter();

        graphInstance?.graph.getElements().forEach((model) => {
            const events = newEvents.filter((e) => e.sourceNodeId === model.id);
            const el = graphInstance.processGraphPaper.findViewByModel(model)?.el;

            const nodeInputThroughput = enabled
                ? throughputData
                      .filter(({ sourceNodeId, destinationNodeId }) => {
                          if (model.hasPort("In")) return destinationNodeId === model.id;
                          if (model.hasPort("Out")) return sourceNodeId === model.id;
                      })
                      .reduce((sum, { throughput }) => sum + throughput, 0)
                : 0;

            const animation = el.getAnimations().find((a) => a.id === "pulse2");
            if (nodeInputThroughput && nodeInputThroughput >= 4) {
                if (!liveDataRefresh?.nextIn) return animation?.cancel();
                if (animation) return animation.updatePlaybackRate(nodeInputThroughput);
                el.animate(PULSE2_KEYFRAMES, {
                    id: "pulse2",
                    iterations: Infinity,
                    duration: 50000,
                    easing: "ease-in-out",
                    composite: "accumulate",
                    fill: "forwards",
                });
                return;
            }
            animation?.cancel();
            events.forEach(({ timestamp }) => {
                const delay = timestamp - newEvents[newEvents.length - 1].timestamp;
                el?.animate(PULSE_KEYFRAMES, {
                    id: "pulse",
                    duration: 1000,
                    delay,
                    easing: "ease-in-out",
                    fill: "none",
                    composite: "accumulate",
                    playbackRate: nodeInputThroughput,
                });
            });
        });
    }, [enabled, graphGetter, liveDataRefresh?.nextIn, newEvents, throughputData]);

    useEffect(() => {
        const graphInstance = graphGetter();
        const recentEvents = flatEvents.filter(({ timestamp }) => timestamp > liveDataRefresh?.last - 2000);

        graphInstance?.graph.getLinks()?.forEach((model) => {
            const [el] = graphInstance.processGraphPaper.findViewByModel(model).findBySelector(".connection");

            const transitionThroughput = throughputData.find(
                ({ sourceNodeId, destinationNodeId }) =>
                    sourceNodeId === model.attributes.edgeData.from && destinationNodeId === model.attributes.edgeData.to,
            );

            const recentEvent = recentEvents.find(
                ({ sourceNodeId, destinationNodeId }) =>
                    sourceNodeId === transitionThroughput?.sourceNodeId && destinationNodeId === transitionThroughput?.destinationNodeId,
            );

            const normalizedThroughput = transitionThroughput && recentEvent ? transitionThroughput.throughput / maxThroughput : 0;
            el.classList.toggle(CLASS_NAME, normalizedThroughput > 0);

            const animation = el.getAnimations().find((a) => a.id === "dash");
            if (!liveDataRefresh?.nextIn) return animation?.cancel();
            if (animation) return animation.updatePlaybackRate(normalizedThroughput);
            if (!normalizedThroughput) return;
            el.animate(DASH_KEYFRAMES, {
                id: "dash",
                iterations: Infinity,
                duration: 300,
                playbackRate: normalizedThroughput,
                easing: "linear",
                direction: "reverse",
                composite: "replace",
                fill: "forwards",
            });
        });
    }, [flatEvents, graphGetter, liveDataRefresh?.last, liveDataRefresh?.nextIn, maxThroughput, throughputData]);

    return (
        <GlobalStyles
            styles={(theme) => ({
                ".joint-cell.joint-link": {
                    "&& > .connection": {
                        transition: "1s ease-in-out",
                        transitionProperty: "filter, stroke, stroke-width",
                        [`.${CLASS_NAME} &`]: {
                            stroke: theme.palette.text.primary,
                            strokeDasharray: "5 5",
                            filter: "brightness(0.5)",
                            [`&.${CLASS_NAME}`]: {
                                strokeWidth: 2,
                                filter: "brightness(1)",
                            },
                        },
                    },
                },
            })}
        />
    );
}
