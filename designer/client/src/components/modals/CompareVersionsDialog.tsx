/* eslint-disable i18next/no-literal-string */
import { css, cx } from "@emotion/css";
import type { WindowButtonProps, WindowContentProps, WindowType } from "@touk/window-manager";
import i18next from "i18next";
import { keys } from "lodash";
import React, { useCallback, useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";

import Icon from "../../assets/img/toolbarButtons/compare.svg";
import { formatAbsolutely } from "../../common/DateUtils";
import { flattenObj, objectDiff } from "../../common/JsonUtils";
import HttpService from "../../http/HttpService/instance";
import { getProcessName, getProcessVersionId, getVersions } from "../../reducers/selectors/graph";
import { getTargetEnvironmentId } from "../../reducers/selectors/settings";
import { useAppSelector } from "../../store/storeHelpers";
import type { NodeType, StickyNoteNodeType } from "../../types/node";
import { WindowContent } from "../../windowManager/WindowContent";
import { WindowKind } from "../../windowManager/WindowKind";
import EdgeDetailsContent from "../graph/node-modal/edge/EdgeDetailsContent";
import type { Option } from "../graph/node-modal/fragment-input-definition/TypeSelect";
import { TypeSelect } from "../graph/node-modal/fragment-input-definition/TypeSelect";
import { NodeRow } from "../graph/node-modal/node/NodeRow";
import { WindowHeaderIconStyled } from "../graph/node-modal/nodeDetails/NodeDetailsStyled";
import { NodeDetailsContent } from "../graph/node-modal/NodeDetailsContent";
import { PathsToMarkProvider } from "../graph/node-modal/PathsToMark";
import { StickyNoteType } from "../graph/utils/stickyNotesUtils";
import type { ProcessVersionType } from "../Process/types";
import { PropertiesForm } from "../properties/PropertiesForm";
import { CompareContainer, CompareModal, VersionHeader } from "./Styled";

const initState: State = {
    otherVersion: null,
    currentDiffId: null,
    difference: null,
    remoteVersions: [],
};

interface State {
    currentDiffId: string;
    otherVersion: string;
    remoteVersions: ProcessVersionType[];
    difference: unknown;
}

interface Props {
    predefinedOtherVersion?: string;
}
const VersionsForm = ({ predefinedOtherVersion }: Props) => {
    const remotePrefix = "remote-";

    const [state, setState] = useState<State>(initState);
    const processName = useAppSelector(getProcessName);
    const version = useAppSelector(getProcessVersionId);
    const otherEnvironment = useAppSelector(getTargetEnvironmentId);
    const versions = useAppSelector(getVersions);

    useEffect(() => {
        if (processName && otherEnvironment) {
            HttpService.fetchRemoteVersions(processName).then((response) =>
                setState((prevState) => ({ ...prevState, remoteVersions: response.data || [] })),
            );
        }
    }, [processName, otherEnvironment]);

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
                setState(initState);
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
        return property ? (
            <PropertiesForm editedProperties={property} isValidating={false} />
        ) : (
            <div className="notPresent">Properties not present</div>
        );
    };

    const versionOptions: Option[] = useMemo(() => {
        return [
            { label: "", value: "" },
            ...versions
                .filter((currentVersion) => version !== currentVersion.processVersionId)
                .map((version) => ({ label: createVersionElement(version), value: createVersionId(version) })),
            ...(state?.remoteVersions ?? []).map((version) => ({
                label: createVersionElement(version, remotePrefix),
                value: createVersionId(version, remotePrefix),
            })),
        ];
    }, [createVersionElement, state?.remoteVersions, version, versions]);

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

    return (
        <>
            <NodeRow label="Version to compare">
                <TypeSelect
                    readOnly={Boolean(predefinedOtherVersion)}
                    autoFocus={true}
                    id="otherVersion"
                    onChange={(value) => loadVersion(value)}
                    value={versionOptions.find((option) => option.value === state.otherVersion)}
                    options={versionOptions}
                    fieldErrors={[]}
                    sx={{ flex: 1 }}
                />
            </NodeRow>
            {state.otherVersion ? (
                <>
                    <NodeRow label="Difference to pick">
                        <TypeSelect
                            id="differentVersion"
                            onChange={(value) => setState({ ...state, currentDiffId: value })}
                            value={differenceOptions.find((option) => option.value === state.currentDiffId)}
                            options={differenceOptions}
                            fieldErrors={[]}
                            sx={{ flex: 1 }}
                        />
                    </NodeRow>
                    {state.currentDiffId ? printDiff(state.currentDiffId) : null}
                </>
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
