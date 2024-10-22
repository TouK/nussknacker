import React, { PropsWithChildren, useCallback, useMemo } from "react";
import { Button, styled, Typography } from "@mui/material";
import { SearchHighlighter } from "../../creator/SearchHighlighter";
import HttpService from "../../../../http/HttpService";
import { ActionMetadata, ActivityAttachment } from "../types";
import UrlIcon from "../../../UrlIcon";
import { unsavedProcessChanges } from "../../../../common/DialogMessages";
import { useDispatch, useSelector } from "react-redux";
import { getProcessName, getProcessVersionId, getScenario, isSaveDisabled } from "../../../../reducers/selectors/graph";
import { useWindows } from "../../../../windowManager";
import { displayScenarioVersion } from "../../../../actions/nk";
import { ItemActivity } from "../ActivitiesPanel";
import { handleOpenCompareVersionDialog } from "../../../modals/CompareVersionsDialog";
import { getHeaderColors } from "../helpers/activityItemColors";

const StyledHeaderIcon = styled(UrlIcon)(({ theme }) => ({
    width: "16px",
    height: "16px",
    marginRight: theme.spacing(1),
    color: theme.palette.primary.main,
}));

const StyledHeaderActionIcon = styled(UrlIcon)(({ theme }) => ({
    width: "1.25rem",
    height: "1.25rem",
    marginLeft: "auto",
    cursor: "pointer",
    color: theme.palette.text.secondary,
}));

const StyledActivityItemHeader = styled("div")<{ isHighlighted: boolean; isRunning: boolean; isActiveFound: boolean }>(
    ({ theme, isHighlighted, isRunning, isActiveFound }) => ({
        display: "flex",
        alignItems: "center",
        padding: theme.spacing(0.5, 0.75),
        borderRadius: theme.spacing(1),
        ...getHeaderColors(theme, isHighlighted, isRunning, isActiveFound),
    }),
);

const HeaderActivity = ({
    activityAction,
    scenarioVersionId,
    activityAttachment,
}: {
    activityAction: ActionMetadata;
    scenarioVersionId: number;
    activityAttachment: ActivityAttachment;
}) => {
    const { open } = useWindows();
    const processName = useSelector(getProcessName);
    const currentScenarioVersionId = useSelector(getProcessVersionId);

    switch (activityAction.id) {
        case "compare": {
            const isCurrentVersionIsTheSameAsVersionFromActivity = currentScenarioVersionId === scenarioVersionId;
            if (isCurrentVersionIsTheSameAsVersionFromActivity) {
                return null;
            }

            return (
                <StyledHeaderActionIcon
                    data-testid={`compare-${scenarioVersionId}`}
                    onClick={() => open(handleOpenCompareVersionDialog(scenarioVersionId.toString()))}
                    key={activityAction.id}
                    src={activityAction.icon}
                />
            );
        }
        case "download_attachment": {
            const attachmentId = activityAttachment.file.status === "AVAILABLE" && activityAttachment.file.id;
            const attachmentName = activityAttachment.filename;

            const handleDownloadAttachment = () => HttpService.downloadAttachment(processName, attachmentId, attachmentName);
            return <StyledHeaderActionIcon onClick={handleDownloadAttachment} key={attachmentId} src={activityAction.icon} />;
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
    children,
}: PropsWithChildren<{
    scenarioVersion: number;
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
            sx={{ textTransform: "initial", p: 0, m: 0 }}
            onClick={() => {
                changeVersion(scenarioVersion);
            }}
        >
            {children}
        </Button>
    );
};

const ActivityItemHeader = ({ activity, isRunning, isActiveFound, searchQuery }: Props) => {
    const scenario = useSelector(getScenario);
    const { processVersionId } = scenario || {};

    const isHighlighted = ["SCENARIO_DEPLOYED", "SCENARIO_CANCELED"].includes(activity.type);
    const openVersionEnable = activity.type === "SCENARIO_MODIFIED" && activity.scenarioVersionId !== processVersionId;

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
                    maxWidth: "75%",
                })}
            >
                {text}
            </Typography>
        );

        if (openVersionEnable) {
            return <WithOpenVersion scenarioVersion={activity.scenarioVersionId}>{headerTitle}</WithOpenVersion>;
        }

        return headerTitle;
    }, [activity.activities.displayableName, activity.overrideDisplayableName, activity.scenarioVersionId, openVersionEnable, searchQuery]);

    return (
        <StyledActivityItemHeader isHighlighted={isHighlighted} isRunning={isRunning} isActiveFound={isActiveFound}>
            <StyledHeaderIcon src={activity.activities.icon} id={activity.uiGeneratedId} />
            {getHeaderTitle}
            {activity.actions.map((activityAction) => (
                <HeaderActivity
                    key={activityAction.id}
                    activityAction={activityAction}
                    scenarioVersionId={activity.scenarioVersionId}
                    activityAttachment={activity.attachment}
                />
            ))}
        </StyledActivityItemHeader>
    );
};

export default ActivityItemHeader;
