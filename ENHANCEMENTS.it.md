# Nuvio Enhanced — Novità

Tutto ciò che Nuvio Enhanced aggiunge al [Nuvio](https://github.com/NuvioMedia/NuvioMobile) originale. Le funzioni dell'app originale (compresi Watch Party, liste MDBList e motore P2P) restano presenti e vengono integrate regolarmente; questa pagina elenca solo ciò che è specifico di questa fork.

🇬🇧 [English version](./ENHANCEMENTS.md) · [Torna al README](./README.md)

La maggior parte delle impostazioni è salvata per profilo e sincronizzata con l'account Nuvio, quindi ti segue su tutti i dispositivi.

---

## Indice

- [App Android TV](#app-android-tv)
- [Telefono come telecomando della TV](#telefono-come-telecomando-della-tv)
- [App desktop per Windows](#app-desktop-per-windows)
- [Live TV ed EPG](#live-tv-ed-epg)
- [Chromecast e proxy multimediale](#chromecast-e-proxy-multimediale)
- [Download](#download)
- [Player](#player)
- [Flussi](#flussi)
- [Integrazioni](#integrazioni)
- [Plugin CloudStream](#plugin-cloudstream)
- [Rete](#rete)
- [Aspetto e interfaccia](#aspetto-e-interfaccia)
- [Diagnostica](#diagnostica)
- [Sincronizzazione e impostazioni](#sincronizzazione-e-impostazioni)
- [Release e aggiornamenti](#release-e-aggiornamenti)

---

## App Android TV

Un'app dedicata ad Android TV, distribuita come APK separato (`Nuvio-Enhanced-Alby-TV-…`). Condivide tutta la logica dell'app per telefono (account, profili, sincronizzazione, flussi, player) e aggiunge un'interfaccia pensata per il telecomando, ispirata a [NuvioTV](https://github.com/NuvioMedia/NuvioTV).

- **Navigazione:** "sidebar moderna" in stile NuvioTV con una pillola flottante che mostra la sezione aperta, oppure una **barra di navigazione vetrosa in alto** (icone + nomi o solo icone, con bagliore opzionale).
- **Home:** hero "Moderno" di NuvioTV sotto la barra di navigazione con **carosello automatico** (fino a 2 cataloghi hero, sincronizzati col profilo), metadati arricchiti, voti e classificazione per età, sfondo a schermo intero mantenuto dietro tutte le righe. L'hero si può **espandere** (trama, pulsanti Riproduci e Libreria, Riproduci tenuto premuto per scegliere la sorgente a mano) e resta aperto mentre si scorrono le righe. Righe dimensionate e scorrevoli come in NuvioTV, "Continua a guardare" in tre stili (Card, Wide, Poster) con badge per tempo rimanente, prossimo episodio, nuovo episodio e nuova stagione, espansione opzionale della locandina col focus.
- **Pagina "Layout TV":** preset per larghezza locandine e raggio degli angoli, locandine orizzontali, etichette sotto le locandine, stile di "Continua a guardare", ritardo dell'espansione, sfondo hero a schermo intero, più le scorciatoie alle opzioni condivise di Home e "Continua a guardare".
- **Pagina dettagli:** sfondo a schermo intero, hero in stile NuvioTV, pulsante Play (OK tenuto = scelta della sorgente), pulsanti libreria/visto/shuffle, voti MDBList, locandine o schede di testo per le stagioni, card episodi orizzontali o a elenco (segue l'impostazione del telefono, con episodi non visti sfocati e trame espandibili), **sezione voti IMDb per episodio**, sinossi espandibile, menu azioni della stagione con pressione prolungata, cast cliccabile che apre la pagina della persona, collezione e titoli simili.
- **Menu opzioni delle card:** OK tenuto o tasto Menu su qualsiasi locandina o card di "Continua a guardare": dettagli, episodio a caso, riprendi/riproduci dall'inizio, scegli sorgente, segna come visto (senza sfarfallio in "Continua a guardare"), aggiungi/rimuovi dalla libreria, rimuovi da "Continua a guardare". Anche i flussi hanno il loro menu TV.
- **"Continua a guardare" nella schermata Home di Android TV:** la riga viene pubblicata nel launcher come canale "Continua a guardare" di Nuvio e nella riga "Riproduci successivo" (con avanzamento e immagine dell'episodio). Aprendo un elemento si può mostrare il menu opzioni della card (riprendi, scegli sorgente e riproduci, dettagli…), riprendere subito, aprire l'elenco dei flussi dell'episodio giusto o la pagina dettagli. Configurabile in Layout TV → Schermata Home di Android TV.
- **Telecomando nel player:** OK play/pausa, sinistra/destra per un seek cumulativo con anteprima (il passo cresce tenendo premuto), **miniature di anteprima** anche con i controlli nascosti, tasti multimediali, focus sulla barra di avanzamento, prompt "Salta intro"/"Prossimo episodio", prompt di ripresa compatto, pannelli e dialog che ricevono il focus da soli. Nel pannello canali della Live TV, OK tenuto premuto apre la guida EPG del canale.
- **Gestione del focus ovunque:** il focus segue la navigazione, non si perde nelle schede nascoste, le Impostazioni funzionano a livelli (categorie → pannello → sottopagine, Indietro risale di un livello), i dialog danno il focus al pulsante principale, i campi di testo aprono la tastiera solo premendo OK.
- **Accesso con codice QR** (stesso collegamento del dispositivo di NuvioTV), con email e password come alternativa.
- **Ricerca vocale** con il riconoscimento vocale di sistema e dall'Assistente Google, più un **provider di ricerca globale di Android TV** (risultati di Nuvio nella ricerca di sistema). Ricerche recenti sempre visibili come chip scorrevoli.
- Scorrimento centrato con bordi sfumati nelle Impostazioni, focus visibile nella selezione profilo e nei chip dei filtri.
- **Ottimizzazioni delle prestazioni:** baseline profile, caricamento immagini più leggero, scorrimento delle righe come NuvioTV, ripetizione del D-pad tenuto premuto limitata, sfocatura a bassa risoluzione al posto di quella in tempo reale, sfocatura facoltativa dietro la barra vetrosa, hero in pausa quando la Home non è visibile.

## Telefono come telecomando della TV

- **Telecomando per la riproduzione su Android TV** dal telefono: abbinamento con codice, scanner QR integrato o rilevamento automatico della TV in rete (indirizzo manuale come alternativa).
- Controlli in una notifica in background, sulla schermata di blocco e con i tasti del volume, mostrati in automatico quando la TV risulta online.
- Riquadro "Telecomando Nuvio" nelle impostazioni rapide per aprire (o chiudere) i controlli in notifica anche con l'app chiusa.
- **Accesso a Telegram sulla TV** fatto dal telefono abbinato.
- **Visualizzazione e gestione dei log della TV** dal telefono (Impostazioni → Avanzate → Diagnostica → Log TV, visibile quando c'è una TV associata).
- **Chat di Watch Party** sul telefono come companion.

## App desktop per Windows

Un'app per Windows costruita dallo stesso codice, distribuita come installer MSI con aggiornamenti dall'app.

- **Player libmpv** con scorciatoie da tastiera, volume con la rotellina del mouse, pulsante schermo intero, cursore che si nasconde da solo, controlli mostrati muovendo il mouse, anteprima al passaggio sulla barra di avanzamento con **miniature generate da FFmpeg**, prompt di ripresa compatto.
- Trailer nell'app e riproduzione del trailer nell'hero, modalità di riproduzione delle artwork animate.
- **Chromecast** tramite il protocollo Cast v2 (ricevitori trovati anche attraverso il firewall di Windows), con un **proxy multimediale LAN** per i flussi protetti da header e quelli locali.
- **Streaming P2P** con il motore Nuvio, **plugin scraper JS** su QuickJS.
- Cartella dei download, cartelle per episodio e download HLS con FFmpeg.
- Interfaccia pensata per il mouse: righe trascinabili col mouse, tasto destro come pressione prolungata, larghezza delle locandine, ingrandimento e anteprima al passaggio del mouse per dispositivo; il desktop conta come dispositivo a sé per le impostazioni del player per dispositivo.

## Live TV ed EPG

Portata da [luqmanfadlli/NuvioMobile-Enhanced](https://github.com/luqmanfadlli/NuvioMobile-Enhanced) ed estesa.

- Sorgenti: **playlist M3U** (URL o file locale), provider **Xtream** e **Stalker**.
- **EPG XMLTV** (`.xml` / `.xml.gz`), rilevata in automatico da playlist e provider, abbinata tramite `tvg-id` o nome del canale; il programma in onda è mostrato nel player.
- Scheda Live TV dedicata (telefono, tablet, TV e tab bar nativa iOS), preferiti, ricerca e categorie.
- **Schede delle sezioni** in cima alla scheda Live TV (Canali, Guida, Sport, On Demand), con icone e animazioni:
  - **Guida:** griglia della guida TV con tutti i canali sulla stessa linea del tempo, linea "adesso" che pulsa, riempimento di avanzamento sui programmi in onda e pulsante "Adesso" per tornare all'ora attuale. Toccando un programma in onda si apre il canale, altrimenti la guida del canale.
  - **Sport:** eventi in diretta e in programma di oltre 100 leghe e competizioni (calcio, basket, football americano, hockey, baseball, rugby, cricket, AFL, UFC, F1, MotoGP, tennis, golf e altri) da ESPN, HockeyTech (AHL, ECHL, PWHL, hockey giovanile), MLB Stats API (Minor League Baseball) e sporting-events.org (date degli eventi che ESPN non copre, come MotoGP, boxe, freccette e snooker), con badge LIVE lampeggiante, punteggi animati, conto alla rovescia nell'ultima ora, filtri per campionato e i **canali corrispondenti delle tue playlist** per guardare con un tocco. Le leghe da mostrare si scelgono in **Impostazioni → Sport**: leghe raggruppate per sport, con un interruttore per ogni lega, uno per l'intero sport e un pulsante per ripristinare le leghe predefinite; la scelta è sincronizzata con il profilo. Con i **promemoria partite** attivi (Impostazioni → Sport → Promemoria) ogni partita ha una campanella e il filtro **Promemoria** mostra solo le partite scelte; la notifica può arrivare all'inizio e/o 5, 15, 30 o 60 minuti prima (anche più momenti insieme), gli **avvisi durante la partita** facoltativi controllano il risultato ogni 5, 10, 15 o 30 minuti e avvisano a ogni cambio e al risultato finale, e un pulsante invia una notifica di prova. Toccando la notifica si apre Live TV → Sport (Android).
  - **On Demand:** film e serie dei provider **Xtream**, con categorie, ricerca, locandine e un pannello stagioni/episodi; i titoli si aprono nel player normale.
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
- **Download di intere stagioni** con cartelle per episodio, download raggruppati per titolo.
- **Sezione "Download in background":** stato dell'ottimizzazione batteria con scorciatoia, e consigli per i produttori con risparmio energetico aggressivo.

## Player

- **Intervallo di salto personalizzato** (1–20 secondi) per i pulsanti a schermo, il doppio tocco (cumulativo), la notifica/media session e i comandi remoti di iOS, con icona dinamica.
- **Prompt "Stai ancora guardando?"** (in stile Netflix): dopo un numero configurabile di episodi riprodotti in automatico, opzionalmente solo di notte (22:00–04:00), con un conto alla rovescia di 10 secondi prima della chiusura del player (o del ritorno alla pagina dettagli), con opzioni per dispositivo. Una versione separata a tempo per la **Live TV** (da 15 min a 6 h).
- **Miniature di anteprima** durante il seek per file singoli e flussi HLS, con opzioni separate per telefono e TV.
- **Pulsante picture-in-picture** nella barra in alto del player (telefoni Android con supporto PiP), oltre al PiP automatico quando si esce dall'app.
- **Barre di vetro:** con il Liquid Glass attivo, i pulsanti della barra del player (sottotitoli, audio, velocità, prossimo episodio…) stanno in una capsula di vetro e il pulsante di chiusura in un cerchio di vetro; seguono l'opzione "Sfondi di vetro sui controlli".
- **Vetro più leggero nel player:** il vetro dietro i controlli del player legge il video meno spesso e a risoluzione più bassa, e quasi si ferma col video in pausa: consuma meno batteria e la riproduzione resta fluida.
- **Controlli più puliti:** opzioni per togliere gli sfondi di vetro dietro ai pulsanti del player e ai tempi e per nascondere i pulsanti per tornare indietro e andare avanti (il doppio tocco per spostarsi funziona comunque).
- **Pulsanti del player personalizzabili:** nelle impostazioni di Riproduzione, "Pulsanti del player" permette di scegliere quali pulsanti restano sulla barra del player, quali stanno dietro la freccia e in che ordine (con ripristino). I pulsanti che non servono per il video attuale vengono saltati. Anche PiP, Chromecast e watch party della barra in alto si possono riordinare o nascondere.
- **Controlli più ricchi:** logo del titolo, badge dello Stream Parser, nome della sorgente, orologio e orario di fine sopra la barra di avanzamento.
- Cursori **Potenzia dialoghi**, **compressione della gamma dinamica** e **amplificazione volume** nel pannello audio (amplificazione fino a +10 dB, ricordata per ogni dispositivo di uscita audio).
- **Downmix stereo automatico in Bluetooth** (ExoPlayer, telefono e tablet): l'audio surround viene decodificato con FFmpeg e ridotto a uno stereo 2.0 pulito, così i dialoghi restano udibili con auricolari e casse.
- **Sottotitoli:** pipeline Nuvio libass ad atlante per ASS/SSA, stile dei sottotitoli applicato alle tracce ASS incorporate, pulsanti del ritardo sottotitoli che si ripetono tenendoli premuti e **sincronizzazione automatica** facoltativa per i sottotitoli degli addon, di OpenSubtitles e di SubDL (ASS/SSA abbinati tramite i dialoghi, ripiego su SRT/VTT).
- **Impostazioni dei sottotitoli per tipo di dispositivo:** stile, aspetto, lingue e opzioni libass sono separati per telefono, tablet, TV e Windows (sincronizzati col profilo) e le Impostazioni mostrano solo quelli del dispositivo in uso.
- **Riproduzione automatica per priorità dei provider:** l'auto-play sceglie il flusso seguendo l'ordine dei provider preferiti.
- I progressi di visione vengono salvati quando si esce dall'app.
- **Ritardo audio** (portato da NuvioTV): ±60 s a passi di 25 ms, ricordato **per uscita audio** (altoparlante, Bluetooth, HDMI…), con supporto all'hint `behaviorHints.audioDelayMs` di addon e plugin. Funziona con ExoPlayer e mpv.
- **Capitoli** (portati dalla fork di luqmanfadlli): segni dei capitoli sulla barra di avanzamento, titolo del capitolo corrente sopra la barra e pannello **Capitoli** per saltare a qualsiasi capitolo, su telefono, tablet e app TV. Letti dai file MKV/MP4 con ExoPlayer e direttamente da mpv (libmpv Android e iOS).
- **Visione privata** (portata dalla fork di luqmanfadlli): interruttore in Riproduzione → Player che smette di salvare progressi, Continua a guardare e stato "visto" e di inviare lo scrobbling ai tracker collegati, sia nel player interno sia per i risultati dei player esterni. Sincronizzata col profilo. Nel menu del flusso c'è anche **Guarda in privato**, per vedere in privato un singolo film o episodio (gli episodi successivi vengono registrati normalmente).
- **Rich Presence di Discord** (portata dalla fork di luqmanfadlli, telefono e TV Android): Impostazioni → Integrazioni → Discord mostra sul profilo Discord cosa stai guardando (titolo, episodio, locandina, riproduzione/pausa, tempo trascorso e rimanente, pulsante IMDb facoltativo, canale della Live TV facoltativo). Si accede con il login web di Discord; account e impostazioni della presence si sincronizzano col profilo Nuvio, quindi la TV usa l'account collegato sul telefono. Mai mostrata con la Visione privata. Usa il token dell'account come Kizzy, il che potrebbe violare i Termini di servizio di Discord.
- **Consigli a fine visione** (portati da NuvioTV): verso la fine di un film, o di una serie senza un episodio successivo, un pannello a schermo intero propone titoli simili presi dalla fonte di "Altri simili" (TMDB, Trakt o Simkl), con sfondo, logo, informazioni, **voti MDBList** e trama. Si possono scorrere fino a 12 titoli, con **Riproduci** (i film partono subito, le serie aprono la scheda), **Dettagli**, **Torna al player** e un **trailer** nel pannello che parte dopo un conto alla rovescia di 5 secondi (Android full e desktop). Nei film il pannello segue i titoli di coda e le scene dopo i titoli quando sono noti, altrimenti la soglia configurabile **Quando mostrare i consigli nei film** (80–100%); nelle serie segue la soglia del prossimo episodio. Funziona con il telecomando della TV e su desktop. Interruttore in Riproduzione → Riproduzione automatica, sincronizzato con il profilo.

## Flussi

- **Stream Parser:** una card alternativa dei flussi che legge qualsiasi descrizione e mostra badge della risoluzione con bagliore animato, sorgente, tag video/audio, canali, lingue audio (bandiere), dimensione (stimata se manca), stato cached/torrent/proxy, con un colore univoco per ogni etichetta.
- **Ordina per qualità video:** unisce tutti gli addon e plugin in un unico elenco ordinato da 8K a SD.
- **Menu azioni del flusso** (pulsante ⋮) con **"Descrizione originale"** per vedere e copiare il testo grezzo dell'addon.
- **Filtro delle qualità mostrate:** nasconde dall'elenco dei flussi le risoluzioni che non interessano.
- Trama completa nel pannello di scelta del flusso su tablet e TV.
- La preferenza dei plugin "raggruppa flussi per repository" ora è sincronizzata col profilo.

## Integrazioni

- **Pagina Integrazioni riorganizzata** con sezioni per metadati (TMDB, MDBList), timestamp, liste/tracker, sottotitoli ed extra, ogni servizio con la sua pagina.
- **Timestamp:** AniSkip, IntroDB e **SkipDB**, con ordine di priorità dei provider.
- **Tracker e liste:** **MyAnimeList**, **Kitsu**, **AniList** e **WeTrakr** (accesso con codice dispositivo), con sincronizzazione dell'accesso, integrazione nella libreria e **scelta della libreria per servizio**.
- **Telegram:** accesso e ricerca dei flussi su Telegram (immagini, GIF e clip brevi escluse).
- Integrazione dell'**Assistente AI**.
- Ogni integrazione è elencata in Impostazioni → Licenze e attribuzioni.

## Plugin CloudStream

- Runtime dei plugin CloudStream con archiviazione e sincronizzazione col profilo: i plugin forniscono sorgenti, cataloghi e, facoltativamente, pagine dettagli complete. Pagina delle impostazioni dedicata.

## Rete

- **DNS over HTTPS** (Impostazioni → Avanzate → Rete): Cloudflare, Google, Quad9, AdGuard, Mullvad, OpenDNS o un resolver personalizzato, ripiego sul DNS del dispositivo, pulsante di test e sincronizzazione facoltativa col profilo. Usato da tutti i client di rete dell'app (addon, immagini, download, proxy multimediale…).

## Aspetto e interfaccia

- **Modalità AMOLED completa:** toggle aggiuntivo che rende nere anche superfici e card.
- **Pulsanti trasparenti nei dettagli:** con lo sfondo cinematico o a colore dominante, pulsanti e chip della pagina dettagli (azioni, stagioni, trailer) diventano trasparenti e lasciano vedere l'artwork; disattivabile nelle impostazioni della pagina dettagli.
- **Pulsanti del profilo rotondi:** sul telefono modifica/cambia profilo diventano pulsanti rotondi con icona nell'intestazione del profilo e "Modifica profilo" salva da un pulsante rotondo in alto; si possono spegnere in Aspetto per tornare ai pulsanti larghi.
- **Scelta del tema compatta:** in Aspetto l'elenco dei temi è chiuso in una riga che mostra il tema attuale; toccandola si apre l'elenco completo.
- **Nuovi cursori:** i cursori delle impostazioni (riproduzione, stream, poster, colori del tema) hanno una barra più sottile e un pallino rotondo nel colore del tema.
- **Barra di navigazione Liquid Glass:** con il Liquid Glass attivo, la barra flottante può essere Liquida, Smerigliata, Trasparente o Solida, con sfocatura, rifrazione e tinta regolabili, interruttori per vividezza, bordo luminoso, ombra, aberrazione cromatica e lente liquida sotto la scheda selezionata, più un colore personalizzato per le icone non selezionate (pannello Barra di navigazione, con ripristino).
- **Effetti Liquid Glass:** toggle opzionale (spento di default, assente su TV) che trasforma in vetro liquido pannelli, controlli e pannelli del player, il pulsante per saltare, le schede e i filtri degli stream, rifrangendo il video o il contenuto sottostante. Con lo sfondo cinematico anche la schermata degli stream e la pagina dei dettagli (telefono) rifrangono l'artwork. Liquid Glass, vetro della barra di navigazione, pulsanti trasparenti e rotondi e le opzioni del player (sfondi di vetro, pulsanti per saltare, pulsanti del player) si sincronizzano con il profilo.
- **Liquid Glass su Windows:** anche l'app desktop ha l'opzione Liquid Glass: pannelli di vetro e barra di navigazione flottante con la selezione "jelly" e gli stili di vetro (senza il bagliore extra della barra del telefono). Anche i controlli del player sono di vetro e rifrangono una piccola copia del video, che quasi si ferma col video in pausa.
- **Formato data** a scelta (sistema, GG/MM/AAAA, MM/GG/AAAA, AAAA-MM-GG, GG MMM AAAA…) applicato a tutte le date dell'app.
- **Formato del codice episodio:** S1 E2, S01E02, S1E2, 01x02, 1x2.
- **Trame degli episodi espandibili** con layout animato in entrambi gli stili dell'elenco episodi.
- **Locandine orizzontali** applicate ovunque (griglie, cataloghi, libreria, ricerca, cartelle delle collezioni) senza mai tagliare le locandine verticali; le cartelle delle collezioni rispettano la propria forma.
- Stile delle card episodi **a elenco orizzontale**.
- **Pagina Account ridisegnata.**
- Nomi di paesi e lingue localizzati; sfondi e locandine TMDB nella sezione Trailer e immagini.
- **Pulsanti d'azione principali con gradiente** e icone (Riproduci, Riprendi, Scegli episodi, Visualizza dettagli).
- **Righe Top 10** nella Home (telefono, tablet e TV): due righe in classifica con i primi 10 titoli di un catalogo film e di un catalogo serie, con grandi numeri accanto alle locandine. Cataloghi scelti in Impostazioni → Scoperta contenuti → Top 10, sincronizzati per profilo.
- Nuova icona di navigazione per la Live TV.
- **Traduzione italiana completa.**

## Diagnostica

- **Logger in-app** (Impostazioni → Avanzate → Diagnostica): registra i log dell'app per 24 ore, filtri per livello e per più categorie, aggiornamento in tempo reale, copia, condivisione come file, importazione del logcat del processo (Android). Segreti, token e configurazioni degli addon vengono oscurati prima del salvataggio. Include una **traccia dei tempi di avvio**.
- **Avvio più veloce:** lo splash di sistema resta finché la Home non mostra i primi contenuti, e le operazioni pesanti (client Supabase, Cast, download salvati, controllo di Telegram, notifiche delle release) sono state tolte dal percorso critico.

## Sincronizzazione e impostazioni

- La **ricerca nelle impostazioni** copre tutte le opzioni e ignora maiuscole e accenti, con evidenziazione del risultato e filtro **"Solo funzioni Enhanced"**.
- Altre impostazioni sincronizzate col profilo: hero e sorgenti hero, opzioni del proxy multimediale, toggle del logger, preferenze Live TV, raggruppamento dei plugin, opzioni dello Stream Parser.
- Risolti gli ANR (blocchi dell'app) all'apertura delle Impostazioni mentre la Home stava caricando.

## Release e aggiornamenti

- Release pubblicate su [albyalex96/NuvioMobile-V2](https://github.com/albyalex96/NuvioMobile-V2/releases) con note di rilascio automatiche, APK separati per telefono e TV e un installer MSI per Windows.
- **Novità versioni precedenti:** selettore delle versioni con il changelog di ogni release precedente, impaginato come quello dell'ultima versione. Il banner dell'aggiornamento si nasconde mentre si guarda un video e ricompare nelle altre pagine.
- Pannello delle novità ridisegnato con le note della versione installata, download automatico degli aggiornamenti in background facoltativo (in test).
- Versione centralizzata in `version.json` con version code automatico.
- Identità propria dell'app (`com.nuvioenhanced.alby`, "Nuvio Enhanced"): si installa accanto all'app ufficiale.
- L'updater scarica le release da questa fork e sceglie l'APK giusto (telefono o TV) per l'app installata.
