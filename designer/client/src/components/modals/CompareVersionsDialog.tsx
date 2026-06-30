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
import { getProcessName, getProcessVersionId, getVersions } from "../../reducers/selectors/graph";
import { getTargetEnvironmentId } from "../../reducers/selectors/settings";
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
const VERSION_MENU_COMPONENTS = { MenuList: VersionMenuList };


const initState: State = {
    environment: "local",
    otherVersion: null,
    currentDiffId: null,
    difference: null,
    remoteVersions: [],
    versionIdsWithDifferences: null,
    hasMoreLocalVersions: false,
    localVersionsNextOffset: 0,
    remoteVersionIdsWithDifferences: null,
    hasMoreRemoteVersions: false,
    remoteVersionsNextOffset: 0,
};

interface State {
    environment: Environment;
    currentDiffId: string;
    otherVersion: string;
    remoteVersions: ProcessVersionType[];
    difference: unknown;
    versionIdsWithDifferences: Set<number> | null; // null = not yet loaded
    hasMoreLocalVersions: boolean;
    localVersionsNextOffset: number;
    remoteVersionIdsWithDifferences: Set<number> | null; // null = not yet loaded or error
    hasMoreRemoteVersions: boolean;
    remoteVersionsNextOffset: number;
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

    useEffect(() => {
        if (processName && otherEnvironment) {
            HttpService.fetchRemoteVersions(processName).then((response) =>
                setState((prevState) => ({ ...prevState, remoteVersions: response.data || [] })),
            );
        }
    }, [processName, otherEnvironment]);

    useEffect(() => {
        if (processName && version && otherEnvironment) {
            HttpService.fetchRemoteVersionsWithDifferences(processName, version, 0).then((result) => {
                setState((prevState) => ({
                    ...prevState,
                    remoteVersionIdsWithDifferences: result !== null ? new Set(result.versionIds) : null,
                    hasMoreRemoteVersions: result?.hasMore ?? false,
                    remoteVersionsNextOffset: result?.pageSize ?? 0,
                }));
            });
        }
    }, [processName, version, otherEnvironment]);

    useEffect(() => {
        if (processName && version) {
            HttpService.fetchVersionsWithDifferences(processName, version, 0).then((response) => {
                const { versionIds, hasMore, pageSize } = response.data;
                setState((prevState) => ({
                    ...prevState,
                    versionIdsWithDifferences: new Set(versionIds),
                    hasMoreLocalVersions: hasMore,
                    localVersionsNextOffset: pageSize,
                }));
            });
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
            return `${versionDisplayString(versionId)} - created by ${version.user} ${formatAbsolutely(version.createDate)}`;
        },
        [versionDisplayString],
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
            const remoteIds = state.remoteVersionIdsWithDifferences;
            if (remoteIds === null) return [{ label: "", value: "" }];
            const filtered = (state?.remoteVersions ?? []).filter((v) => remoteIds.has(v.processVersionId));
            return [
                { label: "", value: "" },
                ...filtered.map((v) => ({
                    label: createVersionElement(v, remotePrefix),
                    value: createVersionId(v, remotePrefix),
                })),
            ];
        }
        const localIds = state.versionIdsWithDifferences;
        if (localIds === null) return [{ label: "", value: "" }];
        return [
            { label: "", value: "" },
            ...versions
                .filter((v) => version !== v.processVersionId && localIds.has(v.processVersionId))
                .map((v) => ({ label: createVersionElement(v), value: createVersionId(v) })),
        ];
    }, [createVersionElement, state.environment, state?.remoteVersions, state.remoteVersionIdsWithDifferences, state.versionIdsWithDifferences, version, versions]);

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
        HttpService.fetchVersionsWithDifferences(processName, version, state.localVersionsNextOffset).then((response) => {
            const { versionIds, hasMore, pageSize } = response.data;
            setState((prev) => ({
                ...prev,
                versionIdsWithDifferences: new Set([...(prev.versionIdsWithDifferences ?? []), ...versionIds]),
                hasMoreLocalVersions: hasMore,
                localVersionsNextOffset: prev.localVersionsNextOffset + pageSize,
            }));
        });
    }, [processName, version, state.localVersionsNextOffset]);

    const loadMoreRemoteVersions = useCallback(() => {
        if (!processName || !version) return;
        HttpService.fetchRemoteVersionsWithDifferences(processName, version, state.remoteVersionsNextOffset).then((result) => {
            if (result === null) return;
            setState((prev) => ({
                ...prev,
                remoteVersionIdsWithDifferences: new Set([...(prev.remoteVersionIdsWithDifferences ?? []), ...result.versionIds]),
                hasMoreRemoteVersions: result.hasMore,
                remoteVersionsNextOffset: prev.remoteVersionsNextOffset + result.pageSize,
            }));
        });
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
