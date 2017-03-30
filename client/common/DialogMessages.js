
class DialogMessages {
  unsavedProcessChanges = () => {
    return `There are some unsaved process changes. Do you want to discard unsaved changes?`
  }
  deploy = (processId) => {
    return `Are you sure you want to deploy ${processId}?`
  }
  migrate = (processId, environmentId) => {
    return `Are you sure you want to migrate ${processId} to ${environmentId}?`
  }

  stop = (processId) => {
    return `Are you sure you want to stop ${processId}?`
  }

  deleteComment = () => {
    return 'Are you sure you want to delete comment?'
  }

}

export default new DialogMessages()