import React, { PropsWithChildren, useCallback, useMemo } from "react";
import { Button, styled, Typography } from "@mui/material";
import { SearchHighlighter } from "../../creator/SearchHighlighter";
import HttpService from "../../../../http/HttpService";
import { ActionMetadata, ActivityAttachment, ActivityComment, ActivityType } from "../types";
import UrlIcon from "../../../UrlIcon";
import { unsavedProcessChanges } from "../../../../common/DialogMessages";
import { useDispatch, useSelector } from "react-redux";
import { getProcessName, getProcessVersionId, getScenario, isSaveDisabled } from "../../../../reducers/selectors/graph";
import { useWindows } from "../../../../windowManager";
import { displayScenarioVersion } from "../../../../actions/nk";
import { ItemActivity } from "../ActivitiesPanel";
import { handleOpenCompareVersionDialog } from "../../../modals/CompareVersionsDialog";
import { getHeaderColors } from "../helpers/activityItemColors";
import { useTranslation } from "react-i18next";
import * as DialogMessages from "../../../../common/DialogMessages";
import { StyledActionIcon } from "./StyledActionIcon";
import { getScenarioActivities } from "../../../../actions/nk/scenarioActivities";
import { ActivityItemCommentModify } from "./ActivityItemCommentModify";
import { getLoggedUser } from "../../../../reducers/selectors/settings";
import { getCapabilities } from "../../../../reducers/selectors/other";
import { EventTrackingSelector, getEventTrackingProps } from "../../../../containers/event-tracking";

const StyledHeaderIcon = styled(UrlIcon)(({ theme }) => ({
    width: "16px",
    height: "16px",
    color: theme.palette.primary.main,
    svg: {
        width: "16px",
        height: "16px",
    },
}));

const StyledHeaderActionRoot = styled("div")(({ theme }) => ({
    display: "flex",
    marginLeft: "auto",
    gap: theme.spacing(0.5),
}));

const StyledActivityItemHeader = styled("div")<{ isHighlighted: boolean; isRunning: boolean; isActiveFound: boolean }>(
    ({ theme, isHighlighted, isRunning, isActiveFound }) => ({
        display: "flex",
        alignItems: "center",
        padding: theme.spacing(0.5, 0.5, 0.5, 0.75),
        borderRadius: theme.spacing(0.5),
        ...getHeaderColors(theme, isHighlighted, isRunning, isActiveFound),
    }),
);

const HeaderActivity = ({
    activityAction,
    scenarioVersionId,
    activityAttachment,
    activityComment,
    scenarioActivityId,
    activityType,
}: {
    activityAction: ActionMetadata;
    scenarioVersionId: number;
    activityAttachment: ActivityAttachment;
    activityComment: ActivityComment;
    scenarioActivityId: string;
    activityType: ActivityType;
}) => {
    const { open, confirm } = useWindows();
    const processName = useSelector(getProcessName);
    const currentScenarioVersionId = useSelector(getProcessVersionId);
    const { t } = useTranslation();
    const dispatch = useDispatch();
    const loggedUser = useSelector(getLoggedUser);
    const { write } = useSelector(getCapabilities);

    switch (activityAction.id) {
        case "compare": {
            const isCurrentVersionIsTheSameAsVersionFromActivity = currentScenarioVersionId === scenarioVersionId;
            if (isCurrentVersionIsTheSameAsVersionFromActivity) {
                return null;
            }

            return (
                <StyledActionIcon
                    title={activityAction.displayableName}
                    data-testid={`compare-${scenarioVersionId}`}
                    onClick={() => open(handleOpenCompareVersionDialog(scenarioVersionId.toString()))}
                    key={activityAction.id}
                    src={activityAction.icon}
                    {...getEventTrackingProps({ selector: EventTrackingSelector.ScenarioActivitiesCompare })}
                />
            );
        }
        case "download_attachment": {
            const attachmentStatus = activityAttachment.file.status;

            if (attachmentStatus === "DELETED") {
                return null;
            }

            const attachmentId = attachmentStatus && activityAttachment.file.id;
            const attachmentName = activityAttachment.filename;

            const handleDownloadAttachment = () => HttpService.downloadAttachment(processName, attachmentId.toString(), attachmentName);
            return (
                <StyledActionIcon
                    onClick={handleDownloadAttachment}
                    key={attachmentId}
                    src={activityAction.icon}
                    title={activityAction.displayableName}
                    {...getEventTrackingProps({ selector: EventTrackingSelector.ScenarioActivitiesDownloadAttachment })}
                />
            );
        }
        case "delete_attachment": {
            const attachmentStatus = activityAttachment.file.status;

            if (attachmentStatus === "DELETED" || activityAttachment.lastModifiedBy !== loggedUser.id || !write) {
                return null;
            }

            const attachmentId = activityAttachment.file.id;

            return (
                <StyledActionIcon
                    title={activityAction.displayableName}
                    src={activityAction.icon}
                    onClick={() =>
                        confirm({
                            text: DialogMessages.deleteAttachment(activityAttachment.filename),
                            onConfirmCallback: (confirmed) => {
                                confirmed &&
                                    HttpService.deleteAttachment(processName, attachmentId.toString()).then(({ status }) => {
                                        if (status === "success") {
                                            dispatch(getScenarioActivities(processName));
                                        }
                                    });
                            },
                            confirmText: t("panels.actions.process-unarchive.yes", "Yes"),
                            denyText: t("panels.actions.process-unarchive.no", "No"),
                        })
                    }
                    {...getEventTrackingProps({ selector: EventTrackingSelector.ScenarioActivitiesDeleteAttachment })}
                />
            );
        }

        case "add_comment": {
            if (activityComment.content.status === "AVAILABLE" || activityComment.lastModifiedBy !== loggedUser.id || !write) {
                return null;
            }

            return (
                <ActivityItemCommentModify
                    commentContent={activityComment.content}
                    scenarioActivityId={scenarioActivityId}
                    activityType={activityType}
                    activityAction={activityAction}
                    title={t("panels.actions.addComment.title", "Add comment")}
                    confirmButtonText={t("panels.actions.addComment.confirmButton", "Add")}
                    {...getEventTrackingProps({ selector: EventTrackingSelector.ScenarioActivitiesAddCommentToActivity })}
                />
            );
        }
        default: {
            return null;
        }
    }
};

interface Props {
    activity: ItemActivity;
    isRunning: boolean;
    isActiveFound: boolean;
    isFound: boolean;
    searchQuery: string;
}

const WithOpenVersion = ({
    scenarioVersion,
    isFound,
    children,
    activityType,
}: PropsWithChildren<{
    scenarioVersion: number;
    isFound: boolean;
    activityType: ActivityType;
}>) => {
    const nothingToSave = useSelector(isSaveDisabled);
    const scenario = useSelector(getScenario);
    const { name } = scenario || {};
    const dispatch = useDispatch();
    const { confirm } = useWindows();

    const doChangeVersion = useCallback(
        (scenarioId: number) => {
            dispatch(displayScenarioVersion(name, scenarioId));
        },
        [dispatch, name],
    );

    const changeVersion = useCallback(
        (scenarioId: number) =>
            nothingToSave
                ? doChangeVersion(scenarioId)
                : confirm({
                      text: unsavedProcessChanges(),
                      onConfirmCallback: (confirmed) => confirmed && doChangeVersion(scenarioId),
                      confirmText: "DISCARD",
                      denyText: "CANCEL",
                  }),
        [confirm, doChangeVersion, nothingToSave],
    );

    return (
        <Button
            sx={(theme) => ({
                textTransform: "initial",
                "&:hover": { backgroundColor: activityType === "SCENARIO_DEPLOYED" || isFound ? "unset" : theme.palette.action.hover },
                "&:focus": { outline: (activityType === "SCENARIO_DEPLOYED" || isFound) && "unset" },
                width: "100%",
                justifyContent: "flex-start",
                m: theme.spacing(0, 0.5),
                p: theme.spacing(0, 0.5),
            })}
            onClick={() => {
                changeVersion(scenarioVersion);
            }}
            {...getEventTrackingProps({ selector: EventTrackingSelector.ScenarioActivitiesOpenVersion })}
        >
            {children}
        </Button>
    );
};

const ActivityItemHeader = ({ activity, isRunning, isFound, isActiveFound, searchQuery }: Props) => {
    const scenario = useSelector(getScenario);
    const { processVersionId } = scenario || {};

    const isHighlighted = ["SCENARIO_DEPLOYED", "SCENARIO_CANCELED"].includes(activity.type);
    const openVersionEnable =
        ["SCENARIO_MODIFIED", "SCENARIO_DEPLOYED"].includes(activity.type) && activity.scenarioVersionId !== processVersionId;

    const getHeaderTitle = useMemo(() => {
        const text = activity.overrideDisplayableName || activity.activities.displayableName;

        const headerTitle = (
            <Typography
                variant={"caption"}
                component={SearchHighlighter}
                title={text}
                highlights={[searchQuery]}
                sx={(theme) => ({
                    color: theme.palette.text.primary,
                    overflow: "hidden",
                    textOverflow: "ellipsis",
                    textWrap: "noWrap",
                    padding: !openVersionEnable && theme.spacing(0, 1),
                })}
            >
                {text}
            </Typography>
        );

        if (openVersionEnable) {
            return (
                <WithOpenVersion scenarioVersion={activity.scenarioVersionId} isFound={isFound} activityType={activity.type}>
                    {headerTitle}
                </WithOpenVersion>
            );
        }

        return headerTitle;
    }, [
        activity.activities.displayableName,
        activity.overrideDisplayableName,
        activity.scenarioVersionId,
        activity.type,
        isFound,
        openVersionEnable,
        searchQuery,
    ]);

    return (
        <StyledActivityItemHeader isHighlighted={isHighlighted} isRunning={isRunning} isActiveFound={isActiveFound}>
            <StyledHeaderIcon src={activity.activities.icon} id={activity.uiGeneratedId} />
            {getHeaderTitle}
            <StyledHeaderActionRoot>
                {activity.actions.map((activityAction) => (
                    <HeaderActivity
                        key={activityAction.id}
                        activityAction={activityAction}
                        scenarioVersionId={activity.scenarioVersionId}
                        activityAttachment={activity.attachment}
                        activityComment={activity.comment}
                        activityType={activity.type}
                        scenarioActivityId={activity.id}
                    />
                ))}
            </StyledHeaderActionRoot>
        </StyledActivityItemHeader>
    );
};

export default ActivityItemHeader;
