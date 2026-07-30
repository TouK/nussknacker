/* eslint-disable i18next/no-literal-string */
import { css, cx } from "@emotion/css";
import { CircularProgress, FormControl, FormHelperText, FormLabel } from "@mui/material";
import type { WindowButtonProps, WindowContentProps, WindowType } from "@touk/window-manager";
import i18next, { type TFunction } from "i18next";
import { keys } from "lodash";
import React, { useCallback, useContext, useEffect, useMemo, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { useDispatch, useSelector } from "react-redux";
import { components as SelectComponents } from "react-select";

import { getScenarioActivities } from "../../actions/nk/scenarioActivities";
import Icon from "../../assets/img/toolbarButtons/compare.svg";
import { formatAbsolutely } from "../../common/DateUtils";
import { flattenObj, objectDiff } from "../../common/JsonUtils";
import HttpService from "../../http/HttpService";
import type { VersionWithDifference } from "../../http/HttpService";
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
import type { ActivitiesResponse } from "../toolbars/activities/types";
import { CompareContainer, CompareModal, VersionHeader } from "./Styled";

type Environment = "local" | "remote";

type VersionDiffInfo = {
    changedElements: string[];
    differencesUnknown: boolean;
    comment?: string;
    totalChangedElements?: number;
};

const MAX_DESCRIBED_CHANGES = 20;

// `nextOffset` is absent from the endpoint that answers for a whole history at once, which reads the same
// as an exhausted list.
type VersionDiffsResponse = { versions: VersionWithDifference[]; nextOffset?: number | null; remoteUnavailable?: boolean };

const toVersionDiffsMap = (versions: VersionWithDifference[]): Map<number, VersionDiffInfo> =>
    new Map(
        versions.map(({ versionId, changedElements, differencesUnknown, comment, totalChangedElements }) => [
            versionId,
            { changedElements, differencesUnknown, comment, totalChangedElements },
        ]),
    );

const describeVersionDiff = (diffInfo: VersionDiffInfo | undefined, t: TFunction): string => {
    if (!diffInfo) return "";
    if (diffInfo.differencesUnknown) {
        return t("dialog.compareVersions.unknownDifferences", "Unable to determine differences with the remote environment");
    }
    const described = diffInfo.changedElements.slice(0, MAX_DESCRIBED_CHANGES);
    const remaining = (diffInfo.totalChangedElements ?? diffInfo.changedElements.length) - described.length;
    if (remaining > 0) {
        described.push(t("dialog.compareVersions.moreChanges", "…and {{count}} more", { count: remaining }));
    }
    return described.join("\n");
};

type DiffsState = {
    diffs: Map<number, VersionDiffInfo> | null; // null = not yet loaded
    nextOffset: number | null; // null = the version list is exhausted
    isLoadingMore: boolean;
    error: boolean;
    unavailable: boolean;
};

const initialDiffsState: DiffsState = {
    diffs: null,
    nextOffset: 0,
    isLoadingMore: false,
    error: false,
    unavailable: false,
};

const usePaginatedVersionDiffs = (
    fetchPage: ((offset: number) => Promise<VersionDiffsResponse | null>) | null,
): DiffsState & { loadMore: () => void; hasMore: boolean } => {
    const [state, setState] = useState<DiffsState>(initialDiffsState);
    // a save while the dialog is open changes `fetchPage`, and the response in flight for the old version
    // must not be merged into the state that reset for the new one
    const generationRef = useRef(0);

    const applyPage = useCallback((generation: number, offset: number, result: VersionDiffsResponse | null) => {
        if (generation !== generationRef.current) return;
        setState((prev) =>
            result === null
                ? { ...prev, nextOffset: offset, isLoadingMore: false, error: true }
                : {
                      diffs: new Map([...(prev.diffs ?? []), ...toVersionDiffsMap(result.versions)]),
                      nextOffset: result.nextOffset ?? null,
                      isLoadingMore: false,
                      error: false,
                      unavailable: Boolean(result.remoteUnavailable),
                  },
        );
    }, []);

    useEffect(() => {
        const generation = ++generationRef.current;
        setState(initialDiffsState);
        if (fetchPage) {
            fetchPage(0).then((result) => applyPage(generation, 0, result));
        }
    }, [fetchPage, applyPage]);

    const loadMore = useCallback(() => {
        if (!fetchPage || state.nextOffset === null || state.isLoadingMore) return;
        const generation = generationRef.current;
        const offset = state.nextOffset;
        setState((prev) => ({ ...prev, isLoadingMore: true, error: false }));
        fetchPage(offset).then((result) => applyPage(generation, offset, result));
    }, [fetchPage, applyPage, state.nextOffset, state.isLoadingMore]);

    return { ...state, hasMore: state.diffs !== null && state.nextOffset !== null, loadMore };
};

type CommentableActivity = Pick<ActivitiesResponse["activities"][number], "scenarioVersionId" | "comment" | "date">;

const toVersionCommentsMap = (activities: readonly CommentableActivity[]): Map<number, string> => {
    const newestFirst = [...activities].sort((a, b) => new Date(b.date).getTime() - new Date(a.date).getTime());
    const map = new Map<number, string>();
    for (const a of newestFirst) {
        if (
            a.scenarioVersionId != null &&
            a.comment?.content.status === "AVAILABLE" &&
            a.comment.content.value &&
            !map.has(a.scenarioVersionId)
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
    failed: boolean;
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
    const loadMore = () => ctx?.loadMore();
    return (
        <SelectComponents.MenuList {...props}>
            {children}
            {ctx?.isLoadingMore ? (
                <div className={loadMoreRowStyle}>
                    <CircularProgress size="0.85rem" />
                    {t("dialog.compareVersions.loadingMoreVersions", "Loading more versions…")}
                </div>
            ) : (
                ctx?.hasMore && (
                    // not focusable - react-select keeps focus on its own input, so keyboard users reach this
                    // through onMenuScrollToBottom instead
                    <div
                        aria-hidden
                        onMouseDown={(e) => {
                            e.preventDefault();
                            loadMore();
                        }}
                        className={cx(
                            loadMoreRowStyle,
                            css({ cursor: "pointer", "&:hover": { opacity: 1, background: "rgba(128,128,128,0.1)" } }),
                        )}
                    >
                        {ctx.failed
                            ? t("dialog.compareVersions.retryLoadMoreVersions", "Could not load more versions — retry")
                            : t("dialog.compareVersions.loadMoreVersions", "Load more versions…")}
                    </div>
                )
            )}
        </SelectComponents.MenuList>
    );
};

const versionCommentStyle = css({
    display: "-webkit-box",
    WebkitLineClamp: 2,
    WebkitBoxOrient: "vertical",
    overflow: "hidden",
    fontSize: "0.85em",
    opacity: 0.7,
});

const VersionOption = ({ children, innerProps, ...props }: React.ComponentProps<typeof SelectComponents.Option>) => {
    const { description, comment } = props.data as Option;
    return (
        <SelectComponents.Option {...props} innerProps={{ ...innerProps, title: description }}>
            {children}
            {comment ? <div className={versionCommentStyle}>{comment}</div> : null}
        </SelectComponents.Option>
    );
};

const VERSION_MENU_COMPONENTS = { MenuList: VersionMenuList, Option: VersionOption };

const noNewVersionOptions = () => false;

const initState: State = {
    environment: "local",
    otherVersion: null,
    currentDiffId: null,
    difference: null,
    remoteVersions: [],
    remoteVersionsFailed: false,
};

interface State {
    environment: Environment;
    currentDiffId: string;
    otherVersion: string;
    remoteVersions: ProcessVersionType[];
    remoteVersionsFailed: boolean;
    difference: unknown;
}

interface Props {
    predefinedOtherVersion?: string;
}
const VersionsForm = ({ predefinedOtherVersion }: Props) => {
    const remotePrefix = "remote-";

    const { t } = useTranslation();
    const dispatch = useDispatch();
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

    // keyed by scenario, not a plain flag: the store can still hold the previously opened scenario's
    // activities, which a flag would mistake for this one's
    const activitiesRequestedFor = useRef<string | null>(null);
    useEffect(() => {
        if (processName && activitiesRequestedFor.current !== processName) {
            activitiesRequestedFor.current = processName;
            dispatch(getScenarioActivities(processName));
        }
    }, [processName, dispatch]);

    const remoteSelected = state.environment === "remote";

    useEffect(() => {
        if (processName && otherEnvironment && remoteSelected) {
            HttpService.fetchRemoteVersions(processName)
                .then((response) =>
                    setState((prevState) => ({ ...prevState, remoteVersions: response.data || [], remoteVersionsFailed: false })),
                )
                .catch(() => setState((prevState) => ({ ...prevState, remoteVersions: [], remoteVersionsFailed: true })));
        }
    }, [processName, otherEnvironment, remoteSelected]);

    const fetchLocalPage = useMemo<((offset: number) => Promise<VersionDiffsResponse | null>) | null>(
        () =>
            processName && version
                ? (offset: number) =>
                      HttpService.fetchVersionsWithDifferences(processName, version, offset)
                          .then((response) => response.data)
                          .catch(() => null)
                : null,
        [processName, version],
    );
    const localDiffsState = usePaginatedVersionDiffs(fetchLocalPage);

    const fetchRemotePage = useMemo<(() => Promise<VersionDiffsResponse | null>) | null>(
        () =>
            processName && version && otherEnvironment && remoteSelected
                ? () => HttpService.fetchRemoteVersionsWithDifferences(processName, version)
                : null,
        [processName, version, otherEnvironment, remoteSelected],
    );
    const remoteDiffsState = usePaginatedVersionDiffs(fetchRemotePage);

    const {
        diffs: activeDiffs,
        error: activeDiffsError,
        hasMore,
        isLoadingMore,
        loadMore,
    } = state.environment === "remote" ? remoteDiffsState : localDiffsState;
    const isLoadingVersions = activeDiffs === null && !activeDiffsError;
    const showUnfilteredVersions = activeDiffs === null && activeDiffsError;

    const isLayoutChangeOnly = useCallback(
        (diffId: string): boolean => {
            const diff = state.difference[diffId];
            if (diff.type === "NodeDifferent") {
                return differentPathsForObjects(diff.currentNode, diff.otherNode).every((path) =>
                    path.startsWith("additionalFields.layoutData"),
                );
            }
            if (diff.type === "StickyNoteDifferent") {
                return differentPathsForObjects(diff.currentStickyNote, diff.otherStickyNote).every((path) =>
                    path.startsWith("additionalFields.layoutData"),
                );
            }
            return false;
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
            return `${versionDisplayString(versionId)} - created by ${version.user} ${formatAbsolutely(version.createDate)}`;
        },
        [versionDisplayString],
    );

    const versionComment = useCallback(
        (version: ProcessVersionType, versionPrefix = "") =>
            versionPrefix ? activeDiffs?.get(version.processVersionId)?.comment : versionComments.get(version.processVersionId),
        [versionComments, activeDiffs],
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

    // both directions, because objectDiff only walks the keys of its first argument
    const differentPathsForObjects = (currentNode, otherNode) => {
        const paths = [
            ...Object.keys(flattenObj(objectDiff(currentNode, otherNode))),
            ...Object.keys(flattenObj(objectDiff(otherNode, currentNode))),
        ];
        return [...new Set(paths)];
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
        const clearOption: Option[] = state.otherVersion ? [{ label: "", value: "" }] : [];

        const isRemoteEnvironment = state.environment === "remote";
        const prefix = isRemoteEnvironment ? remotePrefix : "";
        const candidates = isRemoteEnvironment ? (state?.remoteVersions ?? []) : versions.filter((v) => version !== v.processVersionId);

        const isSelected = (v: ProcessVersionType) => createVersionId(v, prefix) === state.otherVersion;

        const toOption = (v: ProcessVersionType): Option => ({
            label: createVersionElement(v, prefix),
            value: createVersionId(v, prefix),
            description: describeVersionDiff(activeDiffs?.get(v.processVersionId), t),
            comment: versionComment(v, prefix),
        });

        if (isLoadingVersions) return [...clearOption, ...candidates.filter(isSelected).map(toOption)];

        return [
            ...clearOption,
            ...candidates.filter((v) => showUnfilteredVersions || activeDiffs?.has(v.processVersionId) || isSelected(v)).map(toOption),
        ];
    }, [
        createVersionElement,
        versionComment,
        activeDiffs,
        isLoadingVersions,
        showUnfilteredVersions,
        state.environment,
        state.otherVersion,
        state?.remoteVersions,
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
        () => ({ hasMore, loadMore, isLoadingMore, failed: activeDiffsError }),
        [hasMore, loadMore, isLoadingMore, activeDiffsError],
    );

    const handleMenuScrollToBottom = useCallback(() => {
        if (hasMore) loadMore();
    }, [hasMore, loadMore]);

    const noVersionsMessage = useCallback(
        () =>
            hasMore
                ? t("dialog.compareVersions.noVersionsFoundYet", "No differing version found yet - load more to keep looking")
                : t("dialog.compareVersions.noVersionsToCompare", "No other version differs from this one"),
        [hasMore, t],
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
                        isLoading={isLoadingVersions}
                        isValidNewOption={noNewVersionOptions}
                        noOptionsMessage={noVersionsMessage}
                        onMenuScrollToBottom={handleMenuScrollToBottom}
                    />
                </LoadMoreContext.Provider>
                {state.environment === "remote" && (remoteDiffsState.unavailable || state.remoteVersionsFailed) && (
                    <FormHelperText error>
                        {t("dialog.compareVersions.remoteUnavailable", "Could not compare versions with the {{name}} environment", {
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
