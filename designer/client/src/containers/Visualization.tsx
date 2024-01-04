import { useDispatch, useSelector } from "react-redux";
import { getFetchedProcessDetails, getGraph, getProcessToDisplay } from "../reducers/selectors/graph";
import { isEmpty } from "lodash";
import { getProcessDefinitionData } from "../reducers/selectors/settings";
import { getCapabilities } from "../reducers/selectors/other";
import { GraphPage } from "./Page";
import { useRouteLeavingGuard } from "../components/RouteLeavingGuard";
import { GraphProvider } from "../components/graph/GraphContext";
import SelectionContextProvider from "../components/graph/SelectionContextProvider";
import { BindKeyboardShortcuts } from "./BindKeyboardShortcuts";
import Toolbars from "../components/toolbars/Toolbars";
import SpinnerWrapper from "../components/spinner/SpinnerWrapper";
import { ProcessGraph as GraphEl } from "../components/graph/ProcessGraph";
import React, { useCallback, useEffect, useMemo, useRef, useState } from "react";
import ProcessUtils from "../common/ProcessUtils";
import { useWindows } from "../windowManager";
import { useSearchParams } from "react-router-dom";
import * as VisualizationUrl from "../common/VisualizationUrl";
import { Graph } from "../components/graph/Graph";
import { ErrorHandler } from "./ErrorHandler";
import { fetchVisualizationData } from "../actions/nk/fetchVisualizationData";
import { clearProcess, fetchAndDisplayProcessCounts, loadProcessState } from "../actions/nk";
import { HTML5toTouch } from "rdndmb-html5-to-touch";
import { DndProvider } from "react-dnd-multi-backend";
import { useDecodedParams } from "../common/routerUtils";
import { RootState } from "../reducers";
import { useModalDetailsIfNeeded } from "./hooks/useModalDetailsIfNeeded";
import { ProcessType } from "../components/Process/types";

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

function useProcessState(time = 10000) {
    const dispatch = useDispatch();
    const fetchedProcessDetails = useSelector(getFetchedProcessDetails);
    const { isFragment, isArchived, name } = fetchedProcessDetails || {};

    const fetch = useCallback(() => dispatch(loadProcessState(name)), [dispatch, name]);

    useEffect(() => {
        let processStateIntervalId;
        if (name && !isFragment && !isArchived) {
            processStateIntervalId = setInterval(fetch, time);
        }
        return () => {
            clearInterval(processStateIntervalId);
        };
    }, [fetch, name, isArchived, isFragment, time]);
}

function useCountsIfNeeded() {
    const dispatch = useDispatch();
    const name = useSelector(getFetchedProcessDetails)?.name;
    const processToDisplay = useSelector(getProcessToDisplay);

    const [searchParams] = useSearchParams();
    const from = searchParams.get("from");
    const to = searchParams.get("to");
    useEffect(() => {
        const countParams = VisualizationUrl.extractCountParams({ from, to });
        if (name && countParams) {
            dispatch(fetchAndDisplayProcessCounts(name, countParams.from, countParams.to, processToDisplay));
        }
    }, [dispatch, from, name, to, processToDisplay]);
}

function Visualization() {
    const { processName } = useDecodedParams<{ processName: string }>();
    const dispatch = useDispatch();

    const graphRef = useRef<Graph>();
    const getGraphInstance = useCallback(() => graphRef.current, [graphRef]);

    const [dataResolved, setDataResolved] = useState(false);

    const fetchData = useCallback(
        async (processName: string) => {
            await dispatch(fetchVisualizationData(processName));
            setDataResolved(true);
        },
        [dispatch],
    );

    const { graphLoading } = useSelector(getGraph);
    const fetchedProcessDetails = useSelector(getFetchedProcessDetails);
    const graphNotReady = useMemo(
        () => !dataResolved || isEmpty(fetchedProcessDetails) || graphLoading,
        [dataResolved, fetchedProcessDetails, graphLoading],
    );

    const processDefinitionData = useSelector(getProcessDefinitionData);
    const capabilities = useSelector(getCapabilities);
    const nothingToSave = useSelector((state) => ProcessUtils.nothingToSave(state as RootState));

    const getPastePosition = useCallback(() => {
        const paper = getGraphInstance()?.processGraphPaper;
        const { x, y } = paper?.getArea()?.center() || { x: 300, y: 100 };
        return { x: Math.floor(x), y: Math.floor(y) };
    }, [getGraphInstance]);

    useEffect(() => {
        fetchData(processName);
    }, [fetchData, processName]);

    useProcessState();
    useCountsIfNeeded();

    const { openNodes } = useModalDetailsIfNeeded();
    const openAndHighlightNodes = useCallback(
        async (process: ProcessType) => {
            const windows = await Promise.all(openNodes(process.json));
            getGraphInstance()?.highlightNodes(windows.map((w) => w.meta.node.id));
        },
        [getGraphInstance, openNodes],
    );

    useEffect(() => {
        if (graphNotReady) return;
        openAndHighlightNodes(fetchedProcessDetails);
    }, [fetchedProcessDetails, graphNotReady, openAndHighlightNodes]);

    useUnmountCleanup();
    useRouteLeavingGuard(capabilities.editFrontend && !nothingToSave);

    return (
        <ErrorHandler>
            <DndProvider options={HTML5toTouch}>
                <GraphPage data-testid="graphPage">
                    <GraphProvider graph={getGraphInstance}>
                        <SelectionContextProvider pastePosition={getPastePosition}>
                            <BindKeyboardShortcuts />
                            <Toolbars isReady={dataResolved} />
                        </SelectionContextProvider>
                    </GraphProvider>

                    <SpinnerWrapper isReady={!graphNotReady}>
                        {isEmpty(processDefinitionData) ? null : <GraphEl ref={graphRef} capabilities={capabilities} />}
                    </SpinnerWrapper>
                </GraphPage>
            </DndProvider>
        </ErrorHandler>
    );
}

export default Visualization;
