# Nuvio Enhanced — Novità

Tutto ciò che Nuvio Enhanced aggiunge al [Nuvio](https://github.com/NuvioMedia/NuvioMobile) originale. Le funzioni dell'app originale (compresi Watch Party, liste MDBList e motore P2P) restano presenti e vengono integrate regolarmente; questa pagina elenca solo ciò che è specifico di questa fork.

🇬🇧 [English version](./ENHANCEMENTS.md) · [Torna al README](./README.md)

La maggior parte delle impostazioni è salvata per profilo e sincronizzata con l'account Nuvio, quindi ti segue su tutti i dispositivi.

---

## Indice

- [App Android TV](#app-android-tv)
- [Live TV ed EPG](#live-tv-ed-epg)
- [Chromecast e proxy multimediale](#chromecast-e-proxy-multimediale)
- [Download](#download)
- [Player](#player)
- [Flussi](#flussi)
- [Aspetto e interfaccia](#aspetto-e-interfaccia)
- [Diagnostica](#diagnostica)
- [Sincronizzazione e impostazioni](#sincronizzazione-e-impostazioni)
- [Release e aggiornamenti](#release-e-aggiornamenti)

---

## App Android TV

Un'app dedicata ad Android TV, distribuita come APK separato (`Nuvio-Enhanced-Alby-TV-…`). Condivide tutta la logica dell'app per telefono (account, profili, sincronizzazione, flussi, player) e aggiunge un'interfaccia pensata per il telecomando, ispirata a [NuvioTV](https://github.com/NuvioMedia/NuvioTV).

- **Navigazione:** "sidebar moderna" in stile NuvioTV con una pillola flottante che mostra la sezione aperta, oppure una **barra di navigazione vetrosa in alto** (icone + nomi o solo icone, con bagliore opzionale).
- **Home:** zona hero con **carosello automatico** (fino a 2 cataloghi hero, sincronizzati col profilo), righe dimensionate come NuvioTV, "Continua a guardare" in tre stili (Card, Wide, Poster) con badge per tempo rimanente, prossimo episodio, nuovo episodio e nuova stagione, espansione opzionale della locandina col focus.
- **Pagina "Layout TV":** preset per larghezza locandine e raggio degli angoli, locandine orizzontali, etichette sotto le locandine, stile di "Continua a guardare", ritardo dell'espansione, sfondo hero a schermo intero, più le scorciatoie alle opzioni condivise di Home e "Continua a guardare".
- **Pagina dettagli:** sfondo a schermo intero, hero in stile NuvioTV, pulsante Play (OK tenuto = scelta della sorgente), pulsanti libreria/visto/shuffle, voti MDBList, locandine o schede di testo per le stagioni, card episodi orizzontali o a elenco (segue l'impostazione del telefono, con episodi non visti sfocati e trame espandibili), **sezione voti IMDb per episodio**, cast cliccabile che apre la pagina della persona, collezione e titoli simili.
- **Menu opzioni delle card:** OK tenuto o tasto Menu su qualsiasi locandina o card di "Continua a guardare": dettagli, episodio a caso, riprendi/riproduci dall'inizio, scegli sorgente, segna come visto (senza sfarfallio in "Continua a guardare"), aggiungi/rimuovi dalla libreria, rimuovi da "Continua a guardare". Anche i flussi hanno il loro menu TV.
- **Telecomando nel player:** OK play/pausa, sinistra/destra per un seek cumulativo con anteprima (il passo cresce tenendo premuto), focus sulla barra di avanzamento, prompt "Salta intro"/"Prossimo episodio", pannelli e dialog che ricevono il focus da soli.
- **Gestione del focus ovunque:** il focus segue la navigazione, non si perde nelle schede nascoste, le Impostazioni funzionano a livelli (categorie → pannello → sottopagine, Indietro risale di un livello), i dialog danno il focus al pulsante principale, i campi di testo aprono la tastiera solo premendo OK.
- **Accesso con codice QR** (stesso collegamento del dispositivo di NuvioTV), con email e password come alternativa.
- **Ricerca vocale** con il riconoscimento vocale di sistema.
- Scorrimento centrato con bordi sfumati nelle Impostazioni, focus visibile nella selezione profilo e nei chip dei filtri.

## Live TV ed EPG

Portata da [luqmanfadlli/NuvioMobile-Enhanced](https://github.com/luqmanfadlli/NuvioMobile-Enhanced) ed estesa.

- Sorgenti: **playlist M3U** (URL o file locale), provider **Xtream** e **Stalker**.
- **EPG XMLTV** (`.xml` / `.xml.gz`), rilevata in automatico da playlist e provider, abbinata tramite `tvg-id` o nome del canale; il programma in onda è mostrato nel player.
- Scheda Live TV dedicata (telefono, tablet, TV e tab bar nativa iOS), preferiti, ricerca e categorie.
- Integrazione nel player: pannello con l'elenco dei canali, zapping, logo e nome del canale in sovrimpressione. I canali live non creano mai progressi di visione né scrobbling.
- **Gestione canali:** mostra/nascondi singoli canali e intere categorie, ricerca, seleziona/deseleziona tutto e **ordine personalizzato** con trascinamento (pulsanti Su/Giù sulla TV).
- Playlist, sorgenti EPG, preferiti e preferenze dei canali sono sincronizzati col profilo (credenziali e file locali restano sul dispositivo).

## Chromecast e proxy multimediale

- **Chromecast** (Android) con il Default Media Receiver: il dispositivo Cast funziona come un motore del player, quindi progressi, scrobbling, prossimo episodio, "Stai ancora guardando?" e seek personalizzato continuano a funzionare.
- Pulsante Cast nel player e voce "Trasmetti su Chromecast" nel menu del flusso; passaggio fluido tra telefono e TV.
- **Supporto HLS** su Chromecast: riconoscimento corretto del formato dei segmenti (TS / fMP4 / audio separato) e gestione dei segmenti camuffati (estensione o tipo di contenuto sbagliati).
- **Scelta della traccia audio** sul ricevitore, con la lingua audio preferita applicata in automatico.
- **Avvisi di compatibilità** per i codec che il ricevitore potrebbe non supportare (HEVC, AV1, Dolby Vision, DTS, TrueHD).
- **Proxy multimediale LAN riutilizzabile:** inoltra gli header degli addon, serve al Chromecast i flussi P2P e i download locali, riscrive i manifest HLS/DASH e aggiunge i CORS. Ripiego automatico tramite proxy per i flussi HLS/DASH che falliscono in diretta.
- **Pagina "Proxy multimediale":** stato in tempo reale, porta fissa, indirizzo di rete manuale, mantieni attivi Wi-Fi/CPU, User-Agent predefinito, timeout, interruttori per CORS e riscrittura delle playlist, opzioni Chromecast.

## Download

- **Pagina Impostazioni → Download:** scelta di una **cartella esterna** (Storage Access Framework), riepilogo dello spazio con barre per spazio usato/libero, card dei file con riproduci, riprendi, player esterno, Chromecast, condividi, informazioni sul file, pausa/riprova ed elimina.
- **File esterni:** i video già presenti nella cartella (copiati da altrove o di un altro profilo) si possono riprodurre direttamente, con i sottotitoli con lo stesso nome agganciati in automatico.
- **Download HLS** (Android): scelta di qualità video, tracce audio e sottotitoli in un dialog (lingua audio preferita preselezionata), download in background con pausa/ripresa e supporto AES-128, poi conversione senza ricodifica in **MP4 o MKV** con le tracce incluse.
- **Sezione "Download in background":** stato dell'ottimizzazione batteria con scorciatoia, e consigli per i produttori con risparmio energetico aggressivo.

## Player

- **Intervallo di salto personalizzato** (1–20 secondi) per i pulsanti a schermo, il doppio tocco (cumulativo), la notifica/media session e i comandi remoti di iOS, con icona dinamica.
- **Prompt "Stai ancora guardando?"** (in stile Netflix): dopo un numero configurabile di episodi riprodotti in automatico, opzionalmente solo di notte (22:00–04:00), con un conto alla rovescia di 10 secondi prima della chiusura del player. Una versione separata a tempo per la **Live TV** (da 15 min a 6 h).
- **Ritardo audio** (portato da NuvioTV): ±60 s a passi di 25 ms, ricordato **per uscita audio** (altoparlante, Bluetooth, HDMI…), con supporto all'hint `behaviorHints.audioDelayMs` di addon e plugin. Funziona con ExoPlayer e mpv.
- **Capitoli** (portati dalla fork di luqmanfadlli): segni dei capitoli sulla barra di avanzamento, titolo del capitolo corrente sopra la barra e pannello **Capitoli** per saltare a qualsiasi capitolo, su telefono, tablet e app TV. Letti dai file MKV/MP4 con ExoPlayer e direttamente da mpv (libmpv Android e iOS).
- **Visione privata** (portata dalla fork di luqmanfadlli): interruttore in Riproduzione → Player che smette di salvare progressi, Continua a guardare e stato "visto" e di inviare lo scrobbling ai tracker collegati, sia nel player interno sia per i risultati dei player esterni. Per profilo e per dispositivo (non sincronizzata).

## Flussi

- **Stream Parser:** una card alternativa dei flussi che legge qualsiasi descrizione e mostra badge della risoluzione con bagliore animato, sorgente, tag video/audio, canali, lingue audio (bandiere), dimensione (stimata se manca), stato cached/torrent/proxy, con un colore univoco per ogni etichetta.
- **Ordina per qualità video:** unisce tutti gli addon e plugin in un unico elenco ordinato da 8K a SD.
- **Menu azioni del flusso** (pulsante ⋮) con **"Descrizione originale"** per vedere e copiare il testo grezzo dell'addon.
- La preferenza dei plugin "raggruppa flussi per repository" ora è sincronizzata col profilo.

## Aspetto e interfaccia

- **Modalità AMOLED completa:** toggle aggiuntivo che rende nere anche superfici e card.
- **Formato data** a scelta (sistema, GG/MM/AAAA, MM/GG/AAAA, AAAA-MM-GG, GG MMM AAAA…) applicato a tutte le date dell'app.
- **Formato del codice episodio:** S1 E2, S01E02, S1E2, 01x02, 1x2.
- **Trame degli episodi espandibili** con layout animato in entrambi gli stili dell'elenco episodi.
- **Locandine orizzontali** applicate ovunque (griglie, cataloghi, libreria, ricerca, cartelle delle collezioni) senza mai tagliare le locandine verticali; le cartelle delle collezioni rispettano la propria forma.
- **Pulsanti d'azione principali con gradiente** e icone (Riproduci, Riprendi, Scegli episodi, Visualizza dettagli).
- Nuova icona di navigazione per la Live TV.
- **Traduzione italiana completa.**

## Diagnostica

- **Logger in-app** (Impostazioni → Avanzate → Diagnostica): registra i log dell'app per 24 ore, filtri per livello e per più categorie, aggiornamento in tempo reale, copia, condivisione come file, importazione del logcat del processo (Android). Segreti, token e configurazioni degli addon vengono oscurati prima del salvataggio.

## Sincronizzazione e impostazioni

- La **ricerca nelle impostazioni** copre tutte le opzioni e ignora maiuscole e accenti.
- Altre impostazioni sincronizzate col profilo: hero e sorgenti hero, opzioni del proxy multimediale, toggle del logger, preferenze Live TV, raggruppamento dei plugin, opzioni dello Stream Parser.
- Risolti gli ANR (blocchi dell'app) all'apertura delle Impostazioni mentre la Home stava caricando.

## Release e aggiornamenti

- Release pubblicate su [albyalex96/NuvioMobile-V2](https://github.com/albyalex96/NuvioMobile-V2/releases) con note di rilascio automatiche e APK separati per telefono e TV.
- Versione centralizzata in `version.json` con version code automatico.
- Identità propria dell'app (`com.nuvioenhanced.alby`, "Nuvio Enhanced"): si installa accanto all'app ufficiale.
- L'updater scarica le release da questa fork e sceglie l'APK giusto (telefono o TV) per l'app installata.
