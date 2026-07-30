/* eslint-disable i18next/no-literal-string */
import { css, cx } from "@emotion/css";
import { FormControl, FormHelperText, FormLabel } from "@mui/material";
import type { WindowButtonProps, WindowContentProps, WindowType } from "@touk/window-manager";
import i18next, { type TFunction } from "i18next";
import { keys } from "lodash";
import React, { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { useSelector } from "react-redux";
import { components as SelectComponents } from "react-select";

import Icon from "../../assets/img/toolbarButtons/compare.svg";
import { formatAbsolutely } from "../../common/DateUtils";
import { flattenObj, objectDiff } from "../../common/JsonUtils";
import HttpService from "../../http/HttpService";
import { DEFAULT_VERSIONS_COMPARED, VERSIONS_COMPARED_OPTIONS } from "../../http/HttpService";
import type { VersionWithDifference } from "../../http/HttpService";
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
import type { ActivitiesResponse } from "../toolbars/activities/types";
import { CompareContainer, CompareModal, VersionHeader } from "./Styled";

type Environment = "local" | "remote";

type VersionDiffInfo = {
    changedElements: string[];
    differencesUnknown: boolean;
    totalChangedElements?: number;
};

const MAX_DESCRIBED_CHANGES = 20;

type VersionDiffsResponse = {
    versions: VersionWithDifference[];
    oldestComparedVersionId?: number;
    versionComments?: Record<string, string>;
    remoteUnavailable?: boolean;
};

const toVersionDiffsMap = (versions: VersionWithDifference[]): Map<number, VersionDiffInfo> =>
    new Map(
        versions.map(({ versionId, changedElements, differencesUnknown, totalChangedElements }) => [
            versionId,
            { changedElements, differencesUnknown, totalChangedElements },
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
    // versions older than this were not compared; undefined means the whole history was
    oldestCompared?: number;
    // covers every version, not only the compared ones
    comments: Record<string, string>;
    error: boolean;
    unavailable: boolean;
};

const initialDiffsState: DiffsState = { diffs: null, comments: {}, error: false, unavailable: false };

const useVersionDiffs = (fetch: (() => Promise<VersionDiffsResponse | null>) | null): DiffsState => {
    const [state, setState] = useState<DiffsState>(initialDiffsState);
    // a save while the dialog is open changes `fetch`, and the response in flight for the old version must
    // not be merged into the state that reset for the new one
    const generationRef = useRef(0);

    useEffect(() => {
        const generation = ++generationRef.current;
        setState(initialDiffsState);
        if (!fetch) return;
        fetch().then((result) => {
            if (generation !== generationRef.current) return;
            setState(
                result === null
                    ? { diffs: null, comments: {}, error: true, unavailable: false }
                    : {
                          diffs: toVersionDiffsMap(result.versions),
                          oldestCompared: result.oldestComparedVersionId,
                          comments: result.versionComments ?? {},
                          error: false,
                          unavailable: Boolean(result.remoteUnavailable),
                      },
            );
        });
    }, [fetch]);

    return state;
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

const VERSION_MENU_COMPONENTS = { Option: VersionOption };

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
    const [state, setState] = useState<State>(initState);
    const processName = useSelector(getProcessName);
    const version = useSelector(getProcessVersionId);
    const otherEnvironment = useSelector(getTargetEnvironmentId);
    const { content: localEnvironmentName } = useSelector(getEnvironmentAlert);
    const versions = useSelector(getVersions);

    // Fetched here rather than read from the store: the store does not record which scenario its activities
    // belong to, and dispatching a refresh would reset the Activities panel's search and expand state.
    const [activities, setActivities] = useState<CommentableActivity[]>([]);
    useEffect(() => {
        if (!processName) return;
        let current = true;
        HttpService.fetchActivities(processName)
            .then((response) => current && setActivities(response.data.activities))
            .catch(() => current && setActivities([]));
        return () => {
            current = false;
        };
    }, [processName]);

    const versionComments = useMemo(() => toVersionCommentsMap(activities), [activities]);

    // Latched rather than tracking the current selection, so that switching back and forth between
    // environments does not discard what was already fetched and ask the remote all over again.
    const [remoteRequested, setRemoteRequested] = useState(false);
    useEffect(() => {
        if (state.environment === "remote") setRemoteRequested(true);
    }, [state.environment]);

    useEffect(() => {
        if (processName && otherEnvironment && remoteRequested) {
            HttpService.fetchRemoteVersions(processName)
                .then((response) =>
                    setState((prevState) => ({ ...prevState, remoteVersions: response.data || [], remoteVersionsFailed: false })),
                )
                .catch(() => setState((prevState) => ({ ...prevState, remoteVersions: [], remoteVersionsFailed: true })));
        }
    }, [processName, otherEnvironment, remoteRequested]);

    const [versionsCompared, setVersionsCompared] = useState(DEFAULT_VERSIONS_COMPARED);

    const fetchLocalDiffs = useMemo<(() => Promise<VersionDiffsResponse | null>) | null>(
        () =>
            processName && version
                ? () =>
                      HttpService.fetchVersionsWithDifferences(processName, version, versionsCompared)
                          .then((response) => response.data)
                          .catch(() => null)
                : null,
        [processName, version, versionsCompared],
    );
    const localDiffsState = useVersionDiffs(fetchLocalDiffs);

    const fetchRemoteDiffs = useMemo<(() => Promise<VersionDiffsResponse | null>) | null>(
        () =>
            processName && version && otherEnvironment && remoteRequested
                ? () => HttpService.fetchRemoteVersionsWithDifferences(processName, version, versionsCompared)
                : null,
        [processName, version, otherEnvironment, remoteRequested, versionsCompared],
    );
    const remoteDiffsState = useVersionDiffs(fetchRemoteDiffs);

    const {
        diffs: activeDiffs,
        oldestCompared,
        comments: remoteVersionComments,
        error: activeDiffsError,
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

    // Saving while the dialog is open makes a new version current, and the difference on screen is then
    // against a version that no longer is - recompare rather than leave it captioned "Current version".
    const comparedAgainst = useRef(version);
    useEffect(() => {
        if (comparedAgainst.current === version) return;
        comparedAgainst.current = version;
        if (state.otherVersion) {
            loadVersion(state.otherVersion);
        }
    }, [version, loadVersion, state.otherVersion]);

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
            versionPrefix ? remoteVersionComments[version.processVersionId] : versionComments.get(version.processVersionId),
        [versionComments, remoteVersionComments],
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
        const candidates = isRemoteEnvironment ? state?.remoteVersions ?? [] : versions.filter((v) => version !== v.processVersionId);

        const isSelected = (v: ProcessVersionType) => createVersionId(v, prefix) === state.otherVersion;

        const toOption = (v: ProcessVersionType): Option => ({
            label: createVersionElement(v, prefix),
            value: createVersionId(v, prefix),
            description: describeVersionDiff(activeDiffs?.get(v.processVersionId), t),
            comment: versionComment(v, prefix),
        });

        if (isLoadingVersions) return [...clearOption, ...candidates.filter(isSelected).map(toOption)];

        // A version older than the compared window was never looked at, so filtering it out would claim it
        // is identical to the current one. Those are listed as they were before this dialog filtered at all.
        const wasCompared = (v: ProcessVersionType) => oldestCompared === undefined || v.processVersionId >= oldestCompared;

        return [
            ...clearOption,
            ...candidates
                .filter((v) => showUnfilteredVersions || !wasCompared(v) || activeDiffs?.has(v.processVersionId) || isSelected(v))
                .map(toOption),
        ];
    }, [
        createVersionElement,
        versionComment,
        activeDiffs,
        oldestCompared,
        isLoadingVersions,
        showUnfilteredVersions,
        state.environment,
        state.otherVersion,
        state?.remoteVersions,
        version,
        versions,
        t,
    ]);

    const versionsComparedOptions: Option[] = useMemo(
        () => VERSIONS_COMPARED_OPTIONS.map((count) => ({ label: String(count), value: String(count) })),
        [],
    );

    // Offered once there is something it could change - either history the current setting does not reach,
    // or a setting the user has already moved off the default and may want to move back.
    const showVersionsComparedControl = oldestCompared !== undefined || versionsCompared !== DEFAULT_VERSIONS_COMPARED;

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

    const noVersionsMessage = useCallback(
        ({ inputValue }: { inputValue: string }) =>
            inputValue
                ? t("dialog.compareVersions.noVersionsMatching", "No version matches '{{query}}'", { query: inputValue })
                : t("dialog.compareVersions.noVersionsToCompare", "No other version differs from this one"),
        [t],
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
                />
                {state.environment === "remote" && (remoteDiffsState.unavailable || state.remoteVersionsFailed) && (
                    <FormHelperText error>
                        {t("dialog.compareVersions.remoteUnavailable", "Could not compare versions with the {{name}} environment", {
                            name: otherEnvironment,
                        })}
                    </FormHelperText>
                )}
                {oldestCompared !== undefined && (
                    <FormHelperText>
                        {t(
                            "dialog.compareVersions.comparedRecentOnly",
                            "Compared the {{count}} most recent versions - older ones are listed without their differences.",
                            { count: versionsCompared },
                        )}
                    </FormHelperText>
                )}
            </FormControl>
            {showVersionsComparedControl && (
                <FormControl>
                    <FormLabel>{t("dialog.compareVersions.versionsCompared", "Versions to compare in detail")}</FormLabel>
                    <TypeSelect
                        id="versionsCompared"
                        onChange={(value) => setVersionsCompared(Number(value))}
                        value={versionsComparedOptions.find((o) => o.value === String(versionsCompared))}
                        options={versionsComparedOptions}
                        fieldErrors={[]}
                        isValidNewOption={noNewVersionOptions}
                    />
                </FormControl>
            )}
            {state.otherVersion ? (
                <div>
                    <FormControl>
                        <FormLabel>Difference to pick</FormLabel>
                        <TypeSelect
                            id="differentVersion"
                            onChange={(value) => setState((prev) => ({ ...prev, currentDiffId: value }))}
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
