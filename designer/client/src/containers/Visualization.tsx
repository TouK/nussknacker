import { useWindowManager } from "@touk/window-manager";
import type { dia } from "jointjs";
import { isEmpty } from "lodash";
import React, { memo, useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useErrorBoundary } from "react-error-boundary";
import { useNavigate, useSearchParams } from "react-router-dom";

import { fetchAndDisplayProcessCounts } from "../actions/nk/displayProcessCounts";
import { fetchVisualizationData } from "../actions/nk/fetchVisualizationData";
import { findFreeSpaceForNode } from "../actions/nk/findFreeSpaceForNode";
import { clearProcess, loadProcessState } from "../actions/nk/process";
import { expandSelection } from "../actions/nk/selection";
import { useDecodedParams } from "../common/routerUtils";
import { extractCountParams } from "../common/VisualizationUrl";
import { isAiAssistantDialog } from "../components/aiAssistant/components/IsAiAssistantDialog";
import { useScenarioCommands } from "../components/CommandBar/scenario/useScenarioCommands";
import type { Graph } from "../components/graph/Graph";
import { GraphProvider } from "../components/graph/GraphContext";
import { usePortal } from "../components/graph/node-modal/io/usePortal";
import { ProcessGraph as GraphEl } from "../components/graph/ProcessGraph";
import SelectionContextProvider from "../components/graph/SelectionContextProvider";
import type { Scenario } from "../components/Process/types";
import { useRouteLeavingGuard } from "../components/RouteLeavingGuard";
import SpinnerWrapper from "../components/spinner/SpinnerWrapper";
import { Overlay } from "../components/toolbarComponents/Overlay";
import Toolbars from "../components/toolbars/Toolbars";
import { getProcessDefinitionData } from "../reducers/selectors/getProcessDefinitionData";
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
import { useAppDispatch, useAppSelector } from "../store/storeHelpers";
import { useWindows } from "../windowManager/useWindows";
import { AddComponentsButtons } from "./AddComponentsButtons";
import { AdjustNodeOverlapBehavior } from "./AdjustNodeOverlapBehavior";
import { AddAssertionResultToNodes } from "./assertions/addAssertionResultToNodes";
import { BindKeyboardShortcuts } from "./BindKeyboardShortcuts";
import { useModalsIfNeeded } from "./hooks/useModalsIfNeeded";
import { useInterval } from "./Interval";
import { LiveDataThroughputs } from "./liveData/LiveDataThroughputs";
import { useLiveDataIfNeeded } from "./liveData/useLiveDataIfNeeded";
import { GraphPage } from "./Page";
import { PanToNodes } from "./PanToNodes";
import { VisualizationBasePath } from "./paths";
import { ScenarioDescription } from "./ScenarioDescription";

function useUnmountCleanup() {
    const { closeAll } = useWindows();
    const dispatch = useAppDispatch();
    const closeRef = useRef(closeAll);
    closeRef.current = closeAll;

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
    const dispatch = useAppDispatch();
    const scenario = useAppSelector(getScenario);
    const versionId = useAppSelector(getProcessVersionId);
    const { isFragment, isArchived, name } = scenario || {};

    const fetch = useCallback(() => dispatch(loadProcessState(name, versionId)), [dispatch, name, versionId]);
    const disabled = !name || isFragment || isArchived;

    useInterval(fetch, {
        refreshTime,
        disabled,
    });
}

function useCountsIfNeeded() {
    const dispatch = useAppDispatch();
    const scenario = useAppSelector(getScenario);
    const scenarioGraph = useAppSelector(getScenarioGraph);

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
    const isLatestVersion = useAppSelector(isLatestProcessVersion);
    const currentVersionId = useAppSelector(getProcessVersionId);
    const [latestVersion, ...otherVersions] = useAppSelector(getVersions);
    const navigate = useNavigate();
    // const dispatch = useAppDispatch();

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
    const dispatch = useAppDispatch();
    const { showBoundary } = useErrorBoundary();

    const graphRef = useRef<Graph>(null);
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

    const scenarioLoading = useAppSelector(getScenarioLoading);
    const scenario = useAppSelector(getScenario);
    const graphNotReady = useMemo(() => !dataResolved || isEmpty(scenario) || scenarioLoading, [dataResolved, scenario, scenarioLoading]);

    const processDefinitionData = useAppSelector(getProcessDefinitionData);
    const capabilities = useAppSelector(getCapabilities);
    const nothingToSave = useAppSelector(isPristine);

    const getPastePosition = useCallback(
        (size?: dia.Size) => {
            const instance = getGraphInstance();
            const paper = instance?.processGraphPaper;
            const point = paper?.clientToLocalRect(instance.viewport).center().snapToGrid(1, 1) || { x: 0, y: 0 };
            return findFreeSpaceForNode(paper, point, null, size);
        },
        [getGraphInstance],
    );

    useEffect(() => {
        fetchData(processName);
    }, [fetchData, processName]);

    useScenarioCommands();
    useProcessState();
    useCountsIfNeeded();
    useLiveDataIfNeeded();
    // useVersionSwitchIfNeeded(processName, version);

    const { openNodes, openToolWindows } = useModalsIfNeeded();
    const openAndHighlightNodes = useCallback(
        async (scenario: Scenario) => {
            const windows = await Promise.all(openNodes(scenario));
            windows.map((w) => dispatch(expandSelection(w.meta.node.id, true)));
        },
        [dispatch, openNodes],
    );

    useEffect(() => {
        if (graphNotReady) return;
        openToolWindows();
    }, [graphNotReady, openToolWindows]);

    useEffect(() => {
        if (graphNotReady) return;
        openAndHighlightNodes(scenario);
    }, [graphNotReady, openAndHighlightNodes, scenario]);

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
                    <PanToNodes />
                    <AdjustNodeOverlapBehavior />
                    <SelectionContextProvider pastePosition={getPastePosition}>
                        <BindKeyboardShortcuts disabled={windows.filter((w) => !isAiAssistantDialog(w.id)).length > 0} />
                        <Toolbars isReady={dataResolved} externalLayerWrapper={Portal}>
                            <Overlay gridArea="left" gridRow="top">
                                <ScenarioDescription />
                            </Overlay>
                            <AddComponentsButtons />
                        </Toolbars>
                    </SelectionContextProvider>
                    <AddAssertionResultToNodes />
                </GraphProvider>
            </GraphPage>
            <div data-testid="toolbar-portal" ref={portalRef} />
        </>
    );
}

export default memo(Visualization);
