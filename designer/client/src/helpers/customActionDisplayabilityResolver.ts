import {CustomActionDisplayPolicy} from "../types";
import {useSelector} from "react-redux";
import {getProcessName, getProcessVersionId} from "../reducers/selectors/graph";
import HttpService from "../http/HttpService";

export async function resolveCustomActionDisplayability(displayPolicy: CustomActionDisplayPolicy){
  const processName = useSelector(getProcessName);
  const processVersionId = useSelector(getProcessVersionId);

  switch(displayPolicy) {
    case CustomActionDisplayPolicy.CurrentlyViewedProcessVersionIsDeployed:
      try {
        const response = await HttpService.fetchLastlyDeployedVersionId(processName);
        const data = response.data;
        return data.versionId === processVersionId;
      } catch (error) {
        console.error("Error while fetching lastly deployed version ID:", error);
        return false;
      }
    default:
      return false;
  }
}
