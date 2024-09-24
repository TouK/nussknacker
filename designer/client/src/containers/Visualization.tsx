import { useWindowManager } from "@touk/window-manager";
import { isEmpty } from "lodash";
import { HTML5toTouch } from "rdndmb-html5-to-touch";
import React, { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { DndProvider } from "react-dnd-multi-backend";
import { useErrorBoundary } from "react-error-boundary";
import { useDispatch, useSelector } from "react-redux";
import { useSearchParams } from "react-router-dom";
import { clearProcess, fetchAndDisplayProcessCounts, loadProcessState, toggleSelection } from "../actions/nk";
import { fetchVisualizationData } from "../actions/nk/fetchVisualizationData";
import ProcessUtils from "../common/ProcessUtils";
import { useDecodedParams } from "../common/routerUtils";
import * as VisualizationUrl from "../common/VisualizationUrl";
import { Graph } from "../components/graph/Graph";
import { GraphProvider } from "../components/graph/GraphContext";
import { ProcessGraph as GraphEl } from "../components/graph/ProcessGraph";
import SelectionContextProvider from "../components/graph/SelectionContextProvider";
import { Scenario } from "../components/Process/types";
import { useRouteLeavingGuard } from "../components/RouteLeavingGuard";
import SpinnerWrapper from "../components/spinner/SpinnerWrapper";
import Toolbars from "../components/toolbars/Toolbars";
import { RootState } from "../reducers";
import { getGraph, getScenario, getScenarioGraph } from "../reducers/selectors/graph";
import { getCapabilities } from "../reducers/selectors/other";
import { getProcessDefinitionData } from "../reducers/selectors/settings";
import { useWindows } from "../windowManager";
import { BindKeyboardShortcuts } from "./BindKeyboardShortcuts";
import { useModalDetailsIfNeeded } from "./hooks/useModalDetailsIfNeeded";
import { useInterval } from "./Interval";
import { GraphPage } from "./Page";
import { ScenarioDescription } from "./ScenarioDescription";

function useUnmountCleanup() {
    const { close } = useWindows();
    const dispatch = useDispatch();
    const closeRef = useRef(close);
    closeRef.current = close;

    const cleanup = useCallback(async () => {
        await closeRef.current();
        dispatch(clearProcess());
    }, [dispatch]);

    useEffect(() => {
        return () => {
            cleanup();
        };
    }, [cleanup]);
}

function useProcessState(refreshTime = 10000) {
    const dispatch = useDispatch();
    const scenario = useSelector(getScenario);
    const { isFragment, isArchived, name } = scenario || {};

    const fetch = useCallback(() => dispatch(loadProcessState(name)), [dispatch, name]);
    const disabled = !name || isFragment || isArchived;

    useInterval(fetch, {
        refreshTime,
        disabled,
    });
}

function useCountsIfNeeded() {
    const dispatch = useDispatch();
    const scenario = useSelector(getScenario);
    const scenarioGraph = useSelector(getScenarioGraph);

    const [searchParams] = useSearchParams();
    const from = searchParams.get("from");
    const to = searchParams.get("to");
    const refresh = searchParams.get("refresh");
    useEffect(() => {
        if (!scenario || scenario.isFragment) return;

        const countParams = VisualizationUrl.extractCountParams({ from, to, refresh });
        if (!countParams) return;

        dispatch(
            fetchAndDisplayProcessCounts({
                processName: scenario.name,
                scenarioGraph,
                ...countParams,
            }),
        );
    }, [dispatch, from, refresh, scenario, scenarioGraph, to]);
}

function Visualization() {
    const { processName } = useDecodedParams<{
        processName: string;
    }>();
    const dispatch = useDispatch();
    const { showBoundary } = useErrorBoundary();

    const graphRef = useRef<Graph>();
    const getGraphInstance = useCallback(() => graphRef.current, [graphRef]);

    const [dataResolved, setDataResolved] = useState(false);

    const fetchData = useCallback(
        async (processName: string) => {
            dispatch(
                fetchVisualizationData(
                    processName,
                    () => {
                        setDataResolved(true);
                    },
                    (error) => {
                        showBoundary(error);
                    },
                ),
            );
        },
        [dispatch, showBoundary],
    );

    const { scenarioLoading } = useSelector(getGraph);
    const scenario = useSelector(getScenario);
    const graphNotReady = useMemo(() => !dataResolved || isEmpty(scenario) || scenarioLoading, [dataResolved, scenario, scenarioLoading]);

    const processDefinitionData = useSelector(getProcessDefinitionData);
    const capabilities = useSelector(getCapabilities);
    const nothingToSave = useSelector((state) => ProcessUtils.nothingToSave(state as RootState));

    const getPastePosition = useCallback(() => {
        const paper = getGraphInstance()?.processGraphPaper;
        const { x, y } = paper?.getArea()?.center() || {
            x: 300,
            y: 100,
        };
        return {
            x: Math.floor(x),
            y: Math.floor(y),
        };
    }, [getGraphInstance]);

    useEffect(() => {
        fetchData(processName);
    }, [fetchData, processName]);

    useProcessState();
    useCountsIfNeeded();

    const { openNodes } = useModalDetailsIfNeeded();
    const openAndHighlightNodes = useCallback(
        async (scenario: Scenario) => {
            const windows = await Promise.all(openNodes(scenario));
            windows.map((w) => dispatch(toggleSelection(w.meta.node.id)));
        },
        [dispatch, openNodes],
    );

    useEffect(() => {
        if (graphNotReady) return;
        openAndHighlightNodes(scenario);
    }, [scenario, graphNotReady, openAndHighlightNodes]);

    useUnmountCleanup();
    useRouteLeavingGuard(capabilities.editFrontend && !nothingToSave);

    const { windows } = useWindowManager();

    return (
        <DndProvider options={HTML5toTouch}>
            <GraphPage data-testid="graphPage">
                <SpinnerWrapper isReady={!graphNotReady}>
                    {isEmpty(processDefinitionData) ? null : <GraphEl ref={graphRef} capabilities={capabilities} />}
                </SpinnerWrapper>

                <GraphProvider graph={getGraphInstance}>
                    <SelectionContextProvider pastePosition={getPastePosition}>
                        <BindKeyboardShortcuts disabled={windows.length > 0} />
                        <Toolbars isReady={dataResolved}>
                            <ScenarioDescription />
                        </Toolbars>
                    </SelectionContextProvider>
                </GraphProvider>
            </GraphPage>
        </DndProvider>
    );
}

export default Visualization;
