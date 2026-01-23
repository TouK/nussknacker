import React, { forwardRef, useMemo } from "react";

import { userSettingSet, userSettingsRotate } from "../../actions/nk/userSettings";
import { SwitchElement } from "../../containers/settings/collapsibleSwitchList";
import { getUserSettingsMerged } from "../../reducers/selectors/userSettings";
import type { Setting } from "../../reducers/userSettings";
import { useAppDispatch, useAppSelector } from "../../store/storeHelpers";
import type { ResultItemProps } from "./BaseResultItem";
import { BaseResultItem } from "./BaseResultItem";

export const ResultItem = forwardRef<HTMLDivElement, ResultItemProps>(function Result(props, forwardedRef) {
    const { action } = props;

    const dispatch = useAppDispatch();
    const settings = useAppSelector(getUserSettingsMerged);

    const isFlag = useMemo(() => Boolean(action.id.match(/^flag\//)), [action.id]);
    const path = useMemo(() => action.id.replace(/^flag\//, "") as Setting, [action.id]);
    const setting = settings[path];

    return (
        <BaseResultItem ref={forwardedRef} {...props} preventClose={isFlag}>
            {isFlag && setting ? (
                <SwitchElement
                    value={setting}
                    onChange={(value) => {
                        dispatch(
                            value === false || value === true || value === "default"
                                ? userSettingSet(path, value)
                                : userSettingsRotate(path),
                        );
                    }}
                />
            ) : null}
        </BaseResultItem>
    );
});
