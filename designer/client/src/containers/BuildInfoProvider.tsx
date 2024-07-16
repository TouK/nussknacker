import React, { useContext } from "react";
import { createContext, PropsWithChildren, useEffect, useState } from "react";
import { BuildInfoType } from "../components/Process/types";
import HttpService, { AppBuildInfo } from "../http/HttpService";
import LoaderSpinner from "../components/spinner/Spinner";

const BuildInfoContext = createContext<BuildInfoType>(null);

export const BuildInfoProvider = ({ children }: PropsWithChildren) => {
    const [buildInfo, setBuildInfo] = useState<AppBuildInfo>();

    useEffect(() => {
        HttpService.fetchAppBuildInfo().then((res) => setBuildInfo(res.data));
    }, []);

    return buildInfo ? <BuildInfoContext.Provider value={buildInfo}>{children}</BuildInfoContext.Provider> : <LoaderSpinner show />;
};

export const useBuildInfo = () => {
    const context = useContext(BuildInfoContext);

    if (!context) {
        throw new Error(`${useBuildInfo.name} was used outside of its ${BuildInfoContext.displayName} provider`);
    }

    return context;
};
