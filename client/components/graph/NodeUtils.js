
export default {
  nodeType: (node) => {
    return node.type ? node.type : "Properties";
  },

  nodeIsProperties: (node) => {
    var type = node.type ? node.type : "Properties"
    // fixme jak najlepiej tutaj wolac funkcje z tego samego modulu?
    return type == "Properties";
  }

}
