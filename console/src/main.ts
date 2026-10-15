import { bootstrapApplication } from '@angular/platform-browser';
import Keycloak from 'keycloak-js';

import { appConfig } from './app/app.config';
import { App } from './app/app';
import { Auth, KEYCLOAK, KeycloakAuth } from './app/auth/auth';

// Sign in before anything renders: every screen reads data only a signed-in person may see.
const keycloak = new Keycloak({ url: KEYCLOAK.url, realm: KEYCLOAK.realm, clientId: KEYCLOAK.clientId });

keycloak
  .init({ onLoad: 'login-required', pkceMethod: 'S256', checkLoginIframe: false })
  .then(() => bootstrapApplication(App, {
    ...appConfig,
    providers: [...appConfig.providers, { provide: Auth, useValue: new KeycloakAuth(keycloak) }],
  }))
  .catch((err) => {
    console.error(err);
    document.body.textContent = `Could not reach the sign-in service at ${KEYCLOAK.url} - is Keycloak running `
      + '(docker compose --profile auth up -d)?';
  });
