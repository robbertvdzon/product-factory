# Databasebackup en herstel

De CronJob maakt dagelijks om 02:17 UTC een PostgreSQL custom-format dump in de afzonderlijke map
`productfactory-v2`. Een dump wordt pas onder zijn definitieve naam geplaatst nadat
`pg_restore --list` slaagt; de SHA-256-checksum wordt ernaast bewaard. De bewaartermijn is veertien
dagen. De backup-PVC en CronJob worden pas als onderdeel van de handmatige deployment in stap 9
aangemaakt.

Herstel altijd naar een nieuw aangemaakte, lege tijdelijke database. Valideer daarna minimaal:

1. `sha256sum --check <dump>.sha256`;
2. `pg_restore --list <dump>`;
3. `pg_restore --no-owner --no-privileges --dbname <tijdelijke-url> <dump>`;
4. de hoogste succesvolle versie in `flyway_schema_history`;
5. de verwachte technische metadata en applicatie-health tegen de tijdelijke database.

Overschrijf nooit de actieve productie- of acceptatiedatabase tijdens een restoretest. De
PostgreSQL-integratietest voert deze backup- en herstelroute automatisch uit in een tijdelijke
container.
