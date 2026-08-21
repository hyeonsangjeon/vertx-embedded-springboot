# Security Policy

## Supported Versions

Security fixes are applied to the latest tagged release and the default branch. Older sample releases are not maintained.

## Reporting a Vulnerability

Please use [GitHub private vulnerability reporting](https://github.com/hyeonsangjeon/vertx-embedded-springboot/security/advisories/new). Do not include exploit details, credentials, or sensitive logs in a public issue.

Include the affected version, configuration, reproduction steps, impact, and any known workaround. You should receive an acknowledgement within seven days.

## Sample Boundary

This repository is an educational reference, not a production job system. The local event bus, job registry, search index, demo providers, and H2 database intentionally run in the same process. They provide no durability, cross-instance coordination, authentication, tenant isolation, or delivery guarantee across restarts. See the README's production boundary before adapting the code to a deployed service.
