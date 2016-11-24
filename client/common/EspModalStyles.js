
class EspModalStyles {

  modalStyles = (userStyles = {}) => {
    var defaultStyles = {
      overlay: {
        backgroundColor: 'rgba(63, 62, 61, 0.3)'
      },
      content: {
        borderRadius: '0',
        padding: '0',
        left: '20%',
        right: '20%',
        top: '100px',
        bottom: '100px',
        border: 'none',
        overflow: 'none'
      }
    };
    return {
      overlay: {
        ...defaultStyles.overlay,
        ...userStyles.overlay
      },
      content: {
        ...defaultStyles.content,
        ...userStyles.content
      }

    };
  }

  headerStyles = (fill, color) => {
    return {
      backgroundColor: fill,
      color: color
    }
  }

}

export default new EspModalStyles()