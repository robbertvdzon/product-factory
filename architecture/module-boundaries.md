# Composition roots en grenzen

- `productfactory` is de enige Spring Modulith composition root.
- `agentworker` is een zelfstandig proces dat alleen contracts/common gebruikt.
- `dashboard-backend` is een zelfstandig OIDC-beveiligd proces en benadert de runtime via HTTP.
- `dashboard-frontend` gebruikt uitsluitend de dashboard-backend.
- alleen de workspace-module mag Git-publicatie uitvoeren.
