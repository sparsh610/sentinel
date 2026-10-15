import Keycloak from 'keycloak-js';

export type Role = 'ANALYST' | 'SENIOR_APPROVER';

/** Where the console signs in: the sentinel realm, as a public client using PKCE. */
export const KEYCLOAK = {
  url: 'http://localhost:8080',
  realm: 'sentinel',
  clientId: 'sentinel-console',
} as const;

/**
 * The signed-in person, as the rest of the console sees them.
 *
 * The names and roles shown here only decide which buttons to draw. Every rule is enforced again
 * by the services, from the token - hiding a button is a courtesy, not a control.
 */
export abstract class Auth {
  abstract readonly username: string;
  abstract readonly displayName: string;
  abstract readonly roles: ReadonlySet<string>;

  /** A valid access token, refreshed first if it is about to expire. */
  abstract token(): Promise<string>;

  abstract logout(): void;

  hasRole(role: Role): boolean {
    return this.roles.has(role);
  }
}

/** Auth backed by keycloak-js, created once the login redirect has completed (main.ts). */
export class KeycloakAuth extends Auth {

  constructor(private readonly keycloak: Keycloak) {
    super();
  }

  get username(): string {
    return this.keycloak.tokenParsed?.['preferred_username'] ?? '';
  }

  get displayName(): string {
    return this.keycloak.tokenParsed?.['name'] ?? this.username;
  }

  get roles(): ReadonlySet<string> {
    return new Set<string>(this.keycloak.tokenParsed?.['roles'] ?? []);
  }

  async token(): Promise<string> {
    // Renew when fewer than 30 s are left; a failed renewal means the session is over.
    try {
      await this.keycloak.updateToken(30);
    } catch {
      await this.keycloak.login();
    }
    return this.keycloak.token ?? '';
  }

  logout(): void {
    void this.keycloak.logout({ redirectUri: window.location.origin });
  }
}
