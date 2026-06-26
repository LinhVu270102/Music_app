# Security Policy

This document explains how security issues should be reported and handled in **Orange Music**.

Orange Music is a graduation project built with Android Kotlin, Firebase, Media3 ExoPlayer, and a Node.js server for third-party music API integration.

## Supported Versions

This project is maintained mainly for learning, demonstration, and graduation project purposes.

| Version | Supported |
|---|---|
| `main` branch | Yes |
| Older commits | No |

Security fixes are applied to the latest `main` branch only.

## Reporting a Vulnerability

If you discover a security issue, please do **not** open a public GitHub issue with sensitive details.

Instead, report it privately to the project owner through GitHub.

When reporting a vulnerability, please include:

- A clear description of the issue
- Steps to reproduce
- Affected feature or file
- Possible impact
- Screenshots, logs, or proof of concept if available
- Suggested fix if you have one

Please avoid sharing real user data, private keys, access tokens, or secret values in the report.

## Security Scope

Security issues may include:

- Exposed API keys, secrets, or credentials
- Incorrect Firebase Firestore or Storage rules
- Unauthorized access to user data
- Authentication or authorization bypass
- Insecure file upload handling
- Unsafe handling of music URLs or external API responses
- Hard-coded sensitive information
- Dependency vulnerabilities in the Android app or Node.js server

## Out of Scope

The following reports are usually considered out of scope:

- Issues caused by using outdated local development tools
- Bugs that do not affect security
- Rate limits or restrictions from third-party services
- Vulnerabilities in third-party platforms that are not caused by this project
- Social engineering or physical access attacks

## Secrets and Credentials

The following files and values must never be committed to the repository:

```text
.env
*.env
soundcloud-server/.env
local.properties
serviceAccountKey.json
*.jks
*.keystore
node_modules/
soundcloud-server/node_modules/
```

The project may include example configuration files such as:

```text
.env.example
```

Example files must not contain real secrets.

## Firebase Security

Firebase access must be protected using Firebase Security Rules.

The following files should be reviewed before deployment:

```text
firestore.rules
storage.rules
```

Avoid insecure rules such as:

```js
allow read, write: if true;
```

Rules should check authentication and user permissions where appropriate.

## Third-party Services

This project may integrate with third-party services such as SoundCloud.

Third-party API credentials must be stored in local environment files and must not be exposed in Android source code, GitHub commits, README files, screenshots, or logs.

If a third-party secret is accidentally committed, it should be rotated immediately.

## Dependency Security

For the Node.js server, check dependencies regularly:

```bash
cd soundcloud-server
npm audit
```

For Android dependencies, keep Gradle dependencies updated and review security-related release notes when possible.

## Responsible Disclosure

Please allow reasonable time for the issue to be reviewed and fixed before sharing details publicly.

The project owner may:

- Confirm the vulnerability
- Request more information
- Fix the issue
- Close reports that are not security-related
- Credit the reporter if appropriate
