import React, { ForwardedRef, forwardRef, useCallback, useEffect, useMemo, useRef, useState } from "react";
import { ToolbarPanelProps } from "../../toolbarComponents/DefaultToolbarPanel";
import { ToolbarWrapper } from "../../toolbarComponents/toolbarWrapper/ToolbarWrapper";
import httpService, { ActionMetadata, ActivitiesResponse, ActivityMetadata, ActivityMetadataResponse } from "../../../http/HttpService";
import { Box, Divider, styled, Typography } from "@mui/material";
import { formatDateTime } from "../../../common/DateUtils";
import CommentContent from "../../comment/CommentContent";
import { useSelector } from "react-redux";
import { createSelector } from "reselect";
import { getFeatureSettings } from "../../../reducers/selectors/settings";
import UrlIcon from "../../UrlIcon";
import { getBorderColor } from "../../../containers/theme/helpers";
import { blend } from "@mui/system";
import { VariableSizeList } from "react-window";
import AutoSizer from "react-virtualized-auto-sizer";
import moment from "moment";
import { MoreItemsButton } from "./MoreItemsButton";
import { v4 as uuid4 } from "uuid";
import { LessItemsButton } from "./LessItemsButton";

interface UiButtonActivity {
    type: "moreItemsButton";
    sameItemOccurrence: number;
    isClicked: boolean;
}

interface UiDateActivity {
    type: "date";
    value: string;
}

interface UiItemActivity {
    type: "item";
    isDisabled: boolean;
}

type Activity = ActivitiesResponse["activities"][number] & {
    activities: ActivityMetadata;
    actions: ActionMetadata[];
    ui?: UiButtonActivity | UiDateActivity | UiItemActivity;
};

const estimatedItemSize = 150;
const mergeActivityDataWithMetadata = (
    activities: ActivitiesResponse["activities"],
    activitiesMetadata: ActivityMetadataResponse,
): Activity[] => {
    return activities.map((activity): Activity => {
        const activities = activitiesMetadata.activities.find((activityMetadata) => activityMetadata.type === activity.type);
        const actions = activities.supportedActions.map((supportedAction) => {
            return activitiesMetadata.actions.find((action) => action.id === supportedAction);
        });

        return { ...activity, activities, actions };
    });
};

export const StyledActivityRoot = styled("div")(({ theme }) => ({
    padding: `${theme.spacing(1)} ${theme.spacing(1)} ${theme.spacing(4)}`,
}));
export const StyledActivityHeader = styled("div")(({ theme }) => ({
    display: "flex",
    alignItems: "center",
    padding: theme.spacing(1),
    backgroundColor: blend(theme.palette.background.paper, theme.palette.primary.main, 0.2),
    border: `1px solid ${getBorderColor(theme)}`,
    borderRadius: theme.spacing(1),
}));
export const StyledActivityBody = styled("div")(({ theme }) => ({
    margin: theme.spacing(1),
}));
export const StyledHeaderIcon = styled(UrlIcon)(({ theme }) => ({
    width: "16px",
    height: "16px",
    marginRight: theme.spacing(1),
}));

export const StyledHeaderActionIcon = styled(UrlIcon)(() => ({
    width: "16px",
    height: "16px",
    marginLeft: "auto",
    cursor: "pointer",
}));

const getCommentSettings = createSelector(getFeatureSettings, (f) => f.commentSettings || {});

const HeaderActivity = ({ activityAction }: { activityAction: ActionMetadata }) => {
    switch (activityAction.id) {
        case "compare": {
            return (
                <StyledHeaderActionIcon
                    onClick={() => {
                        alert(`action called: ${activityAction.id}`);
                    }}
                    key={activityAction.id}
                    src={activityAction.icon}
                />
            );
        }
        default: {
            return null;
        }
    }
};

const ActivityItem = forwardRef(({ activity }: { activity: Activity }, ref: ForwardedRef<HTMLDivElement>) => {
    const commentSettings = useSelector(getCommentSettings);

    return (
        <StyledActivityRoot ref={ref}>
            <StyledActivityHeader>
                <StyledHeaderIcon src={activity.activities.icon} />
                <Typography variant={"body2"}>{activity.activities.displayableName}</Typography>
                {activity.actions.map((activityAction) => (
                    <HeaderActivity key={activityAction.id} activityAction={activityAction} />
                ))}
            </StyledActivityHeader>
            <StyledActivityBody>
                <Typography mt={0.5} component={"p"} variant={"caption"}>
                    {formatDateTime(activity.date)} | {activity.user}
                </Typography>
                {activity.scenarioVersionId && (
                    <Typography component={"p"} variant={"caption"}>
                        Version: {activity.scenarioVersionId}
                    </Typography>
                )}
                {activity.comment && <CommentContent content={activity.comment} commentSettings={commentSettings} />}
                {activity.additionalFields.map((additionalField, index) => (
                    <Typography key={index} component={"p"} variant={"caption"}>
                        {additionalField.name}: {additionalField.value}
                    </Typography>
                ))}
            </StyledActivityBody>
        </StyledActivityRoot>
    );
});

ActivityItem.displayName = "ActivityItem";

export const ActivitiesPanel = (props: ToolbarPanelProps) => {
    const listRef = useRef<VariableSizeList>(null);
    const rowHeights = useRef({});

    const setRowHeight = useCallback((index, size) => {
        if (listRef.current) {
            listRef.current.resetAfterIndex(0);
        }

        rowHeights.current = { ...rowHeights.current, [index]: size };
    }, []);

    const getRowHeight = useCallback((index: number) => {
        return rowHeights.current[index] || estimatedItemSize;
    }, []);

    const [data, setData] = useState<Activity[]>([]);

    useEffect(() => {
        Promise.all([httpService.fetchActivitiesMetadata(), httpService.fetchActivities()]).then(([activitiesMetadata, { activities }]) => {
            const mergedActivitiesDataWithMetadata = mergeActivityDataWithMetadata(activities, activitiesMetadata);

            const infiniteListData = [];
            const hideItemsOptionAvailableLimit = 4;
            const formatDate = (date: string) => moment(date).format("YYYY-MM-DD");

            const recursiveDateLabelDesignation = (activity, index, occurrence = 0) => {
                const nextActivity = mergedActivitiesDataWithMetadata[index + 1 + occurrence];
                const previousActivity = mergedActivitiesDataWithMetadata[index - 1 + occurrence];

                if (occurrence > hideItemsOptionAvailableLimit && activity.type !== nextActivity?.type) {
                    return {
                        id: uuid4(),
                        ui: {
                            type: "date",
                            value: `${formatDate(activity.date)} - ${formatDate(previousActivity.date)}`,
                        },
                    };
                }

                if (activity.type === nextActivity?.type) {
                    occurrence++;
                    return recursiveDateLabelDesignation(activity, index, occurrence);
                }

                if (
                    activity.type !== nextActivity?.type &&
                    moment(activity.date).format("YYYY-MM-DD") !==
                        (previousActivity?.date ? moment(previousActivity.date).format("YYYY-MM-DD") : undefined)
                ) {
                    return {
                        id: uuid4(),
                        ui: { value: formatDate(activity.date), type: "date" },
                    };
                }

                return undefined;
            };

            const recursiveMoreItemsButtonDesignation = (activity, index, occurrence = 0) => {
                const previousActivity = mergedActivitiesDataWithMetadata[index - 1 - occurrence];

                if (occurrence > hideItemsOptionAvailableLimit && activity.type !== previousActivity?.type) {
                    return {
                        id: uuid4(),
                        ui: {
                            type: "moreItemsButton",
                            sameItemOccurrence: occurrence,
                            clicked: false,
                        },
                    };
                }

                if (activity.type === previousActivity?.type) {
                    occurrence++;
                    return recursiveMoreItemsButtonDesignation(activity, index, occurrence);
                }

                return undefined;
            };

            mergedActivitiesDataWithMetadata
                .sort((a, b) => moment(b.date).diff(a.date))
                .forEach((activity, index) => {
                    const dateLabel = recursiveDateLabelDesignation(activity, index);
                    const moreItemsButton = recursiveMoreItemsButtonDesignation(activity, index);
                    dateLabel && infiniteListData.push(dateLabel);
                    infiniteListData.push({ ...activity, id: uuid4(), ui: { type: "item", isDisabled: false } });
                    moreItemsButton && infiniteListData.push(moreItemsButton);
                });

            setData(infiniteListData);
        });
    }, []);

    const handleHideData = (index: number, sameItemOccurrence: number) => {
        setData((prevState) => {
            return prevState.map((data, indx) => {
                if (indx === index) {
                    return { ...data, ui: { ...data.ui, isClicked: true } };
                }

                if (indx <= index && indx > index - sameItemOccurrence - 1) {
                    return { ...data, ui: { ...data.ui, isDisabled: true } };
                }

                return data;
            });
        });
        listRef.current.scrollToItem(index - sameItemOccurrence - 2);
    };

    const handleShowData = (index: number, sameItemOccurrence: number) => {
        setData((prevState) => {
            return prevState.map((data, indx) => {
                if (indx === index + sameItemOccurrence) {
                    return { ...data, ui: { ...data.ui, isClicked: false } };
                }

                if (indx >= index && indx < index + sameItemOccurrence) {
                    return { ...data, ui: { ...data.ui, isDisabled: false } };
                }

                return data;
            });
        });
    };

    const dataToDisplay = useMemo(
        () => data.filter((activity) => (activity.ui.type === "item" && !activity.ui.isDisabled) || activity.ui.type !== "item"),
        [data],
    );

    if (!dataToDisplay.length) return;

    const Row = ({ index, style }) => {
        const rowRef = useRef<HTMLDivElement>(null);
        const activity = useMemo(() => dataToDisplay[index], [index]);

        useEffect(() => {
            if (rowRef.current) {
                setRowHeight(index, rowRef.current.clientHeight);
            }
        }, [index, rowRef]);

        const itemToRender = useMemo(() => {
            switch (activity.ui.type) {
                case "item": {
                    return <ActivityItem activity={activity} ref={rowRef} />;
                }
                case "date": {
                    return (
                        <Box display={"flex"} justifyContent={"center"} alignItems={"center"} px={1}>
                            <Divider variant={"fullWidth"} sx={(theme) => ({ flex: 1, backgroundColor: theme.palette.common.white })} />
                            <Typography component={"div"} variant={"caption"} ref={rowRef} flex={1} textAlign={"center"}>
                                {activity.ui.value}
                            </Typography>
                            <Divider variant={"fullWidth"} sx={(theme) => ({ flex: 1, backgroundColor: theme.palette.common.white })} />
                        </Box>
                    );
                }
                case "moreItemsButton": {
                    return (
                        <div ref={rowRef}>
                            {activity.ui.isClicked ? (
                                <MoreItemsButton
                                    sameItemOccurrence={activity.ui.sameItemOccurrence}
                                    handleShowData={handleShowData}
                                    index={index}
                                />
                            ) : (
                                <LessItemsButton
                                    sameItemOccurrence={activity.ui.sameItemOccurrence}
                                    handleHideData={handleHideData}
                                    index={index}
                                />
                            )}
                        </div>
                    );
                }
                default: {
                    return null;
                }
            }
        }, [activity, index]);

        return (
            <div key={activity.id} style={style}>
                {itemToRender}
            </div>
        );
    };

    return (
        <ToolbarWrapper {...props} title={"Activities"}>
            <div style={{ width: "100%", height: "500px" }}>
                <AutoSizer>
                    {({ width, height }) => (
                        <VariableSizeList
                            ref={listRef}
                            itemCount={dataToDisplay.length}
                            itemSize={getRowHeight}
                            height={height}
                            width={width}
                            estimatedItemSize={estimatedItemSize}
                            itemKey={(index) => {
                                return dataToDisplay[index].id;
                            }}
                        >
                            {Row}
                        </VariableSizeList>
                    )}
                </AutoSizer>
            </div>
        </ToolbarWrapper>
    );
};
