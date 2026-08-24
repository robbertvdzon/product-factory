# Deployment tijdelijk uitgeschakeld

Stappen 0 tot en met 8 van de technische fundering deployen niets. De oude Product Factory blijft
in OpenShift draaien en de actieve clusterresources worden in deze fase niet door GitOps vervangen.

In stap 9 krijgt deze map een nieuwe Kustomize-base met uitsluitend overlays voor `acceptance` en
`production`. De eerste rollout is dan bewust handmatig; automatische promotie wordt pas na de
gevalideerde productie-uitrol in stap 10 geactiveerd.

`seal-secrets.sh` behoudt het bestaande Sealed Secrets-proces en schrijft alleen een versleuteld
manifest. Het plaintext bronbestand blijft `../secrets.env`.
