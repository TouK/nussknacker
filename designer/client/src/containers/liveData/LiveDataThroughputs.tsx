import { GlobalStyles } from "@mui/material";
import React, { useCallback, useEffect, useLayoutEffect, useMemo, useRef, useState } from "react";

import { useGraph } from "../../components/graph/GraphContext";
import { watchForCover } from "../../components/graph/watchForCover";
import { getIsLiveDataWorking, getNodeTransitionResults } from "../../reducers/selectors/getLiveData";
import { getUserSettings } from "../../reducers/selectors/userSettings";
import { useAppSelector } from "../../store/storeHelpers";
import type { DataEvent } from "./animationHelpers";
import { updateAnimation } from "./animationHelpers";
import { heatmapColor } from "./heatmapColor";
import { getElementMatcher, getLinkMatcher, getTransitionMatcher } from "./transitionsHelpers";

const CLASS_NAME = "live-data";

export function LiveDataThroughputs() {
    const graphGetter = useGraph();

    const settings = useAppSelector(getUserSettings);
    const showTransitionAnimations = settings["scenario.liveData.showTransitionAnimations"];
    const showNodeAnimations = settings["scenario.liveData.showNodeAnimations"];
    const [covered, setCovered] = useState(false);
    const _enabled = useAppSelector((state) => (showNodeAnimations || showTransitionAnimations) && getIsLiveDataWorking(state));

    useEffect(() => {
        if (!_enabled) return;
        const unsubscrbe = watchForCover(() => graphGetter().processGraphPaper.el, setCovered);
        return () => {
            unsubscrbe();
            setCovered(false);
        };
    }, [_enabled, graphGetter]);

    const enabled = !covered && _enabled;

    useEffect(() => {
        const classList = graphGetter()?.processGraphPaper.el.classList;
        if (!classList) return;
        classList.toggle(CLASS_NAME, enabled);
        classList.toggle(`${CLASS_NAME}-node`, enabled && showNodeAnimations);
        classList.toggle(`${CLASS_NAME}-transition`, enabled && showTransitionAnimations);
    }, [showTransitionAnimations, showNodeAnimations, enabled, graphGetter]);

    const transitionResults = useAppSelector((state) => (enabled ? getNodeTransitionResults(state) : []));

    const flatEvents: DataEvent[] = useMemo(() => {
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
    }, [transitionResults]);

    const maxThroughput = useMemo(
        () =>
            transitionResults.reduce((max, link) => {
                return Math.max(max, link.currentThroughput, 0.1);
            }, 0),
        [transitionResults],
    );

    const lastSeen = useRef(new Date().getTime());
    const newEvents = useMemo<DataEvent[]>(() => {
        const newEvents = flatEvents.filter(({ timestamp }) => timestamp > lastSeen.current);
        lastSeen.current = newEvents[0]?.timestamp || lastSeen.current;
        return newEvents;
    }, [flatEvents]);

    const animate = useCallback(
        (name: string, el: HTMLElement | SVGElement, throughput?: number, events?: DataEvent[]) => {
            let animationEnabled = true;
            switch (name) {
                case "node":
                    animationEnabled = showNodeAnimations;
                    break;
                case "transition":
                    animationEnabled = showTransitionAnimations;
                    break;
            }
            updateAnimation(enabled && animationEnabled ? { name, el, throughput, events } : { name, el });
        },
        [showTransitionAnimations, showNodeAnimations, enabled],
    );

    // Nodes animation
    useLayoutEffect(() => {
        const graphInstance = graphGetter();
        graphInstance?.graph.getElements().forEach((model) => {
            const [el] = graphInstance.processGraphPaper.findViewByModel(model).findBySelector(".body");
            if (!el) return;

            const isMatchingModel = getElementMatcher(model);

            const events = newEvents.filter(isMatchingModel);
            const throughput = transitionResults.filter(isMatchingModel).reduce((sum, { currentThroughput }) => sum + currentThroughput, 0);

            animate("node", el, throughput, events);
        });
    }, [animate, graphGetter, newEvents, transitionResults]);

    // Links animation
    useLayoutEffect(() => {
        const graphInstance = graphGetter();
        graphInstance?.graph.getLinks()?.forEach((model) => {
            const [el] = graphInstance.processGraphPaper.findViewByModel(model).findBySelector(".connection");
            if (!el) return;

            const transition = transitionResults.find(getLinkMatcher(model));
            const events = newEvents.filter(getTransitionMatcher(transition));
            const throughput = transition?.currentThroughput;

            const enabled = showTransitionAnimations;
            el.classList.toggle(`${CLASS_NAME}-active`, enabled && throughput > 0);
            el.style.stroke = enabled
                ? heatmapColor((throughput || 0) / maxThroughput, [
                      { v: 0, c: "#333333" },
                      { v: 0.8, c: "#ffffff" },
                  ])
                : null;

            animate("transition", el, throughput, events);
        });
    }, [animate, showTransitionAnimations, graphGetter, maxThroughput, newEvents, transitionResults]);

    return (
        <GlobalStyles
            styles={{
                ".joint-cell.joint-link": {
                    "&& > .connection": {
                        transition: "1s linear",
                        transitionProperty: "filter, stroke, stroke-width, stroke-dasharray",
                        [`.${CLASS_NAME}-transition &`]: {
                            strokeDasharray: "5 20",
                            strokeWidth: 3,
                            [`&.${CLASS_NAME}-active`]: {
                                strokeDasharray: "20 10",
                            },
                        },
                    },
                },
            }}
        />
    );
}
