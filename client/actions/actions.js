export function displayProcess(processDetails) {
  return {
    type: "DISPLAY_PROCESS",
    processDetails: processDetails,
    processToDisplay: processDetails.json
  };
}

export function displayDeployedProcess(processDetails) {
  return {
    type: "DISPLAY_PROCESS",
    processDetails: processDetails,
    processToDisplay: processDetails.deployedJson
  };
}


export function displayNodeDetails(node) {
  return {
    type: "DISPLAY_NODE_DETAILS",
    nodeToDisplay: node
  };
}

export function closeNodeDetails() {
  return {
    type: "CLOSE_NODE_DETAILS"
  };
}

export function nodeChangePersisted(before, after) {
  return {
    type: "NODE_CHANGE_PERSISTED",
    before,
    after
  };
}