# TODO

## Da fare

### Autenticazione e autorizzazione

- [ ] Supportare metodi di autenticazione alternativi a Basic Auth: valutare ticket Alfresco e OAuth2/OIDC, mantenendo Alfresco come backend di autenticazione e fonte dei gruppi e preservando l'accesso di amministratori e gruppi lettori.
- [ ] Applicare le ACL Alfresco all'accesso ai documenti archiviati; attualmente i gruppi lettori possono consultare tutti i documenti.

### Gestione delle chiavi

- [ ] Valutare uno script operatore per il provisioning manuale dei segreti, senza esposizione nei log o negli argomenti dei processi e senza sovrascritture involontarie. Il provisioning iniziale automatico è già disponibile.

### API di compressione

- [ ] Implementare la compressione di documenti già archiviati tramite API singolo nodo e bulk; gli endpoint esistono ma restituiscono ancora `501 Not Implemented`. Prevedere idempotenza, lock distribuiti e bulk asincrono.

## Implementazioni future

### Backend filesystem

- [ ] Aggiungere un `FileSystemContentService` selezionabile con `VAULT_STORAGE_IMPL=filesystem` e directory di storage configurabile, riutilizzando checksum, compressione e cifratura esistenti; mantenere i metadati ricercabili in MongoDB.
- [ ] Salvare binario e metadati tecnici JSON in generazioni immutabili per nodo, con directory suddivise per prefisso dell'ID e pubblicazione tramite sostituzione atomica del riferimento `current` sullo stesso filesystem.
- [ ] Garantire coerenza e durabilità dopo interruzioni, validare gli ID contro il path traversal, coordinare le operazioni con i lock esistenti e gestire lettori attivi prima di eliminare vecchie generazioni.
- [ ] Prevedere pulizia delle generazioni incomplete e smoke test per archivio, download, ripristino, ri-cifratura e cancellazione, includendo concorrenza e recupero dopo interruzioni.

Il backend deve restare indipendente dal filesystem: Btrfs/ZFS possono fornire snapshot, compressione e deduplicazione a livello infrastrutturale. COW non implica deduplicazione automatica; la cifratura applicativa con salt e nonce casuali rende la deduplicazione dei contenuti praticamente inefficace. Per più istanze ANV serve storage condiviso con semantiche di accesso e sostituzione atomica verificate. Valore principale: installazioni più semplici e uso dello storage esistente.
