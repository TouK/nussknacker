import React, { PropsWithChildren, useCallback, useMemo } from "react";
import { Button, styled, Typography } from "@mui/material";
import { SearchHighlighter } from "../creator/SearchHighlighter";
import HttpService, { ActionMetadata, ActivityAttachment } from "../../../http/HttpService";
import UrlIcon from "../../UrlIcon";
import { blend } from "@mui/system";
import { getBorderColor } from "../../../containers/theme/helpers";
import { unsavedProcessChanges } from "../../../common/DialogMessages";
import { useDispatch, useSelector } from "react-redux";
import { getProcessName, getScenario, isSaveDisabled } from "../../../reducers/selectors/graph";
import { useWindows } from "../../../windowManager";
import { displayScenarioVersion } from "../../../actions/nk";
import { ItemActivity } from "./ActivitiesPanel";
import { handleOpenCompareVersionDialog } from "../../modals/CompareVersionsDialog";

const StyledHeaderIcon = styled(UrlIcon)(({ theme }) => ({
    width: "16px",
    height: "16px",
    marginRight: theme.spacing(1),
    color: theme.palette.primary.main,
}));

const StyledHeaderActionIcon = styled(UrlIcon)(({ theme }) => ({
    width: "16px",
    height: "16px",
    marginLeft: "auto",
    cursor: "pointer",
    color: theme.palette.text.secondary,
}));

const StyledActivityItemHeader = styled("div")<{ isHighlighted: boolean; isActive: boolean }>(({ theme, isHighlighted, isActive }) => ({
    display: "flex",
    alignItems: "center",
    padding: `${theme.spacing(0.5)} ${theme.spacing(1)}`,
    backgroundColor: isActive
        ? blend(theme.palette.background.paper, theme.palette.primary.main, 0.2)
        : isHighlighted
        ? blend(theme.palette.background.paper, theme.palette.primary.main, 0.05)
        : undefined,
    border: (isActive || isHighlighted) && `1px solid ${getBorderColor(theme)}`,
    borderRadius: theme.spacing(1),
}));

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

    switch (activityAction.id) {
        case "compare": {
            return (
                <StyledHeaderActionIcon
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
    isActiveItem: boolean;
    searchQuery: string;
    scenarioVersion: number;
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
            console.log(scenarioId);
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

const ActivityItemHeader = ({ activity, isActiveItem, searchQuery }: Props) => {
    const scenario = useSelector(getScenario);
    const { processVersionId } = scenario || {};

    const isHighlighted = ["SCENARIO_DEPLOYED", "SCENARIO_CANCELED"].includes(activity.type);
    const openVersionEnable = activity.type === "SCENARIO_MODIFIED" && activity.scenarioVersionId !== processVersionId;

    const getHeaderTitle = useMemo(() => {
        const headerTitle = (
            <Typography
                variant={"caption"}
                component={SearchHighlighter}
                highlights={[searchQuery]}
                sx={(theme) => ({ color: theme.palette.text.primary })}
            >
                {activity.overrideDisplayableName || activity.activities.displayableName}
            </Typography>
        );

        if (openVersionEnable) {
            return <WithOpenVersion scenarioVersion={activity.scenarioVersionId}>{headerTitle}</WithOpenVersion>;
        }

        return headerTitle;
    }, [activity.activities.displayableName, activity.overrideDisplayableName, activity.scenarioVersionId, openVersionEnable, searchQuery]);

    return (
        <StyledActivityItemHeader isHighlighted={isHighlighted} isActive={isActiveItem}>
            <StyledHeaderIcon src={activity.activities.icon} />
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
