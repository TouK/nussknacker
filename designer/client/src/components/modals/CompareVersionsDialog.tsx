/* eslint-disable i18next/no-literal-string */
import { css, cx } from "@emotion/css";
import { WindowContentProps } from "@touk/window-manager";
import { keys } from "lodash";
import React, { useEffect, useState } from "react";
import { useSelector } from "react-redux";
import { WindowContent } from "../../windowManager";
import { formatAbsolutely } from "../../common/DateUtils";
import { flattenObj, objectDiff } from "../../common/JsonUtils";
import HttpService from "../../http/HttpService";
import { getProcessName, getProcessVersionId, getVersions } from "../../reducers/selectors/graph";
import { getTargetEnvironmentId } from "../../reducers/selectors/settings";
import EdgeDetailsContent from "../graph/node-modal/edge/EdgeDetailsContent";
import { ProcessVersionType } from "../Process/types";
import { SelectNodeWithFocus } from "../withFocus";
import { NodeDetailsContent } from "../graph/node-modal/NodeDetailsContent";
import { PathsToMarkProvider } from "../graph/node-modal/PathsToMark";
import { NodeType } from "../../types";
import { CompareContainer, CompareModal, VersionHeader } from "./Styled";
import { FormControl, FormLabel } from "@mui/material";

interface State {
    currentDiffId: string;
    otherVersion: string;
    remoteVersions: ProcessVersionType[];
    difference: unknown;
}

const VersionsForm = () => {
    const remotePrefix = "remote-";
    const initState: State = {
        otherVersion: null,
        currentDiffId: null,
        difference: null,
        remoteVersions: [],
    };

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

    function isLayoutChangeOnly(diffId: string): boolean {
        const { type, currentNode, otherNode } = state.difference[diffId];
        if (type === "NodeDifferent") {
            return differentPathsForObjects(currentNode, otherNode).every((path) => path.startsWith("additionalFields.layoutData"));
        }
    }

    const loadVersion = (versionId: string) => {
        if (versionId) {
            HttpService.compareProcesses(processName, version, versionToPass(versionId), isRemote(versionId)).then((response) =>
                setState((prevState) => ({ ...prevState, difference: response.data, otherVersion: versionId, currentDiffId: null })),
            );
        } else {
            setState(initState);
        }
    };

    const isRemote = (versionId: string) => {
        return versionId.startsWith(remotePrefix);
    };

    const versionToPass = (versionId: string) => {
        return versionId.replace(remotePrefix, "");
    };

    const versionDisplayString = (versionId: string) => {
        return isRemote(versionId) ? `${versionToPass(versionId)} on ${otherEnvironment}` : versionId;
    };

    const createVersionElement = (version: ProcessVersionType, versionPrefix = "") => {
        const versionId = versionPrefix + version.processVersionId;
        return (
            <option key={versionId} value={versionId}>
                {versionDisplayString(versionId)} - created by {version.user} &nbsp; {formatAbsolutely(version.createDate)}
            </option>
        );
    };

    const printDiff = (diffId: string) => {
        const diff = state.difference[diffId];

        switch (diff.type) {
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
        return node ? <NodeDetailsContent node={node} /> : <div className="notPresent">Node not present</div>;
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
        return property ? <NodeDetailsContent node={property} /> : <div className="notPresent">Properties not present</div>;
    };

    return (
        <>
            <FormControl>
                <FormLabel>Version to compare</FormLabel>
                <SelectNodeWithFocus
                    autoFocus={true}
                    id="otherVersion"
                    className="selectNode"
                    value={state.otherVersion || ""}
                    onChange={(e) => loadVersion(e.target.value)}
                >
                    <option key="" value="" />
                    {versions
                        .filter((currentVersion) => version !== currentVersion.processVersionId)
                        .map((version) => createVersionElement(version))}
                    {state.remoteVersions.map((version) => createVersionElement(version, remotePrefix))}
                </SelectNodeWithFocus>
            </FormControl>
            {state.otherVersion ? (
                <div>
                    <FormControl>
                        <FormLabel>Difference to pick</FormLabel>
                        <SelectNodeWithFocus
                            id="differentVersion"
                            className="selectNode"
                            value={state.currentDiffId || ""}
                            onChange={(e) => setState({ ...state, currentDiffId: e.target.value })}
                        >
                            <option key="" value="" />
                            {keys(state.difference).map((diffId) => {
                                const isLayoutOnly = isLayoutChangeOnly(diffId);
                                return (
                                    <option key={diffId} value={diffId} disabled={isLayoutOnly}>
                                        {diffId} {isLayoutOnly && "(position only)"}
                                    </option>
                                );
                            })}
                        </SelectNodeWithFocus>
                    </FormControl>
                    {state.currentDiffId ? printDiff(state.currentDiffId) : null}
                </div>
            ) : null}
        </>
    );
};

const CompareVersionsDialog = (props: WindowContentProps) => {
    return (
        <WindowContent {...props}>
            <CompareModal className={cx("modalContentDark", css({ minWidth: 980, padding: "1em" }))}>
                <VersionsForm />
            </CompareModal>
        </WindowContent>
    );
};

export default CompareVersionsDialog;
