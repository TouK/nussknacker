import { createGlobalStyle } from "styled-components";
import { normalize } from "styled-normalize";

export const GlobalStyle = createGlobalStyle`

  // Include normalize.css
  ${normalize}

  // Define your Stylus styles here

  html,
  body {
    margin: 0;
    padding: 0;
    height: 100vh;
    background: ${(props) => props.theme.graphBkgColor};
    color: ${(props) => props.theme.defaultTextColor};
    font-size: 16px;
    overflow: hidden;
  }

  .notification-dismiss {
    display: none !important;
  }

  .notifications-wrapper {
    position: absolute;
    bottom: 25px;
    right: 25px;
    z-index: 10000;
  }

  .notification {
    font-family: "Open Sans";
    font-size: 16px;
  }

  /* ... (continue converting the rest of your styles) */

  .big-blue-button {
    margin: 45px 50px;
    width: 360px;
    min-width: 300px;
    height: ${(props) => props.theme.formControllHeight};
    background-color: ${(props) => props.theme.buttonBlueBkgColor};
    color: ${(props) => props.theme.buttonBlueColor};
    display: flex;
    align-items: center;
    justify-content: center;
    font-family: 'Open Sans';
    font-size: 21px;
    font-weight: 600;
    border-radius: 0;

    &:hover {
      color: ${(props) => props.theme.buttonBlueColor};
      background: lighten(${(props) => props.theme.buttonBlueBkgColor}, 25%);
    }

    &:focus {
      color: ${(props) => props.theme.buttonBlueColor};
    }

    #add-icon {
      width: 20px;
      margin-left: 20px;

      &:hover {
        background: ${(props) => props.theme.processesHoverColor};
      }
    }
  }

  .error-template {
    text-align: center;
    position: absolute;
    top: 50%;
    left: 50%;
    transform: translate(-50%, -50%);

    h1 {
      font-size: 96px;
    }

    h2 {
      font-size: 64px;
    }

    .error-actions {
      margin-top: 15px;
      margin-bottom: 15px;
    }

    .error-actions .btn {
      margin-right: 10px;
    }

    .error-details {
      font-size: 24px;

      .big-blue-button {
        margin: 45px auto;
      }
    }
  }
`;
