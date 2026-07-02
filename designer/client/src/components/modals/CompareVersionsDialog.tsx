/* eslint-disable i18next/no-literal-string */
import { css, cx } from "@emotion/css";
import { FormControl, FormLabel } from "@mui/material";
import type { WindowButtonProps, WindowContentProps, WindowType } from "@touk/window-manager";
import i18next from "i18next";
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
import { getTargetEnvironmentId } from "../../reducers/selectors/settings";
import type { ItemActivity } from "../toolbars/activities/ActivitiesPanel";
import type { ActivitiesResponse, ActivityType } from "../toolbars/activities/types";
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
import { CompareContainer, CompareModal, VersionHeader } from "./Styled";

type Environment = "local" | "remote";

const toVersionDiffsMap = (versions: VersionsWithDifferencesResponse["versions"]): Map<number, string[]> =>
    new Map(versions.map(({ versionId, changedElements }) => [versionId, changedElements]));

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
}
const LoadMoreContext = React.createContext<LoadMoreContextValue | null>(null);

const VersionMenuList = ({ children, ...props }: React.ComponentProps<typeof SelectComponents.MenuList>) => {
    const ctx = useContext(LoadMoreContext);
    return (
        <SelectComponents.MenuList {...props}>
            {children}
            {ctx?.hasMore && (
                <div
                    onMouseDown={(e) => {
                        e.preventDefault();
                        ctx.loadMore();
                    }}
                    className={css({
                        padding: "8px 12px",
                        cursor: "pointer",
                        textAlign: "center",
                        fontSize: "0.85em",
                        opacity: 0.7,
                        borderTop: "1px solid rgba(128,128,128,0.2)",
                        "&:hover": { opacity: 1, background: "rgba(128,128,128,0.1)" },
                    })}
                >
                    Load older versions…
                </div>
            )}
        </SelectComponents.MenuList>
    );
};
const VersionOption = ({ children, innerProps, ...props }: React.ComponentProps<typeof SelectComponents.Option>) => (
    <SelectComponents.Option {...props} innerProps={{ ...innerProps, title: (props.data as Option).description }}>
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
    localVersionDiffs: null,
    hasMoreLocalVersions: false,
    localVersionsNextOffset: 0,
    remoteVersionDiffs: null,
    hasMoreRemoteVersions: false,
    remoteVersionsNextOffset: 0,
    remoteActivities: null,
};

interface State {
    environment: Environment;
    currentDiffId: string;
    otherVersion: string;
    remoteVersions: ProcessVersionType[];
    difference: unknown;
    localVersionDiffs: Map<number, string[]> | null; // null = not yet loaded; versionId → changed elements
    hasMoreLocalVersions: boolean;
    localVersionsNextOffset: number;
    remoteVersionDiffs: Map<number, string[]> | null; // null = not yet loaded or error
    hasMoreRemoteVersions: boolean;
    remoteVersionsNextOffset: number;
    remoteActivities: ActivitiesResponse["activities"] | null; // null = not yet loaded or error
}

interface Props {
    predefinedOtherVersion?: string;
}
const VersionsForm = ({ predefinedOtherVersion }: Props) => {
    const remotePrefix = "remote-";

    const [state, setState] = useState<State>(initState);
    const processName = useSelector(getProcessName);
    const version = useSelector(getProcessVersionId);
    const otherEnvironment = useSelector(getTargetEnvironmentId);
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

    const remoteVersionDiffsLoaded = state.remoteVersionDiffs !== null;

    useEffect(() => {
        // Deferred until the (connection-pool constrained, compare-heavy) diffs pagination has produced its
        // first page - comments are supplementary (tooltips only), so they shouldn't compete for the remote
        // environment's limited connection pool and delay the version dropdown from becoming usable.
        if (processName && otherEnvironment && remoteVersionDiffsLoaded) {
            HttpService.fetchRemoteActivities(processName).then((result) =>
                setState((prevState) => ({ ...prevState, remoteActivities: result?.activities ?? null })),
            );
        }
    }, [processName, otherEnvironment, remoteVersionDiffsLoaded]);

    useEffect(() => {
        if (processName && version && otherEnvironment) {
            const fetchFrom = (offset: number) => {
                HttpService.fetchRemoteVersionsWithDifferences(processName, version, offset).then((result) => {
                    if (result === null) return;
                    setState((prev) => ({
                        ...prev,
                        remoteVersionDiffs: new Map([...(prev.remoteVersionDiffs ?? []), ...toVersionDiffsMap(result.versions)]),
                        hasMoreRemoteVersions: result.hasMore,
                        remoteVersionsNextOffset: offset + result.pageSize,
                    }));
                    if (result.versions.length === 0 && result.hasMore) {
                        fetchFrom(offset + result.pageSize);
                    }
                });
            };
            fetchFrom(0);
        }
    }, [processName, version, otherEnvironment]);

    useEffect(() => {
        if (processName && version) {
            const fetchFrom = (offset: number) => {
                HttpService.fetchVersionsWithDifferences(processName, version, offset).then((response) => {
                    const { versions, hasMore, pageSize } = response.data;
                    setState((prev) => ({
                        ...prev,
                        localVersionDiffs: new Map([...(prev.localVersionDiffs ?? []), ...toVersionDiffsMap(versions)]),
                        hasMoreLocalVersions: hasMore,
                        localVersionsNextOffset: offset + pageSize,
                    }));
                    if (versions.length === 0 && hasMore) {
                        fetchFrom(offset + pageSize);
                    }
                });
            };
            fetchFrom(0);
        }
    }, [processName, version]);

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
            const commentSuffix = comment ? ` (${comment})` : "";
            return `${versionDisplayString(versionId)} - ${formatAbsolutely(version.createDate)} ${version.user}${commentSuffix}`;
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
            const remoteDiffs = state.remoteVersionDiffs;
            if (remoteDiffs === null) return [{ label: "", value: "" }];
            const filtered = (state?.remoteVersions ?? []).filter((v) => remoteDiffs.has(v.processVersionId));
            return [
                { label: "", value: "" },
                ...filtered.map((v) => {
                    const changedElements = remoteDiffs.get(v.processVersionId) ?? [];
                    return {
                        label: createVersionElement(v, remotePrefix),
                        value: createVersionId(v, remotePrefix),
                        description: changedElements.join("\n"),
                    };
                }),
            ];
        }
        const localDiffs = state.localVersionDiffs;
        if (localDiffs === null) return [{ label: "", value: "" }];
        return [
            { label: "", value: "" },
            ...versions
                .filter((v) => version !== v.processVersionId && localDiffs.has(v.processVersionId))
                .map((v) => {
                    const changedElements = localDiffs.get(v.processVersionId) ?? [];
                    return {
                        label: createVersionElement(v),
                        value: createVersionId(v),
                        description: changedElements.join("\n"),
                    };
                }),
        ];
    }, [createVersionElement, state.environment, state?.remoteVersions, state.remoteVersionDiffs, state.localVersionDiffs, version, versions]);

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

    const loadMoreLocalVersions = useCallback(() => {
        if (!processName || !version) return;
        const fetchFrom = (offset: number) => {
            HttpService.fetchVersionsWithDifferences(processName, version, offset).then((response) => {
                const { versions, hasMore, pageSize } = response.data;
                setState((prev) => ({
                    ...prev,
                    localVersionDiffs: new Map([...(prev.localVersionDiffs ?? []), ...toVersionDiffsMap(versions)]),
                    hasMoreLocalVersions: hasMore,
                    localVersionsNextOffset: offset + pageSize,
                }));
                if (versions.length === 0 && hasMore) {
                    fetchFrom(offset + pageSize);
                }
            });
        };
        fetchFrom(state.localVersionsNextOffset);
    }, [processName, version, state.localVersionsNextOffset]);

    const loadMoreRemoteVersions = useCallback(() => {
        if (!processName || !version) return;
        const fetchFrom = (offset: number) => {
            HttpService.fetchRemoteVersionsWithDifferences(processName, version, offset).then((result) => {
                if (result === null) return;
                setState((prev) => ({
                    ...prev,
                    remoteVersionDiffs: new Map([...(prev.remoteVersionDiffs ?? []), ...toVersionDiffsMap(result.versions)]),
                    hasMoreRemoteVersions: result.hasMore,
                    remoteVersionsNextOffset: offset + result.pageSize,
                }));
                if (result.versions.length === 0 && result.hasMore) {
                    fetchFrom(offset + result.pageSize);
                }
            });
        };
        fetchFrom(state.remoteVersionsNextOffset);
    }, [processName, version, state.remoteVersionsNextOffset]);

    const handleEnvironmentChange = useCallback(
        (env: string) => {
            setState((prev) => ({
                ...prev,
                environment: env as Environment,
                otherVersion: null,
                currentDiffId: null,
                difference: null,
            }));
        },
        [],
    );

    const loadMoreContextValue = useMemo<LoadMoreContextValue>(
        () => ({
            hasMore: state.environment === "local" ? state.hasMoreLocalVersions : state.hasMoreRemoteVersions,
            loadMore: state.environment === "local" ? loadMoreLocalVersions : loadMoreRemoteVersions,
        }),
        [state.environment, state.hasMoreLocalVersions, state.hasMoreRemoteVersions, loadMoreLocalVersions, loadMoreRemoteVersions],
    );

    const environmentOptions: Option[] = useMemo(() => {
        if (!otherEnvironment) return [];
        return [
            { label: "Local", value: "local" },
            { label: otherEnvironment, value: "remote" },
        ];
    }, [otherEnvironment]);

    return (
        <>
            {otherEnvironment && (
                <FormControl>
                    <FormLabel>Environment</FormLabel>
                    <TypeSelect
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
