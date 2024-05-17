import React, { useCallback, useMemo } from "react";
import { Scrollbars } from "react-custom-scrollbars";
import { useDispatch, useSelector } from "react-redux";
import { displayScenarioVersion } from "../../actions/nk";
import { unsavedProcessChanges } from "../../common/DialogMessages";
import { getScenario, isSaveDisabled } from "../../reducers/selectors/graph";
import { useWindows } from "../../windowManager";
import { HistoryItem, VersionType } from "./HistoryItem";
import { ProcessVersionType } from "../Process/types";
import { ProcessHistoryWrapper, TrackVertical } from "./StyledHistory";
import { EventTrackingSelector, EventTrackingType, getEventTrackingProps } from "../../containers/event-tracking";

export function ProcessHistoryComponent(props: { isReadOnly?: boolean }): JSX.Element {
    const scenario = useSelector(getScenario);
    const { history = [], lastDeployedAction, name, processVersionId } = scenario || {};
    const nothingToSave = useSelector(isSaveDisabled);
    const selectedVersion = useMemo(() => history.find((v) => v.processVersionId === processVersionId), [history, processVersionId]);

    const dispatch = useDispatch();

    const doChangeVersion = useCallback(
        (version: ProcessVersionType) => {
            dispatch(displayScenarioVersion(name, version.processVersionId));
        },
        [dispatch, name],
    );

    const { confirm } = useWindows();

    const changeVersion = useCallback(
        (version: ProcessVersionType) =>
            props.isReadOnly || nothingToSave
                ? doChangeVersion(version)
                : confirm({
                      text: unsavedProcessChanges(),
                      onConfirmCallback: (confirmed) => confirmed && doChangeVersion(version),
                      confirmText: "DISCARD",
                      denyText: "CANCEL",
                  }),
        [confirm, doChangeVersion, nothingToSave, props.isReadOnly],
    );

    return (
        <Scrollbars
            renderTrackVertical={(props) => <TrackVertical {...props} />}
            renderTrackHorizontal={() => <div className="hide" />}
            autoHeight
            autoHeightMax={300}
            hideTracksWhenNotNeeded={true}
        >
            <ProcessHistoryWrapper id="process-history">
                {history.map((version, index) => {
                    const isLatest = index === 0;
                    const { createDate, processVersionId } = version;
                    const type =
                        selectedVersion?.createDate < createDate
                            ? VersionType.future
                            : selectedVersion?.createDate === createDate || isLatest
                            ? VersionType.current
                            : VersionType.past;

                    return (
                        <HistoryItem
                            key={processVersionId}
                            isLatest={isLatest}
                            isDeployed={processVersionId === lastDeployedAction?.processVersionId}
                            version={version}
                            type={type}
                            onClick={changeVersion}
                            {...getEventTrackingProps({
                                selector: isLatest ? EventTrackingSelector.NewerVersion : EventTrackingSelector.OlderVersion,
                                event: EventTrackingType.CLICK,
                            })}
                        />
                    );
                })}
            </ProcessHistoryWrapper>
        </Scrollbars>
    );
}

export default ProcessHistoryComponent;
