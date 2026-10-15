import { Auth, Role } from './auth';

/** A signed-in person for tests, without Keycloak. */
export class FakeAuth extends Auth {

  readonly displayName: string;
  readonly roles: ReadonlySet<string>;
  loggedOut = false;

  constructor(readonly username: string, ...roles: Role[]) {
    super();
    this.displayName = username;
    this.roles = new Set(roles);
  }

  token(): Promise<string> {
    return Promise.resolve(`token-for-${this.username}`);
  }

  logout(): void {
    this.loggedOut = true;
  }
}

export function signedInAs(username: string, ...roles: Role[]) {
  return { provide: Auth, useValue: new FakeAuth(username, ...roles) };
}
