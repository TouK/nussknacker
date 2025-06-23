import { useWindowManager } from "@touk/window-manager";
import { isEmpty } from "lodash";
import React, { useCallback, useEffect, useMemo } from "react";
import { useErrorBoundary } from "react-error-boundary";
import { useDispatch, useSelector } from "react-redux";
import { useNavigate, useSearchParams } from "react-router-dom";

import { clearProcess, clearScenarioState, expandSelection, fetchAndDisplayProcessCounts, loadProcessState } from "../actions/nk";
import { fetchVisualizationData } from "../actions/nk/fetchVisualizationData";
import { useDecodedParams } from "../common/routerUtils";
import { extractCountParams } from "../common/VisualizationUrl";
import type { Graph } from "../components/graph/Graph";
import { GraphProvider } from "../components/graph/GraphContext";
import { usePortal } from "../components/graph/node-modal/io/usePortal";
import { ProcessGraph as GraphEl } from "../components/graph/ProcessGraph";
import SelectionContextProvider from "../components/graph/SelectionContextProvider";
import type { Scenario } from "../components/Process/types";
import { useRouteLeavingGuard } from "../components/RouteLeavingGuard";
import SpinnerWrapper from "../components/spinner/SpinnerWrapper";
import Toolbars from "../components/toolbars/Toolbars";
import {
    getProcessVersionId,
    getScenario,
    getScenarioGraph,
    getScenarioLoading,
    getVersions,
    isLatestProcessVersion,
    isPristine,
} from "../reducers/selectors/graph";
import { getCapabilities } from "../reducers/selectors/other";
import { getProcessDefinitionData } from "../reducers/selectors/processDefinitionData";
import { useWindows } from "../windowManager";
import { BindKeyboardShortcuts } from "./BindKeyboardShortcuts";
import { useModalDetailsIfNeeded } from "./hooks/useModalDetailsIfNeeded";
import { useInterval } from "./Interval";
import { LiveDataThroughputs } from "./liveData/LiveDataThroughputs";
import { useLiveDataIfNeeded } from "./liveData/useLiveDataIfNeeded";
import { GraphPage } from "./Page";
import { VisualizationBasePath } from "./paths";
import { ScenarioDescription } from "./ScenarioDescription";

function useUnmountCleanup() {
    const { close } = useWindows();
    const dispatch = useDispatch();
    const closeRef = useRef(close);
    closeRef.current = close;

    const cleanup = useCallback(async () => {
        await closeRef.current();
        dispatch(clearProcess());
        dispatch(clearScenarioState());
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
    const versionId = useSelector(getProcessVersionId);
    const { isFragment, isArchived, name } = scenario || {};

    const fetch = useCallback(() => dispatch(loadProcessState(name, versionId)), [dispatch, name, versionId]);
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
        if (!scenario?.name || scenario.isFragment) return;

        const countParams = extractCountParams({
            from,
            to,
            refresh,
        });
        if (!countParams) return;

        dispatch(
            fetchAndDisplayProcessCounts({
                processName: scenario.name,
                ...countParams,
            }),
        );
    }, [dispatch, from, refresh, scenario, scenarioGraph, to]);
}

function useVersionSwitchIfNeeded(processName: string, version: string) {
    const isLatestVersion = useSelector(isLatestProcessVersion);
    const currentVersionId = useSelector(getProcessVersionId);
    const [latestVersion, ...otherVersions] = useSelector(getVersions);
    const navigate = useNavigate();
    // const dispatch = useDispatch();

    useEffect(() => {
        const urlVersionId = parseInt(version);
        console.debug({
            processName,
            isLatestVersion,
            urlVersionId,
            currentVersionId,
            latestVersion,
            otherVersions,
        });
        if (version) {
            navigate(`${VisualizationBasePath}/${processName}`);
        }
        // navigate(`${VisualizationBasePath}/${processName}/${currentVersionId}`);
        // dispatch(displayScenarioVersion(processName, urlVersionId));
    }, [currentVersionId, isLatestVersion, latestVersion, navigate, otherVersions, processName, version]);
}

function Visualization() {
    const { processName, version } = useDecodedParams<{
        processName: string;
        version: string;
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

    const scenarioLoading = useSelector(getScenarioLoading);
    const scenario = useSelector(getScenario);
    const graphNotReady = useMemo(() => !dataResolved || isEmpty(scenario) || scenarioLoading, [dataResolved, scenario, scenarioLoading]);

    const processDefinitionData = useSelector(getProcessDefinitionData);
    const capabilities = useSelector(getCapabilities);
    const nothingToSave = useSelector(isPristine);

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
    useLiveDataIfNeeded();
    // useVersionSwitchIfNeeded(processName, version);

    const { openNodes } = useModalDetailsIfNeeded();
    const openAndHighlightNodes = useCallback(
        async (scenario: Scenario) => {
            const windows = await Promise.all(openNodes(scenario));
            windows.map((w) => dispatch(expandSelection(w.meta.node.id, true)));
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
    const [Portal, portalRef] = usePortal();

    return (
        <>
            <GraphPage data-testid="graphPage">
                <SpinnerWrapper isReady={!graphNotReady}>
                    {isEmpty(processDefinitionData) ? null : <GraphEl ref={graphRef} capabilities={capabilities} />}
                </SpinnerWrapper>

                <GraphProvider graph={getGraphInstance}>
                    <LiveDataThroughputs />
                    <SelectionContextProvider pastePosition={getPastePosition}>
                        <BindKeyboardShortcuts disabled={windows.length > 0} />
                        <Toolbars isReady={dataResolved} externalLayerWrapper={Portal}>
                            <ScenarioDescription />
                        </Toolbars>
                    </SelectionContextProvider>
                </GraphProvider>
            </GraphPage>
            <div data-testid="toolbar-portal" ref={portalRef} />
        </>
    );
}

export default memo(Visualization);
