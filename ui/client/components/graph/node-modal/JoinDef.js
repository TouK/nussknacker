import NodeUtils from "../NodeUtils"

class JoinDef {

    constructor(node, nodeObjectDetails, processToDisplay) {
        this.branchParameters = nodeObjectDetails.parameters.filter(p => p.branchParam)
        this.incomingEdges = NodeUtils.getIncomingEdges(node.id, processToDisplay)
    }

}

export default JoinDef
