/* eslint-disable i18next/no-literal-string */
import { css, cx } from "@emotion/css";
import { CircularProgress, FormControl, FormHelperText, FormLabel } from "@mui/material";
import type { WindowButtonProps, WindowContentProps, WindowType } from "@touk/window-manager";
import i18next, { type TFunction } from "i18next";
import { keys } from "lodash";
import React, { useCallback, useContext, useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { useSelector } from "react-redux";
import { components as SelectComponents } from "react-select";

import Icon from "../../assets/img/toolbarButtons/compare.svg";
import { formatAbsolutely } from "../../common/DateUtils";
import { flattenObj, objectDiff } from "../../common/JsonUtils";
import HttpService from "../../http/HttpService";
import type { VersionsWithDifferencesResponse } from "../../http/HttpService";
import { getActivities } from "../../reducers/selectors/activities";
import { getProcessName, getProcessVersionId, getVersions } from "../../reducers/selectors/graph";
import { getEnvironmentAlert, getTargetEnvironmentId } from "../../reducers/selectors/settings";
import type { NodeType, StickyNoteNodeType } from "../../types";
import { WindowContent, WindowKind } from "../../windowManager";
import EdgeDetailsContent from "../graph/node-modal/edge/EdgeDetailsContent";
import type { Option } from "../graph/node-modal/fragment-input-definition/TypeSelect";
import { TypeSelect } from "../graph/node-modal/fragment-input-definition/TypeSelect";
import { WindowHeaderIconStyled } from "../graph/node-modal/nodeDetails/NodeDetailsStyled";
import { NodeDetailsContent } from "../graph/node-modal/NodeDetailsContent";
import { PathsToMarkProvider } from "../graph/node-modal/PathsToMark";
import { StickyNoteType } from "../graph/utils/stickyNotesUtils";
import type { ProcessVersionType } from "../Process/types";
import { PropertiesForm } from "../properties";
import type { ItemActivity } from "../toolbars/activities/ActivitiesPanel";
import type { ActivitiesResponse, ActivityType } from "../toolbars/activities/types";
import { CompareContainer, CompareModal, VersionHeader } from "./Styled";

type Environment = "local" | "remote";

type VersionDiffInfo = { changedElements: string[]; differencesUnknown: boolean };

const toVersionDiffsMap = (versions: VersionsWithDifferencesResponse["versions"]): Map<number, VersionDiffInfo> =>
    new Map(versions.map(({ versionId, changedElements, differencesUnknown }) => [versionId, { changedElements, differencesUnknown }]));

const describeVersionDiff = (diffInfo: VersionDiffInfo | undefined, t: TFunction): string => {
    if (!diffInfo) return "";
    return diffInfo.differencesUnknown
        ? t("dialog.compareVersions.unknownDifferences", "Unable to determine differences with the remote environment")
        : diffInfo.changedElements.join("\n");
};

type DiffsPageState = {
    diffs: Map<number, VersionDiffInfo> | null; // null = not yet loaded
    hasMore: boolean;
    nextPageNumber: number;
    isLoadingMore: boolean;
    error: boolean;
    unavailable: boolean;
};

const initialDiffsPageState: DiffsPageState = {
    diffs: null,
    hasMore: false,
    nextPageNumber: 0,
    isLoadingMore: false,
    error: false,
    unavailable: false,
};

const usePaginatedVersionDiffs = (
    fetchPage: ((pageNumber: number) => Promise<VersionsWithDifferencesResponse | null>) | null,
): DiffsPageState & { loadMore: () => void } => {
    const [state, setState] = useState<DiffsPageState>(initialDiffsPageState);

    const applyPage = useCallback((pageNumber: number, result: VersionsWithDifferencesResponse | null, isLoadingMore = false) => {
        setState((prev) =>
            result === null
                ? {
                      diffs: prev.diffs,
                      hasMore: prev.hasMore,
                      nextPageNumber: pageNumber,
                      isLoadingMore,
                      error: true,
                      unavailable: prev.unavailable,
                  }
                : {
                      diffs: new Map([...(prev.diffs ?? []), ...toVersionDiffsMap(result.versions)]),
                      hasMore: result.hasMore,
                      nextPageNumber: pageNumber + 1,
                      isLoadingMore,
                      error: false,
                      unavailable: Boolean(result.remoteUnavailable),
                  },
        );
    }, []);

    useEffect(() => {
        let cancelled = false;
        setState(initialDiffsPageState);
        if (fetchPage) {
            fetchPage(0).then((result) => {
                if (!cancelled) applyPage(0, result);
            });
        }
        return () => {
            cancelled = true;
        };
    }, [fetchPage, applyPage]);

    const loadMore = useCallback(() => {
        if (!fetchPage) return;
        setState((prev) => ({ ...prev, isLoadingMore: true }));
        // A page with 0 changed-elements versions still needs to be treated as "more to fetch": nothing to
        // show, but hasMore may still be true, so we keep paging until we surface a page with content or
        // run out of pages - the spinner needs to stay up for the whole chain, not just its first hop.
        const fetchFrom = (pageNumber: number) => {
            fetchPage(pageNumber).then((result) => {
                const willContinue = result !== null && result.versions.length === 0 && result.hasMore;
                applyPage(pageNumber, result, willContinue);
                if (willContinue) {
                    fetchFrom(pageNumber + 1);
                }
            });
        };
        fetchFrom(state.nextPageNumber);
    }, [fetchPage, applyPage, state.nextPageNumber]);

    return { ...state, loadMore };
};

type CommentableActivity = Pick<ActivitiesResponse["activities"][number], "type" | "scenarioVersionId" | "comment">;

// Only activities that represent an actual content change get to contribute a version's displayed comment.
// Deployment-lifecycle activities (SCENARIO_DEPLOYED, SCENARIO_CANCELED, SCENARIO_REDEPLOYED, ...) commonly
// carry their own comment (e.g. "restart", "redeploy action") which would otherwise clobber the meaningful
// save/migration comment for that same version, since it's chronologically later.
const COMMENT_SOURCE_ACTIVITY_TYPES: ReadonlySet<ActivityType> = new Set<ActivityType>([
    "SCENARIO_CREATED",
    "SCENARIO_MODIFIED",
    "SCENARIO_MODIFIED_WITH_AUTOSAVE",
    "AUTOMATIC_UPDATE",
]);

const toVersionCommentsMap = (activities: readonly CommentableActivity[]): Map<number, string> => {
    const map = new Map<number, string>();
    for (const a of activities) {
        if (
            COMMENT_SOURCE_ACTIVITY_TYPES.has(a.type) &&
            a.scenarioVersionId != null &&
            a.comment?.content.status === "AVAILABLE" &&
            a.comment.content.value
        ) {
            map.set(a.scenarioVersionId, a.comment.content.value);
        }
    }
    return map;
};

interface LoadMoreContextValue {
    hasMore: boolean;
    loadMore: () => void;
    isLoadingMore: boolean;
}
const LoadMoreContext = React.createContext<LoadMoreContextValue | null>(null);

const loadMoreRowStyle = css({
    display: "flex",
    alignItems: "center",
    justifyContent: "center",
    gap: "6px",
    padding: "8px 12px",
    textAlign: "center",
    fontSize: "0.85em",
    opacity: 0.7,
    borderTop: "1px solid rgba(128,128,128,0.2)",
});

const VersionMenuList = ({ children, ...props }: React.ComponentProps<typeof SelectComponents.MenuList>) => {
    const ctx = useContext(LoadMoreContext);
    const { t } = useTranslation();
    return (
        <SelectComponents.MenuList {...props}>
            {children}
            {ctx?.isLoadingMore ? (
                <div className={loadMoreRowStyle}>
                    <CircularProgress size="0.85rem" />
                    {t("dialog.compareVersions.loadingOlderVersions", "Loading older versions…")}
                </div>
            ) : (
                ctx?.hasMore && (
                    <div
                        onMouseDown={(e) => {
                            e.preventDefault();
                            ctx.loadMore();
                        }}
                        className={cx(
                            loadMoreRowStyle,
                            css({ cursor: "pointer", "&:hover": { opacity: 1, background: "rgba(128,128,128,0.1)" } }),
                        )}
                    >
                        {t("dialog.compareVersions.loadOlderVersions", "Load older versions…")}
                    </div>
                )
            )}
        </SelectComponents.MenuList>
    );
};
// The version label may contain a trailing "\n<comment>" (see createVersionElement) - react-select's default
// option styling collapses whitespace, so it has to be opted into preserving the line break explicitly.
const versionOptionLabel = css({ whiteSpace: "pre-line !important" });

const VersionOption = ({ children, innerProps, className, ...props }: React.ComponentProps<typeof SelectComponents.Option>) => (
    <SelectComponents.Option
        {...props}
        innerProps={{ ...innerProps, title: (props.data as Option).description }}
        className={cx(versionOptionLabel, className)}
    >
        {children}
    </SelectComponents.Option>
);
const VERSION_MENU_COMPONENTS = { MenuList: VersionMenuList, Option: VersionOption };

const initState: State = {
    environment: "local",
    otherVersion: null,
    currentDiffId: null,
    difference: null,
    remoteVersions: [],
    remoteActivities: null,
};

interface State {
    environment: Environment;
    currentDiffId: string;
    otherVersion: string;
    remoteVersions: ProcessVersionType[];
    difference: unknown;
    remoteActivities: ActivitiesResponse["activities"] | null; // null = not yet loaded or error
}

interface Props {
    predefinedOtherVersion?: string;
}
const VersionsForm = ({ predefinedOtherVersion }: Props) => {
    const remotePrefix = "remote-";

    const { t } = useTranslation();
    const [state, setState] = useState<State>(initState);
    const processName = useSelector(getProcessName);
    const version = useSelector(getProcessVersionId);
    const otherEnvironment = useSelector(getTargetEnvironmentId);
    const { content: localEnvironmentName } = useSelector(getEnvironmentAlert);
    const versions = useSelector(getVersions);
    const activities = useSelector(getActivities);

    const versionComments = useMemo(
        () => toVersionCommentsMap(activities.filter((activity): activity is ItemActivity => activity.uiType === "item")),
        [activities],
    );

    const remoteVersionComments = useMemo(() => toVersionCommentsMap(state.remoteActivities ?? []), [state.remoteActivities]);

    useEffect(() => {
        if (processName && otherEnvironment) {
            HttpService.fetchRemoteVersions(processName).then((response) =>
                setState((prevState) => ({ ...prevState, remoteVersions: response.data || [] })),
            );
        }
    }, [processName, otherEnvironment]);

    const fetchLocalPage = useMemo<((pageNumber: number) => Promise<VersionsWithDifferencesResponse | null>) | null>(
        () =>
            processName && version
                ? (pageNumber: number) =>
                      HttpService.fetchVersionsWithDifferences(processName, version, pageNumber)
                          .then((response) => response.data)
                          .catch(() => null)
                : null,
        [processName, version],
    );
    const localDiffsState = usePaginatedVersionDiffs(fetchLocalPage);

    const fetchRemotePage = useMemo<((pageNumber: number) => Promise<VersionsWithDifferencesResponse | null>) | null>(
        () =>
            processName && version && otherEnvironment
                ? (pageNumber: number) => HttpService.fetchRemoteVersionsWithDifferences(processName, version, pageNumber)
                : null,
        [processName, version, otherEnvironment],
    );
    const remoteDiffsState = usePaginatedVersionDiffs(fetchRemotePage);

    const remoteVersionDiffsLoaded = remoteDiffsState.diffs !== null;

    useEffect(() => {
        if (processName && otherEnvironment && remoteVersionDiffsLoaded) {
            HttpService.fetchRemoteActivities(processName).then((result) =>
                setState((prevState) => ({ ...prevState, remoteActivities: result?.activities ?? null })),
            );
        }
    }, [processName, otherEnvironment, remoteVersionDiffsLoaded]);

    const isLayoutChangeOnly = useCallback(
        (diffId: string): boolean => {
            const { type, currentNode, otherNode } = state.difference[diffId];
            if (type === "NodeDifferent") {
                return differentPathsForObjects(currentNode, otherNode).every((path) => path.startsWith("additionalFields.layoutData"));
            }
        },
        [state.difference],
    );

    const loadVersion = useCallback(
        (versionId: string) => {
            if (versionId) {
                HttpService.compareProcesses(processName, version, versionToPass(versionId), isRemote(versionId)).then((response) =>
                    setState((prevState) => ({ ...prevState, difference: response.data, otherVersion: versionId, currentDiffId: null })),
                );
            } else {
                setState((prev) => ({ ...prev, otherVersion: null, currentDiffId: null, difference: null }));
            }
        },
        [processName, version],
    );

    useEffect(() => {
        if (predefinedOtherVersion) {
            loadVersion(predefinedOtherVersion);
        }
    }, [loadVersion, predefinedOtherVersion]);

    const isRemote = (versionId: string) => {
        return versionId.startsWith(remotePrefix);
    };

    const versionToPass = (versionId: string) => {
        return versionId.replace(remotePrefix, "");
    };

    const versionDisplayString = useCallback(
        (versionId: string) => {
            return isRemote(versionId) ? `${versionToPass(versionId)} on ${otherEnvironment}` : versionId;
        },
        [otherEnvironment],
    );

    const createVersionId = (version: ProcessVersionType, versionPrefix = "") => {
        return versionPrefix + version.processVersionId;
    };

    const createVersionElement = useCallback(
        (version: ProcessVersionType, versionPrefix = "") => {
            const versionId = createVersionId(version, versionPrefix);
            const comment = versionPrefix
                ? remoteVersionComments.get(version.processVersionId)
                : versionComments.get(version.processVersionId);
            const commentSuffix = comment ? `\n${comment}` : "";
            return `${versionDisplayString(versionId)} - created by ${version.user} ${formatAbsolutely(
                version.createDate,
            )}${commentSuffix}`;
        },
        [versionDisplayString, versionComments, remoteVersionComments],
    );

    const enrichStickyNoteNode = (node: NodeType): StickyNoteNodeType => {
        return {
            ...node,
            type: StickyNoteType,
        } as StickyNoteNodeType;
    };

    const printDiff = (diffId: string) => {
        const diff = state.difference[diffId];

        switch (diff.type) {
            case "StickyNotePresentInOther":
            case "StickyNotePresentInCurrent":
            case "StickyNoteDifferent":
                return renderDiff(enrichStickyNoteNode(diff.currentStickyNote), enrichStickyNoteNode(diff.otherStickyNote), printNode);
            case "NodeNotPresentInOther":
            case "NodeNotPresentInCurrent":
            case "NodeDifferent":
                return renderDiff(diff.currentNode, diff.otherNode, printNode);
            case "EdgeNotPresentInCurrent":
            case "EdgeNotPresentInOther":
            case "EdgeDifferent":
                return renderDiff(diff.currentEdge, diff.otherEdge, printEdge);
            case "PropertiesDifferent":
                return renderDiff(diff.currentProperties, diff.otherProperties, printProperties);
            default:
                console.error(`Difference type ${diff.type} is not supported`);
        }
    };

    const renderDiff = (currentElement, otherElement, printElement) => {
        const differentPaths = differentPathsForObjects(currentElement, otherElement);
        return (
            <CompareContainer>
                <PathsToMarkProvider value={differentPaths}>
                    <div>
                        <VersionHeader>Current version</VersionHeader>
                        {printElement(currentElement)}
                    </div>
                    <div>
                        <VersionHeader>Version {versionDisplayString(state.otherVersion)}</VersionHeader>
                        {printElement(otherElement)}
                    </div>
                </PathsToMarkProvider>
            </CompareContainer>
        );
    };

    const differentPathsForObjects = (currentNode, otherNode) => {
        const diffObject = objectDiff(currentNode, otherNode);
        const flatObj = flattenObj(diffObject);
        return Object.keys(flatObj);
    };

    const printNode = (node: NodeType) => {
        return node?.id ? <NodeDetailsContent node={node} /> : <div className="notPresent">Node not present</div>;
    };

    const stubOnChange = () => {
        return;
    };

    const printEdge = (edge) => {
        return edge ? (
            <EdgeDetailsContent
                edge={edge}
                readOnly={true}
                showValidation={false}
                showSwitch={false}
                changeEdgeTypeValue={stubOnChange}
                changeEdgeTypeCondition={stubOnChange}
                variableTypes={{}}
            />
        ) : (
            <div className="notPresent">Edge not present</div>
        );
    };

    const printProperties = (property) => {
        return property ? <PropertiesForm editedProperties={property} /> : <div className="notPresent">Properties not present</div>;
    };

    const versionOptions: Option[] = useMemo(() => {
        if (state.environment === "remote") {
            const remoteDiffs = remoteDiffsState.diffs;
            const remoteError = remoteDiffsState.error;
            if (remoteDiffs === null && !remoteError) return [{ label: "", value: "" }];
            const filtered = (state?.remoteVersions ?? []).filter(
                (v) => remoteError || remoteDiffs?.has(v.processVersionId) || createVersionId(v, remotePrefix) === state.otherVersion,
            );
            return [
                { label: "", value: "" },
                ...filtered.map((v) => ({
                    label: createVersionElement(v, remotePrefix),
                    value: createVersionId(v, remotePrefix),
                    description: describeVersionDiff(remoteDiffs?.get(v.processVersionId), t),
                })),
            ];
        }
        const localDiffs = localDiffsState.diffs;
        const localError = localDiffsState.error;
        if (localDiffs === null && !localError) return [{ label: "", value: "" }];
        return [
            { label: "", value: "" },
            ...versions
                .filter(
                    (v) =>
                        version !== v.processVersionId &&
                        (localError || localDiffs?.has(v.processVersionId) || createVersionId(v) === state.otherVersion),
                )
                .map((v) => ({
                    label: createVersionElement(v),
                    value: createVersionId(v),
                    description: describeVersionDiff(localDiffs?.get(v.processVersionId), t),
                })),
        ];
    }, [
        createVersionElement,
        state.environment,
        state.otherVersion,
        state?.remoteVersions,
        remoteDiffsState.diffs,
        remoteDiffsState.error,
        localDiffsState.diffs,
        localDiffsState.error,
        version,
        versions,
        t,
    ]);

    const differenceOptions: Option[] = useMemo(() => {
        return [
            { label: "", value: "" },
            ...keys(state?.difference ?? []).map((diffId) => {
                const layoutChangeOnly = isLayoutChangeOnly(diffId);
                return {
                    label: `${diffId} ${layoutChangeOnly ? "(position only)" : ""}`,
                    value: diffId,
                    isDisabled: layoutChangeOnly,
                };
            }),
        ];
    }, [isLayoutChangeOnly, state?.difference]);

    const handleEnvironmentChange = useCallback((env: string) => {
        setState((prev) => ({
            ...prev,
            environment: env as Environment,
            otherVersion: null,
            currentDiffId: null,
            difference: null,
        }));
    }, []);

    const loadMoreContextValue = useMemo<LoadMoreContextValue>(
        () => ({
            hasMore: state.environment === "local" ? localDiffsState.hasMore : remoteDiffsState.hasMore,
            loadMore: state.environment === "local" ? localDiffsState.loadMore : remoteDiffsState.loadMore,
            isLoadingMore: state.environment === "local" ? localDiffsState.isLoadingMore : remoteDiffsState.isLoadingMore,
        }),
        [
            state.environment,
            localDiffsState.hasMore,
            remoteDiffsState.hasMore,
            localDiffsState.loadMore,
            remoteDiffsState.loadMore,
            localDiffsState.isLoadingMore,
            remoteDiffsState.isLoadingMore,
        ],
    );

    const environmentOptions: Option[] = useMemo(() => {
        if (!otherEnvironment) return [];
        const localLabel = localEnvironmentName
            ? t("dialog.compareVersions.localWithName", "Local: {{name}}", { name: localEnvironmentName })
            : t("dialog.compareVersions.local", "Local");
        return [
            { label: localLabel, value: "local" },
            { label: t("dialog.compareVersions.remoteWithName", "Remote: {{name}}", { name: otherEnvironment }), value: "remote" },
        ];
    }, [otherEnvironment, localEnvironmentName, t]);

    return (
        <>
            {otherEnvironment && (
                <FormControl>
                    <FormLabel>{t("dialog.compareVersions.environment", "Environment")}</FormLabel>
                    <TypeSelect
                        readOnly={Boolean(predefinedOtherVersion)}
                        id="environment"
                        onChange={handleEnvironmentChange}
                        value={environmentOptions.find((o) => o.value === state.environment)}
                        options={environmentOptions}
                        fieldErrors={[]}
                    />
                </FormControl>
            )}
            <FormControl>
                <FormLabel>Version to compare</FormLabel>
                <LoadMoreContext.Provider value={loadMoreContextValue}>
                    <TypeSelect
                        readOnly={Boolean(predefinedOtherVersion)}
                        autoFocus={true}
                        id="otherVersion"
                        onChange={loadVersion}
                        value={versionOptions.find((option) => option.value === state.otherVersion)}
                        options={versionOptions}
                        fieldErrors={[]}
                        selectComponents={VERSION_MENU_COMPONENTS}
                    />
                </LoadMoreContext.Provider>
                {state.environment === "remote" && remoteDiffsState.unavailable && (
                    <FormHelperText error>
                        {t("dialog.compareVersions.remoteUnavailable", "Could not reach the {{name}} environment", {
                            name: otherEnvironment,
                        })}
                    </FormHelperText>
                )}
            </FormControl>
            {state.otherVersion ? (
                <div>
                    <FormControl>
                        <FormLabel>Difference to pick</FormLabel>
                        <TypeSelect
                            id="differentVersion"
                            onChange={(value) => setState({ ...state, currentDiffId: value })}
                            value={differenceOptions.find((option) => option.value === state.currentDiffId)}
                            options={differenceOptions}
                            fieldErrors={[]}
                        />
                    </FormControl>
                    {state.currentDiffId ? printDiff(state.currentDiffId) : null}
                </div>
            ) : null}
        </>
    );
};

export const handleOpenCompareVersionDialog = (
    scenarioVersionId?: string,
): Partial<WindowType<number, { scenarioVersionId?: string }>> => ({
    title: i18next.t("dialog.title.compareVersions", "compare versions"),
    isResizable: true,
    minWidth: 980,
    minHeight: 200,
    kind: WindowKind.compareVersions,
    meta: { scenarioVersionId },
});

const CompareVersionsDialog = (props: WindowContentProps<number, { scenarioVersionId?: string }>) => {
    const { t } = useTranslation();
    const buttons: WindowButtonProps[] = useMemo(() => [{ title: t("dialog.button.ok", "OK"), action: props.close }], [props.close, t]);

    return (
        <WindowContent buttons={buttons} icon={<WindowHeaderIconStyled as={Icon} type={props.data.kind} />} {...props}>
            <CompareModal className={cx("modalContentDark", css({ padding: "1em" }))}>
                <VersionsForm predefinedOtherVersion={props.data.meta.scenarioVersionId} />
            </CompareModal>
        </WindowContent>
    );
};

export default CompareVersionsDialog;
