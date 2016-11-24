
class DialogMessages {
  unsavedProcessChanges = () => {
    return `There are some unsaved process changes. Do you want to discard unsaved changes?`
  }
  deploy = (processId) => {
    return `Are you sure you want to deploy ${processId}?`
  }
  stop = (processId) => {
    return `Are you sure you want to stop ${processId}?`
  }

}

export default new DialogMessages()