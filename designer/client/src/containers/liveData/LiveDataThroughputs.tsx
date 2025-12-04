import { alpha, GlobalStyles, useTheme } from "@mui/material";
import React, { useEffect, useMemo, useRef } from "react";

import { useGraph } from "../../components/graph/GraphContext";
import type { NodeTransitionResult } from "../../http/resultsWithCountsDto";
import {
    getIsLiveDataWorking,
    getLiveDataLastUpdate,
    getLiveDataNextUpdate,
    getNodeTransitionResults,
} from "../../reducers/selectors/getLiveData";
import { getUserSettings } from "../../reducers/selectors/userSettings";
import { useAppSelector } from "../../store/storeHelpers";

const isSafari = /^((?!chrome|android).)*safari/i.test(navigator.userAgent);

const CLASS_NAME = "live-data";
const DASH_KEYFRAMES: Keyframe[] = [{ strokeDashoffset: 0 }, { strokeDashoffset: 20 }];

export function LiveDataThroughputs() {
    const theme = useTheme();
    const PULSE_KEYFRAMES: Keyframe[] = useMemo(
        () =>
            isSafari
                ? [{ offset: 0 }, { offset: 0.025, fill: alpha(theme.palette.primary.main, 0.2) }, { offset: 1 }]
                : [
                      { offset: 0, filter: "brightness(1)" },
                      { offset: 0.025, filter: "brightness(1.5) hue-rotate(20deg)" },
                      { offset: 1, filter: "brightness(1)" },
                  ],
        [theme.palette.primary.main],
    );

    const PULSE2_KEYFRAMES: Keyframe[] = useMemo(
        () =>
            isSafari
                ? [
                      { fill: alpha(theme.palette.primary.main, 0.2) },
                      { fill: alpha(theme.palette.primary.main, 0.5) },
                      { fill: alpha(theme.palette.primary.main, 0.2) },
                  ]
                : [
                      { filter: "brightness(1.5) hue-rotate(20deg)" },
                      { filter: "brightness(2.0) hue-rotate(30deg)" },
                      { filter: "brightness(1.5) hue-rotate(20deg)" },
                  ],
        [theme.palette.primary.main],
    );

    const graphGetter = useGraph();

    const settings = useAppSelector(getUserSettings);
    const showAnimations = settings["scenario.showLiveDataAnimations"];
    const isWorking = useAppSelector(getIsLiveDataWorking);
    const enabled = showAnimations && isWorking;

    useEffect(() => {
        graphGetter()?.processGraphPaper.el.classList.toggle(CLASS_NAME, enabled);
    }, [graphGetter, enabled]);

    const transitionResults = useAppSelector(getNodeTransitionResults);
    const nextIn = useAppSelector(getLiveDataNextUpdate);
    const last = useAppSelector(getLiveDataLastUpdate);

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
            transitionResults.reduce((max, link) => {
                return Math.max(max, link.currentThroughput, 0.1);
            }, 0),
        [transitionResults],
    );

    const lastSeen = useRef(new Date().getTime());
    const newEvents = useMemo(() => {
        const newEvents = flatEvents.filter(({ timestamp }) => timestamp > lastSeen.current);
        lastSeen.current = newEvents[0]?.timestamp || lastSeen.current;
        return newEvents;
    }, [flatEvents]);

    useEffect(() => {
        const graphInstance = graphGetter();

        graphInstance?.graph.getElements().forEach((model) => {
            const isMatchingModel = ({
                sourceNodeId,
                destinationNodeId,
            }: Pick<NodeTransitionResult, "sourceNodeId" | "destinationNodeId">): boolean => {
                if (model.hasPort("In")) return model.id === destinationNodeId;
                if (model.hasPort("Out")) return model.id === sourceNodeId;
            };

            const events = newEvents.filter(isMatchingModel);
            let el = graphInstance.processGraphPaper.findViewByModel(model)?.el;
            if (isSafari) {
                // <g> not allowed for animation/filter
                el = el.getElementsByTagName("rect")[0];
                if (!el) return;
            }

            const nodeInputThroughput = enabled
                ? transitionResults.filter(isMatchingModel).reduce((sum, { currentThroughput }) => sum + currentThroughput, 0)
                : 0;

            const animations = el.getAnimations().filter(({ id }) => id === "pulse2" || id === "pulse");

            const animation = animations.find((a) => a.id === "pulse2");
            if (nodeInputThroughput && nodeInputThroughput >= 4) {
                if (!nextIn) return animation?.cancel();
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
            if (enabled) {
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
            } else {
                animations.forEach((a) => a.cancel());
            }
        });
    }, [enabled, graphGetter, nextIn, newEvents, transitionResults, PULSE2_KEYFRAMES, PULSE_KEYFRAMES]);

    useEffect(() => {
        const graphInstance = graphGetter();
        const recentEvents = flatEvents.filter(({ timestamp }) => timestamp > last - nextIn);

        graphInstance?.graph.getLinks()?.forEach((model) => {
            const [el] = graphInstance.processGraphPaper.findViewByModel(model).findBySelector(".connection");

            const transitionThroughput = transitionResults.find(
                ({ sourceNodeId, destinationNodeId }) =>
                    sourceNodeId === model.attributes.edgeData?.from && destinationNodeId === model.attributes.edgeData?.to,
            );

            const recentEvent = recentEvents.find(
                ({ sourceNodeId, destinationNodeId }) =>
                    sourceNodeId === transitionThroughput?.sourceNodeId && destinationNodeId === transitionThroughput?.destinationNodeId,
            );

            const normalizedThroughput = transitionThroughput && recentEvent ? transitionThroughput.currentThroughput / maxThroughput : 0;
            el.classList.toggle(CLASS_NAME, normalizedThroughput > 0);

            const animation = el.getAnimations().find((a) => a.id === "dash");
            if (!enabled) return animation?.cancel();
            if (!normalizedThroughput) return;
            if (animation) return animation.updatePlaybackRate(normalizedThroughput);
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
    }, [flatEvents, graphGetter, last, nextIn, maxThroughput, transitionResults, enabled]);

    return (
        <GlobalStyles
            styles={(theme) => ({
                ".joint-cell.joint-link": {
                    "&& > .connection": {
                        transition: "1s linear",
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
