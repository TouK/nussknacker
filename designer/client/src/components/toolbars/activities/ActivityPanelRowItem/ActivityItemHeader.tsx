import CircleIcon from "@mui/icons-material/Circle";
import { Button, styled, Typography } from "@mui/material";
import type { PropsWithChildren } from "react";
import React, { useCallback, useMemo } from "react";
import { useTranslation } from "react-i18next";

import { displayScenarioVersion } from "../../../../actions/nk/process";
import { getScenarioActivities } from "../../../../actions/nk/scenarioActivities";
import DialogMessages from "../../../../common/DialogMessages";
import { getEventTrackingProps } from "../../../../containers/event-tracking/helpers";
import { EventTrackingSelector } from "../../../../containers/event-tracking/use-register-tracking-events";
import HttpService from "../../../../http/HttpService/instance";
import { getProcessName, getProcessVersionId, getScenario, isPristine } from "../../../../reducers/selectors/graph";
import { getCapabilities } from "../../../../reducers/selectors/other";
import { getLoggedUser } from "../../../../reducers/selectors/settings";
import { useAppDispatch, useAppSelector } from "../../../../store/storeHelpers";
import { useWindows } from "../../../../windowManager/useWindows";
import { InfoTooltip } from "../../../graph/node-modal/editors/InfoTooltip/InfoTooltip";
import { useUnsavedChangesPrompt } from "../../../useUnsavedChangesPrompt";
import { handleOpenCompareVersionDialog } from "../../../modals/CompareVersionsDialog";
import UrlIcon from "../../../UrlIcon";
import { SearchHighlighter } from "../../creator/SearchHighlighter";
import type { ItemActivity } from "../ActivitiesPanel";
import { getHeaderColors } from "../helpers/activityItemColors";
import type { ActionMetadata, ActivityAttachment, ActivityComment, ActivityType } from "../types";
import { ActivityItemCommentModify } from "./ActivityItemCommentModify";
import { StyledActionIcon } from "./StyledActionIcon";

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

const StyledActivityItemHeader = styled("div")<{
    isHighlighted: boolean;
    isDeploymentActive: boolean;
    isActiveFound: boolean;
    isVersionSelected: boolean;
}>(({ theme, isHighlighted, isDeploymentActive, isActiveFound, isVersionSelected }) => ({
    display: "flex",
    alignItems: "center",
    padding: theme.spacing(0.5, 0.5, 0.5, 0.75),
    borderRadius: theme.spacing(0.5),
    ...getHeaderColors(theme, isHighlighted, isDeploymentActive, isActiveFound, isVersionSelected),
}));

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
    const processName = useAppSelector(getProcessName);
    const currentScenarioVersionId = useAppSelector(getProcessVersionId);
    const { t } = useTranslation();
    const dispatch = useAppDispatch();
    const loggedUser = useAppSelector(getLoggedUser);
    const { write } = useAppSelector(getCapabilities);

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
    isDeploymentActive: boolean;
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
    const nothingToSave = useAppSelector(isPristine);
    const scenario = useAppSelector(getScenario);
    const { name } = scenario || {};
    const dispatch = useAppDispatch();
    const { promptOrProceed } = useUnsavedChangesPrompt();

    const doChangeVersion = useCallback(
        (scenarioId: number) => {
            dispatch(displayScenarioVersion(name, scenarioId));
        },
        [dispatch, name],
    );

    const changeVersion = useCallback(
        (scenarioId: number) => {
            if (nothingToSave) return doChangeVersion(scenarioId);
            promptOrProceed(() => doChangeVersion(scenarioId));
        },
        [doChangeVersion, nothingToSave, promptOrProceed],
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
            title={"Switch to version " + scenarioVersion}
            {...getEventTrackingProps({ selector: EventTrackingSelector.ScenarioActivitiesOpenVersion })}
        >
            {children}
        </Button>
    );
};

const ActivityItemHeader = ({ activity, isDeploymentActive, isFound, isActiveFound, searchQuery }: Props) => {
    const scenario = useAppSelector(getScenario);
    const { processVersionId } = scenario || {};
    const { t } = useTranslation();

    const actionsWithVersionChange: ActivityType[] = [
        "AUTOMATIC_UPDATE",
        "INCOMING_MIGRATION",
        "SCENARIO_DEPLOYED",
        "SCENARIO_REDEPLOYED",
        "SCENARIO_MODIFIED",
        "SCENARIO_MODIFIED_WITH_AUTOSAVE",
    ];

    const isHighlighted = ["SCENARIO_DEPLOYED", "SCENARIO_REDEPLOYED", "SCENARIO_CANCELED"].includes(activity.type);
    const openVersionEnable = actionsWithVersionChange.includes(activity.type) && activity.scenarioVersionId !== processVersionId;
    const isVersionSelected =
        ["AUTOMATIC_UPDATE", "INCOMING_MIGRATION", "SCENARIO_MODIFIED", "SCENARIO_MODIFIED_WITH_AUTOSAVE"].includes(activity.type) &&
        activity.scenarioVersionId === processVersionId;

    const getHeaderTitle = useMemo(() => {
        const text = activity.overrideDisplayableName || activity.activities.displayableName;

        const activeItemIndicatorText = isDeploymentActive
            ? t("activityItem.currentlyDeployedVersionText", "Currently deployed version")
            : isVersionSelected
            ? t("activityItem.currentlySelectedVersionText", "Currently selected version")
            : undefined;

        const headerTitle = (
            <>
                <Typography
                    variant={"caption"}
                    component={SearchHighlighter}
                    highlights={[searchQuery]}
                    sx={(theme) => ({
                        color: theme.palette.text.primary,
                        overflow: "hidden",
                        textOverflow: "ellipsis",
                        textWrap: "noWrap",
                        padding: !openVersionEnable && theme.spacing(0, 1),
                    })}
                    aria-label={`tool:${text}`}
                >
                    {text}
                </Typography>
                {activeItemIndicatorText && (
                    <InfoTooltip title={activeItemIndicatorText} variant={"hover"}>
                        <CircleIcon sx={{ fontSize: "10px", mx: openVersionEnable && 1 }} color={"primary"} />
                    </InfoTooltip>
                )}
            </>
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
        isDeploymentActive,
        isFound,
        isVersionSelected,
        openVersionEnable,
        searchQuery,
        t,
    ]);

    return (
        <StyledActivityItemHeader
            isHighlighted={isHighlighted}
            isDeploymentActive={isDeploymentActive}
            isActiveFound={isActiveFound}
            isVersionSelected={isVersionSelected}
        >
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
