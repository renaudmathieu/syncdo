---
marp: true
theme: default
paginate: true
title: "SyncDO : CRDTs & synchronisation temps réel en Kotlin Multiplatform"
author: "Renaud Mathieu"
duration: 45min
---

<!--
========================================================================
PRÉSENTATION SyncDO : 45 MINUTES
========================================================================
Format : Markdown compatible Marp / Reveal.js / Pandoc → PowerPoint.
Chaque `---` = nouveau slide. Le bloc "Notes:" en bas de chaque slide est
destiné au présentateur (peut être collé dans les notes PPT).

Découpage temporel indicatif (≈48 slides + 4 annexes, ≈1 min/slide) :
  • Partie 1 : Le problème               (5 min, slides 1-5)
  • Partie 2 : Théorie CRDT              (6 min, slides 6-11)
  • Partie 3 : Module :crdt              (11 min, slides 12-20)
  • Partie 4 : Module :sync              (10 min, slides 21-31)
  • Partie 5 : Sample Todo & KMP         (8 min, slides 32-40)
  • Partie 6 : Bilan & Q/R               (5 min, slides 41-48)
========================================================================
-->

# **SyncDO**
## CRDTs & synchronisation temps réel en Kotlin Multiplatform

Une architecture *offline-first*, deux librairies publiables, un sample Todo

Renaud Mathieu, 2026

Notes:
- Bienvenue. Aujourd'hui : on construit (et on dissèque) un système de synchronisation collaborative basé sur les CRDTs, en pur Kotlin Multiplatform.
- Objectif de la session : repartir avec une compréhension claire de **pourquoi** les CRDTs résolvent le problème de la sync collaborative, et **comment** on en fait une implémentation propre, testable et publiable.
- Pas de magie, pas de framework : ~2000 lignes de Kotlin.

---

# Plan

1. **Le problème** : pourquoi la sync est dure
2. **La théorie** : CRDTs, semi-treillis, causalité
3. **Le module `:crdt`** : primitives génériques
4. **Le module `:sync`** : protocole WebSocket
5. **L'intégration** : sample Todo offline-first
6. **Bilan** : ce qu'on retient

Notes:
- 45 minutes, ~48 slides, on garde du temps pour les questions à la fin.
- Le code est ouvert, on s'y réfèrera tout du long.

---

# Partie 1 : Le problème

Notes:
- 5 minutes sur le problème. Ne pas plonger trop vite dans la solution.

---

# Le scénario

Deux utilisateurs. Une même liste. Un réseau instable.

- Alice ajoute « Acheter du pain » (hors-ligne, métro)
- Bob coche « Faire la lessive » (en ligne)
- Bob renomme « Faire la lessive » en « Lessive » (en ligne)
- Alice revient en ligne 10 minutes plus tard

**Que doit-il se passer ?**

Notes:
- C'est le scénario universel des apps collab : Notion, Linear, Figma, Apple Notes, Trello…
- Le serveur ne peut pas être l'unique source de vérité quand le client est offline.

---

# Les fausses bonnes idées

| Approche | Problème |
|---|---|
| « Last write wins » sur l'objet entier | Bob écrase l'ajout d'Alice |
| Locks / verrous distribués | Impossible offline |
| Operational Transform (OT) | Complexité quadratique, dur à prouver correct |
| Timestamps clients « naïfs » | Horloges désynchronisées |

→ Il nous faut une structure qui **converge mathématiquement**, sans coordination.

Notes:
- OT est ce que Google Docs a utilisé pendant longtemps. Très puissant, mais une dette technique énorme : chaque opération doit être "transformée" contre toutes les opérations concurrentes.
- Les CRDTs offrent une garantie plus forte : pas de transformation nécessaire, juste un `merge` algébrique.

---

# Partie 2 : La théorie

CRDTs, semi-treillis, causalité

Notes:
- 7 minutes de théorie. On reste pragmatique : juste ce qu'il faut pour comprendre les choix d'implémentation.

---

# CRDT : la définition courte

> **Conflict-free Replicated Data Type**
> Une structure de données dont l'opération `merge` est :
>
> - **Commutative** : `a.merge(b) = b.merge(a)`
> - **Associative** : `(a.merge(b)).merge(c) = a.merge(b.merge(c))`
> - **Idempotente** : `a.merge(a) = a`

→ L'ordre des messages n'a pas d'importance.
→ Les doublons ne posent pas de problème.
→ Tous les réplicas convergent vers le même état final.

Notes:
- C'est la magie. Avec ces trois propriétés, on n'a plus besoin de coordination distribuée : on peut envoyer les mêmes deltas plusieurs fois, dans n'importe quel ordre, et tout le monde finit avec le même état.
- Mathématiquement, ce sont des **join-semilattices**.

---

# Deux familles de CRDT

**State-based (CvRDT)**
- Échange l'état complet
- Merge = jointure du semi-treillis
- Simple, robuste, mais coûteux en réseau

**Operation-based (CmRDT)**
- Échange des opérations / deltas
- Plus efficace, mais exige une livraison fiable

**SyncDO = delta-state CRDT** : on échange des *deltas* qui se mergent comme des états → meilleur des deux mondes.

Notes:
- Delta-state CRDT est l'approche moderne (papier de Almeida & Shoker, 2018).
- On a la compacité réseau des CmRDT et la robustesse algébrique des CvRDT.

---

# Causalité : la VectorClock

Comment savoir si un événement s'est produit *avant*, *après*, ou *en parallèle* d'un autre ?

```
Nœud A :  ●─────●──────●         clock A = {A:3}
                       │
                       ▼
Nœud B :         ●────●────●     clock B = {A:2, B:2}
```

Une **VectorClock** = `Map<NodeId, Long>`
→ comparer = comparer composante par composante

Notes:
- Inventées par Mattern & Fidge en 1988.
- C'est la pièce maîtresse de la causalité distribuée. Sans horloge vectorielle, on ne peut pas distinguer un événement "ancien" d'un événement "concurrent".

---

# Trois relations possibles

```
A = {nodeA: 3, nodeB: 1}     B = {nodeA: 2, nodeB: 2}

A ≤ B ?   nodeA: 3 ≤ 2 ✗                    → NON
B ≤ A ?   nodeA: 2 ≤ 3 ✓   nodeB: 2 ≤ 1 ✗   → NON
─────────────────────────────────────────────────────
→ Ni A ≤ B, ni B ≤ A : événements CONCURRENTS
```

C'est exactement ce que fait `VectorClock.isLessThanOrEqual()` dans `:crdt`.

Notes:
- "Concurrent" = ni avant, ni après : c'est là que les conflits se produisent.
- Une seule fonction suffit : "A avant B" = `A ≤ B` et `A ≠ B`. L'audit a supprimé `dominates`, qui n'était utilisée nulle part.
- La résolution d'un conflit concurrent est ce qui distingue les types de CRDT (LWW, OR-Set, Counter…).

---

# Les briques qu'on va construire

1. **VectorClock** : causalité distribuée
2. **LwwRegister<T>** : dernière écriture gagne (avec tiebreak)
3. **OrSet<E>** : ensemble *observed-remove* (add-wins)
4. **mergeStates** : une `Map` de CRDTs est un CRDT

Chacune est un join-semilattice. **Et leur composition l'est aussi.**

Une seule règle pour les deltas : **un delta est un petit état du même type.**

Notes:
- Point clé pour la suite : la composition de join-semilattices reste un join-semilattice. C'est ça qui rend le système extensible sans reprouver la convergence à chaque ajout.
- Pas de type "delta" à part : on verra que ça supprime beaucoup de code.

---

# Partie 3 : Module `:crdt`

5 fichiers, 289 lignes, dont **78 lignes de code**. Tout le reste est de la documentation.

Aucune dépendance applicative (pas même les coroutines). Multiplatform.

Notes:
- 12 minutes ici. C'est le cœur technique.
- Le module est publiable sur Maven et ne contient aucune référence à Todo, Sync, WebSocket. Pure algèbre.
- Ordre de lecture conseillé : `CrdtState.kt`, `VectorClock.kt`, `LwwRegister.kt`, `OrSet.kt`, `MapMerge.kt`.

---

# `CrdtState<T>` : l'interface minimale

```kotlin
interface CrdtState<T : CrdtState<T>> {
    fun merge(other: T): T
}
```

- C'est la **seule** interface du module
- Type auto-référencé `<T : CrdtState<T>>` → force la cohérence à l'appel
- Aucune contrainte sur `equals`/`hashCode`
- Le contrat de commutativité/associativité/idempotence est **implicite** mais testé

Notes:
- C'est volontairement minimal. Plus on ajoute de méthodes à l'interface, plus on contraint les implémentations.
- Les tests (commun à tous les CRDTs) vérifient les trois propriétés sur des cas variés.

---

# `VectorClock` : 4 fonctions

```kotlin
@Serializable
data class VectorClock(val entries: Map<NodeId, Long> = emptyMap())
    : CrdtState<VectorClock> {

    operator fun get(nodeId: NodeId): Long = entries.getOrElse(nodeId) { 0L }

    fun increment(nodeId: NodeId): VectorClock =
        copy(entries = entries + (nodeId to this[nodeId] + 1))

    override fun merge(other: VectorClock): VectorClock =
        VectorClock((entries.keys + other.entries.keys).associateWith { maxOf(this[it], other[it]) })

    fun isLessThanOrEqual(other: VectorClock): Boolean =
        (entries.keys + other.entries.keys).all { this[it] <= other[it] }
}
```

**Merge = max composante par composante.** Trivialement commutatif/associatif/idempotent.

Notes:
- Représentation `sparse` : on ne stocke que les nœuds actifs. Si un nouveau nœud apparaît, sa valeur implicite est 0.
- `isLessThanOrEqual` est utilisé côté serveur par le `DeltaLog` pour décider quels deltas envoyer à un client qui se reconnecte.
- Une horloge vectorielle est elle-même un CRDT (une map de "max").

---

# `LwwRegister<T>` : Last-Writer-Wins

```kotlin
@Serializable
data class LwwRegister<T>(
    val value: T,
    val timestamp: Instant,
    val nodeId: NodeId,
) : CrdtState<LwwRegister<T>> {

    override fun merge(other: LwwRegister<T>): LwwRegister<T> = when {
        timestamp > other.timestamp -> this
        timestamp < other.timestamp -> other
        nodeId >= other.nodeId -> this   // tiebreak lexicographique
        else -> other
    }
}
```

- **Tiebreak déterministe** : même timestamp ? Le nodeId gagne lexicographiquement
- Convergence garantie même en cas d'horloges identiques
- Hypothèse : un nœud n'écrit jamais deux valeurs différentes au même instant (sinon `merge` n'est plus commutatif)

Notes:
- Utilise `kotlin.time.Instant` (Kotlin 2.1+), donc aucune dépendance externe à `kotlinx-datetime`.
- Trade-off : un LWW perd les écritures concurrentes (par design). Pour conserver la donnée, il faut un autre type CRDT (Multi-Value Register).

---

# `OrSet<E>` : le cœur du sujet

> **Problème** : un add concurrent à un remove, qui gagne ?

```kotlin
@Serializable
data class UniqueTag(val nodeId: NodeId, val counter: Long)

@Serializable
data class OrSet<E>(
    val entries:    Map<E, Set<UniqueTag>> = emptyMap(),  // actifs
    val tombstones: Map<E, Set<UniqueTag>> = emptyMap(),  // supprimés
) : CrdtState<OrSet<E>>
```

Chaque `add` crée un **tag unique** ; chaque `remove` *tombstone* les tags **observés à ce moment**.

**Invariant** : `entries` ne contient que des tags vivants, donc `contains(e) = e in entries`.

Notes:
- C'est l'OR-Set (Observed-Remove Set) classique de Shapiro et al. (2011).
- Le nom est important : on ne supprime que ce qu'on a "observé", donc les ajouts concurrents (avec un nouveau tag) survivent.

---

# OrSet : Add-wins illustré

```
Réplica A                      Réplica B
─────────────────────────────────────────────────────
t1:  add("X", A, 1)            (vide)
     entries = {X: {A:1}}

t2:  remove("X")               add("X", B, 1)
     entries = {}              entries = {X: {B:1}}
     tombs   = {X: {A:1}}

t3:  ─── MERGE ───
     entries    = {X: {B:1}}   ← le nouveau tag survit
     tombstones = {X: {A:1}}
     contains("X") = TRUE
```

Le merge prend **l'union** des tags et **l'union** des tombstones, puis filtre.

Notes:
- C'est *le* cas d'usage qui motive l'OR-Set. Avec un G-Set ou un 2P-Set, on perdrait l'ajout de B.
- En pratique : "j'ajoute un item pendant qu'un collègue supprime la même chose" → on garde l'ajout. Comportement intuitif côté UX.

---

# Un delta est un petit état

```kotlin
// Le delta : un OrSet qui ne contient QUE le changement
fun addDelta(element: E, tag: UniqueTag): OrSet<E> =
    OrSet(entries = mapOf(element to setOf(tag)))

fun removeDelta(element: E): OrSet<E> {
    val observedTags = entries[element] ?: return OrSet()
    return OrSet(tombstones = mapOf(element to observedTags))
}

// Une mutation locale = merge du delta
fun add(element: E, tag: UniqueTag) = merge(addDelta(element, tag))
fun remove(element: E) = merge(removeDelta(element))
```

- Pas de type `Delta` à part : **même type, même `merge`**
- On applique le delta localement **et** on envoie la même valeur aux autres
- Deux deltas se combinent avec... `merge` : `δ1.merge(δ2)`

Notes:
- Avant l'audit : `Delta`, `DeltaState`, `OrSetDelta`, `applyDelta`, `toDelta`… cinq notions pour une seule idée.
- C'est la définition même des delta-state CRDTs (Almeida et al.) : un delta est un élément du même semi-treillis.
- Les deltas se composent : 50 frappes de l'utilisateur hors-ligne → un seul delta à envoyer.

---

# `MapMerge` : composer des CRDTs

```kotlin
// Public : "une Map de CRDTs est un CRDT"
fun <K, V : CrdtState<V>> Map<K, V>.mergeStates(other: Map<K, V>): Map<K, V> =
    mergeValues(other) { a, b -> a.merge(b) }

// Internes, utilisés par OrSet
internal fun <K, T> Map<K, Set<T>>.unionValues(other: Map<K, Set<T>>): Map<K, Set<T>>
internal fun <K, V : Any> Map<K, V>.mergeValues(other: Map<K, V>, merge: (V, V) -> V): Map<K, V>
```

> **« Une `Map<K, CrdtState>` est elle-même un CRDT. »**

→ C'est ainsi qu'on construit `TodoListCrdt` à partir de briques.

Notes:
- C'est le secret du passage à l'échelle : on assemble des CRDTs composites sans reprouver la convergence à chaque fois.
- En théorie des catégories : les join-semilattices sont fermés sous le produit.
- Seul `mergeStates` est public : moins d'API à documenter et à maintenir.

---

# Tests : prouver la convergence (≈ 900 lignes)

| Suite | Lignes | Ce qu'on vérifie |
|---|---|---|
| `VectorClockTest` | 68 | merge commut/assoc/idemp + `isLessThanOrEqual` |
| `LwwRegisterTest` | 60 | tiebreak déterministe, convergence concurrente |
| `OrSetTest` | 137 | **add concurrent → add wins**, lois du merge, deltas |
| `TodoListCrdtTest` | 165 | merge composite, delta = état |
| `SerializationRoundTripTest` | 61 | wire format JSON stable |
| `DeltaBufferTest` | 41 | deltas en attente fusionnés, push raté |
| `SyncServerTest` | 47 | catch-up : rien / deltas manqués / état complet |
| `SyncRouteTest` | 122 | broadcast Ktor, intégration WS |

**Pas de mocks.** Tout est testé avec les vrais types.

Notes:
- La stratégie de test ici est cruciale : on teste les propriétés mathématiques explicitement (commutativité, associativité, idempotence).
- `SerializationRoundTripTest` est un test de régression : si on change le format JSON, le test casse → pas de break silencieux côté clients déjà déployés.
- `SyncServerTest` couvre les deux bugs trouvés pendant l'audit (voir `DeltaLog`).

---

# Partie 4 : Module `:sync`

Le protocole, le client, le serveur.

469 lignes.

Notes:
- 10 minutes ici. On a tout ce qu'il faut pour la sync : on a juste besoin d'un transport.

---

# Le protocole : `SyncMessage<D>`

```kotlin
@Serializable
sealed interface SyncMessage<D> {

    @SerialName("push_delta")
    data class PushDelta<D>(val delta: D, val nodeId: NodeId) : SyncMessage<D>

    @SerialName("pull_request")
    data class PullRequest<D>(val clock: VectorClock, val nodeId: NodeId) : SyncMessage<D>

    @SerialName("pull_response")
    data class PullResponse<D>(val delta: D) : SyncMessage<D>
}
```

3 messages. Sérialisation polymorphe avec discriminant.

Notes:
- C'est un protocole *push + catch-up* :
  - Push = "voici mes mutations"
  - Pull = "dis-moi ce que j'ai manqué depuis cette horloge"
  - PullResponse = "voici tes deltas (ou l'état complet)"
- Format JSON sur WebSocket : lisible, debuggable, versionnable.

---

# `ClockedState<S>` : ce dont la sync a besoin

```kotlin
interface ClockedState<S : ClockedState<S>> : CrdtState<S> {
    val clock: VectorClock
}
```

- Un CRDT **+ une horloge vectorielle**, rien de plus
- Le **même type** sert d'état complet et de delta
- L'horloge vit dans `:sync` : `:crdt` reste de l'algèbre pure

Notes:
- Avant l'audit, `clock` était dans les interfaces de `:crdt` (`Delta`, `DeltaState`) alors qu'aucun CRDT ne s'en sert pour fusionner : LWW utilise des `Instant`, l'OR-Set des tags.
- C'est le seul besoin du serveur : savoir quels deltas un client n'a pas encore vus.

---

# `DeltaBuffer<D>` : la file d'attente offline

```kotlin
class DeltaBuffer<D : CrdtState<D>> {
    private val mutex = Mutex()
    private var pending: D? = null

    suspend fun record(delta: D): Unit = mutex.withLock {
        pending = pending?.merge(delta) ?: delta
    }

    suspend fun flush(): D? = mutex.withLock {
        pending.also { pending = null }
    }
}
```

→ Un seul delta en attente, fusionné en continu. Coroutine-safe.

Notes:
- C'est le buffer offline. Quand on est déconnecté, toutes les mutations s'accumulent dans un unique delta grâce à la compositivité du merge.
- Envoi raté ? On ré-enregistre le delta avec `record` : `merge` est commutatif, l'ordre ne compte pas. Plus besoin de `restore`.
- Ce n'est pas un CRDT mais une préoccupation de sync : il a quitté `:crdt`, qui n'a donc plus besoin des coroutines.

---

# `SyncEngine<D>` : le client (151 lignes)

```kotlin
class SyncEngine<D : ClockedState<D>>(
    deltaSerializer: KSerializer<D>,
    private val serverHost: String,
    private val serverPort: Int,
    private val nodeId: NodeId,
    private val scope: CoroutineScope,
    private val onRemoteDelta:        suspend (D) -> Unit,
    private val getPendingDelta:      suspend () -> D?,
    private val restorePendingDelta:  suspend (D) -> Unit,
    private val getLocalClock:        () -> VectorClock,
    private val logger: SyncLogger = SyncLogger.Noop,
)
```

4 callbacks → **zéro couplage** avec le repository applicatif.

Notes:
- Le moteur ne sait *rien* du domaine : pas de TodoListCrdt. Juste `D : ClockedState<D>` et 4 fonctions suspendues.
- C'est ce qui le rend publiable et réutilisable pour n'importe quelle app.

---

# Cycle de vie d'une connexion

```kotlin
fun start() {
    connectJob = scope.launch {
        var backoff = 1_000L
        while (isActive) {
            try {
                client.webSocket(...) {
                    backoff = 1_000L
                    send(PullRequest(getLocalClock(), nodeId))   // 1. catch-up
                    pushPendingDelta()                           // 2. flush buffer
                    syncStatus.update { Synced }
                    for (frame in incoming) handleMessage(...)   // 3. boucle
                }
            } catch (e: Exception) { syncStatus.update { Offline } }
            delay(backoff)
            backoff = (backoff * 2).coerceAtMost(30_000L)        // 4. backoff
        }
    }
}
```

**Backoff exponentiel** 1s → 2s → 4s → … → 30s, **reset** sur succès.

Notes:
- L'ordre est important : pull D'ABORD, push ENSUITE.
  - On rattrape ce qu'on a manqué pendant qu'on était offline, AVANT d'envoyer nos changements.
  - Sinon on pourrait écraser des données plus récentes côté serveur (en LWW).
- Le backoff évite le thundering herd en cas de crash serveur.

---

# `SyncStatus` : un StateFlow observable

```kotlin
enum class SyncStatus { Synced, Syncing, PendingChanges, Offline, Error }
```

```
                    ┌──────────────┐
   (local edit)     │   Synced     │
   ┌───────────────►│              │
   │                └──────┬───────┘
   │                       │ (connection lost)
   │                       ▼
┌─────────────────┐    ┌──────────┐
│ PendingChanges  │◄───┤ Offline  │
└────────┬────────┘    └──────────┘
         │ (reconnect)       ▲
         ▼                   │
    ┌─────────┐  (fail)      │
    │ Syncing ├──────────────┘
    └────┬────┘
         │ (ack)
         ▼
       Synced
```

L'UI peut s'abonner → indicateur visuel "cloud / cloud-off / spinner".

Notes:
- `StateFlow` : la valeur actuelle est toujours disponible + nouvel abonné reçoit immédiatement la dernière valeur.
- Utile pour : badge "non synchronisé", spinner pendant un push, banner "hors-ligne".

---

# `SyncServer<S>` : état autoritaire

```kotlin
class SyncServer<S : ClockedState<S>>(
    initialState: S,
    maxLogSize: Int = 100,
    private val onStateChanged: suspend (S) -> Unit = {},
) {
    private val mutex = Mutex()
    private var state: S = initialState
    private val deltaLog = DeltaLog<S>(initialState.clock, maxLogSize)

    suspend fun mergeDelta(delta: S): Unit = mutex.withLock {
        state = state.merge(delta)
        deltaLog.append(delta)
        onStateChanged(state)
    }

    suspend fun getDeltaSince(clientClock: VectorClock): S? = mutex.withLock {
        when {
            state.clock.isLessThanOrEqual(clientClock) -> null        // client à jour
            deltaLog.covers(clientClock) -> deltaLog.missedSince(clientClock)
            else -> state                                              // état complet
        }
    }
}
```

Notes:
- `mutex` sérialise les merges : un seul à la fois → pas de race condition sur `state`.
- `onStateChanged` invoqué SOUS le lock → la persistance est cohérente avec l'état logique.
- Le serveur fait exactement comme un client : `state.merge(delta)`. Un état complet est un delta valide, donc plus besoin de `fullStateAsDelta`.

---

# `DeltaLog<D>` : catch-up borné

```kotlin
internal class DeltaLog<D : ClockedState<D>>(baseClock: VectorClock, private val maxSize: Int) {
    private val deltas = ArrayDeque<D>()
    private var evictedClock = baseClock   // tout ce qui n'est plus dans le log

    fun append(delta: D) {
        deltas.addLast(delta)
        while (deltas.size > maxSize) evictedClock = evictedClock.merge(deltas.removeFirst().clock)
    }

    fun covers(clientClock: VectorClock) = evictedClock.isLessThanOrEqual(clientClock)

    fun missedSince(clientClock: VectorClock): D? = deltas
        .filterNot { it.clock.isLessThanOrEqual(clientClock) }
        .reduceOrNull { acc, delta -> acc.merge(delta) }
}
```

- Ring buffer borné (mémoire constante)
- **Deux bugs trouvés pendant l'audit** :
  1. `null` voulait dire à la fois « rien de manqué » et « log trop court » → l'état complet était renvoyé après **chaque** push
  2. La troncature n'était jamais détectée → un client trop en retard **perdait** les deltas évincés

Notes:
- 100 deltas = compromis : assez pour rattraper un client offline quelques minutes, pas assez pour exploser la mémoire.
- `evictedClock` résume tout ce qui est sorti du log (et l'état chargé au démarrage). Si le client ne l'a pas vu, on lui renvoie l'état complet. Toujours correct grâce à l'idempotence.

---

# `syncEndpoint` : le handler Ktor (78 lignes)

```kotlin
webSocket(path) {
    connections += this
    try {
        for (frame in incoming) {
            val message = json.decodeFromString(serializer, frame.readText())
            when (message) {
                is PushDelta -> {
                    server.mergeDelta(message.delta)             // 1. fusionner
                    broadcastToOthers(message.delta)             // 2. diffuser
                    sendMissed(server.getDeltaSince(message.delta.clock)) // 3. rattraper l'envoyeur
                }
                is PullRequest -> sendMissed(server.getDeltaSince(message.clock)) // 4. répondre
                is PullResponse -> Unit                          // ignoré côté serveur
            }
        }
    } finally { connections -= this }
}
```

Notes:
- Un client qui push reçoit aussi un catch-up : il peut avoir manqué les broadcasts pendant que son delta voyageait.
- `sendMissed` n'envoie rien si le client est à jour (`getDeltaSince` renvoie `null`).
- Les peers morts sont retirés à la première erreur d'envoi (pas de fuite de connexions).
- Snapshot des peers sous lock, envoi hors du lock → pas de deadlock.

---

# Diagnostic : `SyncLogger` (20 lignes)

```kotlin
enum class SyncLogLevel { Debug, Info, Warn, Error }

fun interface SyncLogger {
    fun log(level: SyncLogLevel, message: String, error: Throwable?)
    companion object { val Noop: SyncLogger = SyncLogger { _, _, _ -> } }
}
```

- `fun interface` → syntaxe lambda
- **Zéro dépendance de logging** dans `:crdt` et `:sync`
- L'host fournit l'adapter : `println`, `NSLog`, `Logcat`, `Logback`…

Notes:
- Design important pour une lib publiable : on ne force JAMAIS le consommateur à dépendre de SLF4J, de Logback, ou de n'importe quel logger spécifique.
- Le défaut `Noop` permet l'utilisation silencieuse.

---

# Partie 5 : L'intégration

Sample Todo + Kotlin Multiplatform

Notes:
- 7 minutes. On montre que les briques s'assemblent en quelque chose d'utilisable.

---

# Composition : `TodoItemCrdt` (49 lignes)

```kotlin
@Serializable
data class TodoItemCrdt(
    val id: String,                          // immuable
    val title:     LwwRegister<String>,      // CRDT
    val note:      LwwRegister<String>,      // CRDT
    val completed: LwwRegister<Boolean>,     // CRDT
    val position:  LwwRegister<Double>,      // CRDT
    val createdAt: Instant,                  // immuable
) : CrdtState<TodoItemCrdt> {
    override fun merge(other: TodoItemCrdt) = TodoItemCrdt(
        id        = id,
        title     = title.merge(other.title),
        note      = note.merge(other.note),
        completed = completed.merge(other.completed),
        position  = position.merge(other.position),
        createdAt = minOf(createdAt, other.createdAt),
    )
}
```

→ 4 LwwRegisters mergés *en parallèle*. Pas de conflit champ-à-champ.

Notes:
- Granularité par champ : Alice peut renommer un item pendant que Bob le coche. Les deux changements coexistent. Pas de "qui écrase qui".
- `createdAt = min` : on garde la date de création la plus ancienne (déterministe).

---

# Composition : `TodoListCrdt`

```kotlin
@Serializable
data class TodoListCrdt(
    val itemIds: OrSet<String> = OrSet(),                        // qui existe
    val items:   Map<String, TodoItemCrdt> = emptyMap(),         // détail
    override val clock: VectorClock = VectorClock(),             // causalité
) : ClockedState<TodoListCrdt> {

    override fun merge(other: TodoListCrdt) = TodoListCrdt(
        itemIds = itemIds.merge(other.itemIds),
        items   = items.mergeStates(other.items),
        clock   = clock.merge(other.clock),
    )
}
```

**C'est aussi son propre type de delta** : un delta = un petit `TodoListCrdt`.

Notes:
- 3 briques : OR-Set pour l'appartenance, Map<CRDT> pour les détails, VectorClock pour la causalité.
- Avant l'audit, un `filterKeys` effaçait les données des items absents de l'OR-Set. Deux deltas qui ne modifiaient que des items se faisaient donc effacer en se combinant dans le buffer, et le merge n'était plus associatif.
- Maintenant les données des items supprimés restent, comme des tombstones. L'UI n'affiche que `itemIds.elements()`.

---

# `OfflineFirstTodoRepository` (132 lignes)

```kotlin
override suspend fun addTodo(title: String) = mutate { state ->
    val id = Uuid.random().toString()
    val clock = state.clock.increment(nodeId)
    val tag = UniqueTag(nodeId, clock[nodeId])   // notre compteur ne se répète jamais
    val item = TodoItemCrdt.create(id, title, nextPosition(state), nodeId, Clock.System.now())
    state.commit(TodoListCrdt(itemIds = state.itemIds.addDelta(id, tag), items = mapOf(id to item), clock = clock))
}

private suspend fun TodoListCrdt.commit(delta: TodoListCrdt): TodoListCrdt {
    deltaBuffer.record(delta)   // pour la sync
    return merge(delta)         // en local
}
```

Construire un delta → l'enregistrer → le merger → sauver sur disque → push (si connecté).

Notes:
- Toutes les mutations suivent ce schéma. Le même delta sert à la fois en local et pour le réseau.
- Le compteur de tag de l'OR-Set est l'entrée de notre propre horloge vectorielle : unique par construction, plus besoin d'un compteur à part.
- Tout se passe sous un mutex pour éviter qu'une mutation locale et un merge distant se marchent dessus.

---

# Le flux de données complet

```
┌────────────────────────────────────────────────────────────────┐
│  USER ACTION (composeApp UI)                                   │
└──────────────────────────────┬─────────────────────────────────┘
                               ▼
┌────────────────────────────────────────────────────────────────┐
│  TodoViewModel → TodoRepository.addTodo(...)                   │
└──────────────────────────────┬─────────────────────────────────┘
                               ▼
┌────────────────────────────────────────────────────────────────┐
│  OfflineFirstTodoRepository.mutate { ... }                     │
│    1. deltaBuffer.record(delta)                                │
│    2. crdtState.update(state.merge(delta))  (StateFlow → UI)   │
│    3. storage.save(newState)             (JSON sur disque)     │
└──────────────────────────────┬─────────────────────────────────┘
                               ▼
┌────────────────────────────────────────────────────────────────┐
│  SyncEngine.pushPendingDelta()  ──[WebSocket]──►  Server       │
│                                                     │          │
│                            ┌────────────────────────┘          │
│                            ▼                                   │
│  ┌──────────────────────────────────────────────────────┐      │
│  │ SyncServer.mergeDelta + broadcast to other peers     │      │
│  └──────────────────────────────────────────────────────┘      │
│                            │                                   │
│  Other clients ◄───[PullResponse]───┘                          │
│       │                                                        │
│       ▼                                                        │
│  onRemoteDelta → repository.mergeRemoteDelta → UI update       │
└────────────────────────────────────────────────────────────────┘
```

Notes:
- C'est *le* slide à montrer en démo si possible. Idéalement : démo live à 3 clients (desktop, Android, iOS) + serveur.
- À noter : la persistance disque arrive AVANT l'envoi réseau. Si l'app crash, l'état est sauf.

---

# Kotlin Multiplatform : le setup

```kotlin
kotlin {
    androidTarget { publishLibraryVariants("release") }
    iosArm64()
    iosSimulatorArm64()
    jvm()

    applyDefaultHierarchyTemplate()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.serialization.json)   // :crdt n'a besoin de rien d'autre
        }
    }
}
```

| Cible | Artefact |
|---|---|
| `androidTarget` | `.aar` (Android library) |
| `iosArm64` | framework natif iPhone |
| `iosSimulatorArm64` | framework simulateur (Mac M-series) |
| `jvm` | `.jar` standalone (desktop / serveur) |

Notes:
- Une seule codebase, quatre artefacts. Pas de duplication.
- Le module `:sync` a EN PLUS un `jvmMain` qui contient `SyncServer` + `syncEndpoint` (Ktor), non publié sur iOS car inutile.

---

# `expect` / `actual` : IO portable

```kotlin
// commonMain
expect suspend fun readTextFile(path: String): String?
expect suspend fun writeTextFile(path: String, content: String)
expect fun getStoragePath(): String
```

```kotlin
// androidMain / jvmMain
actual suspend fun writeTextFile(path: String, content: String) = withContext(IO) {
    val tmp = File("$path.tmp"); tmp.writeText(content)
    Files.move(tmp.toPath(), File(path).toPath(), ATOMIC_MOVE, REPLACE_EXISTING)
}
```

```kotlin
// iosMain
actual suspend fun writeTextFile(path: String, content: String) = withContext(IO) {
    (content as NSString).writeToFile(path, atomically = true, ...)
}
```

→ Écriture **atomique** sur chaque plateforme (pas de fichier corrompu si crash).

Notes:
- Le pattern `tmp + move` côté JVM/Android garantit l'atomicité même si on tire la prise pendant l'écriture.
- iOS le fait nativement avec `atomically = true`.

---

# Binary compatibility : `apiCheck`

```bash
./gradlew :crdt:apiDump    # génère api/<module>.api
./gradlew :crdt:apiCheck   # valide contre la baseline (lance dans build)
```

```
// crdt/api/crdt.api (extrait)
public final class com/doppio/syncdo/crdt/VectorClock : ... CrdtState {
    public fun increment (Ljava/lang/String;)Lcom/doppio/syncdo/crdt/VectorClock;
    public fun merge (...)Lcom/doppio/syncdo/crdt/VectorClock;
    ...
}
```

→ Changement d'API public **ne peut pas passer** sans diff explicite committé.

Notes:
- C'est un filet de sécurité crucial pour une lib publiable : on ne casse pas l'ABI/API par accident.
- Si on renomme un paramètre, ajoute une méthode, change un type → `apiDump` doit être relancé et le diff committé. Le reviewer voit alors l'impact.

---

# Une doc qu'on peut parcourir : Dokka

```bash
./gradlew dokkaGenerate    # → build/dokka/html/index.html
```

- Une page d'accueil par module (`Module.md`) : par où commencer, quel type choisir, un exemple complet
- KDoc avec exemple sur chaque type, `reportUndocumented` → **0** déclaration publique sans doc
- Liens "source" vers GitHub, et la doc est livrée dans le `-javadoc.jar` publié

Notes:
- Une lib publiable sans doc lisible n'est pas réutilisable. Dokka était déclaré dans le projet mais jamais appliqué : le jar javadoc publié était vide.
- Dans `:crdt`, il y a plus de lignes de documentation que de code.

---

# Partie 6 : Bilan

Notes:
- 4 minutes restantes. On résume, on annonce les questions.

---

# Ce qu'on retient

1. **Les CRDTs ne sont pas magiques** : juste des structures dont le merge est commut/assoc/idemp
2. **La composition** sauve la mise : assembler des CRDTs reste un CRDT
3. **Un delta est un petit état** : même type, même `merge`, compacité + robustesse
4. **L'offline-first** est un choix d'architecture, pas une fonctionnalité ajoutée après coup
5. **Kotlin Multiplatform** rend tout ça partageable sur Android, iOS, JVM, sans duplication

Notes:
- Le point 4 est philosophique : si on ne pense pas "offline-first" dès le début, on finit avec des bugs de réconciliation pendant des années.
- Le point 5 est pragmatique : la même logique CRDT tourne sur 4 cibles, pas 4 fois écrite.

---

# Simplifier : avant / après l'audit

| | Avant | Après |
|---|---|---|
| Interfaces dans `:crdt` | `CrdtState`, `Delta`, `DeltaState` | `CrdtState` |
| Types de delta | `OrSetDelta`, `TodoListDelta` | aucun (delta = état) |
| Fichiers dans `:crdt` | 9 | 5 |
| Lignes de code dans `:crdt` | 172 | 78 |
| Dépendances de `:crdt` | coroutines + serialization | serialization |

**Et 3 bugs corrigés** en simplifiant : état complet renvoyé à chaque push, deltas perdus après troncature du log, merge non associatif dans `TodoListCrdt`.

Notes:
- Simplifier n'est pas que cosmétique : en enlevant des concepts, les bugs sont devenus visibles.
- La question à se poser face à chaque abstraction : "est-ce une nouvelle idée, ou la même idée sous un autre nom ?"

---

# Les chiffres

| Composant | Lignes |
|---|---|
| Module `:crdt` (publié) | **289** (dont 78 de code) |
| Module `:sync` (publié) | **469** |
| Sample `:shared` | **350** |
| Tests | **≈ 900** |
| **Total** | **≈ 2 000** |

Plus de lignes de tests que de lignes de code dans les libs.

Notes:
- Petit code, grosse densité. Chaque ligne compte.
- Le ratio tests/code reflète l'importance de prouver les propriétés mathématiques.

---

# Pour aller plus loin

**Améliorations possibles**
- Compression des deltas (Protobuf, CBOR)
- Authentification / autorisation côté `syncEndpoint`
- Garbage collection des tombstones (causal stable cuts)
- Snapshots périodiques + log compacté
- Support multi-room (un `SyncServer` par document)

**Concepts CRDT non couverts ici**
- RGA / Yjs / Automerge (texte collaboratif)
- Multi-Value Register
- Counters (G-Counter : exemple dans le README de `:crdt` ; PN-Counter)

Notes:
- Le texte collaboratif (édition caractère par caractère) demande un CRDT plus spécialisé : c'est pour ça que Yjs/Automerge existent. Le sample Todo n'en a pas besoin.

---

# Lectures recommandées

- **Shapiro et al.** : *A comprehensive study of CRDTs* (INRIA, 2011)
- **Almeida & Shoker** : *Delta State Replicated Data Types* (2018)
- **Martin Kleppmann** : *Designing Data-Intensive Applications* (chap. 5)
- **Automerge** : automerge.org
- **Yjs** : docs.yjs.dev

Notes:
- Le papier d'INRIA 2011 est *la* référence académique. Très lisible.
- Le livre de Kleppmann est plus accessible et donne le contexte plus large.

---

# Repo

```
github.com/renaudmathieu/syncdo
```

**Modules**
- `:crdt` : primitives CRDT génériques (publiable)
- `:sync` : client WebSocket + serveur Ktor (publiable)
- `:shared` : domaine Todo (sample)
- `:composeApp` : UI Compose Multiplatform (sample)
- `:server` : application Ktor (sample)

**Quick start**
```bash
./gradlew :server:run            # localhost:8080
./gradlew :composeApp:run        # desktop client
./gradlew :crdt:allTests :sync:allTests
./gradlew dokkaGenerate          # doc API → build/dokka/html
```

Notes:
- Lien à partager. Lien optionnel vers slides.

---

# Questions ?

Renaud Mathieu
*loafer54@gmail.com*

Notes:
- Réserver ~3-4 minutes de Q/R.
- Questions courantes à anticiper :
  • « Comment ça scale au-delà de 100 deltas dans le log ? » → snapshot + fallback full-state
  • « Pourquoi WebSocket et pas HTTP ? » → push serveur, latence, full-duplex
  • « Quid de la sécurité ? » → handler Ktor, à coupler avec auth (out of scope)
  • « Multi-document ? » → un SyncServer par document, ou shard par roomId
  • « Pourquoi pas Yjs/Automerge ? » → on voulait du pur Kotlin + KMP, sans interop JS

---

<!--
========================================================================
ANNEXES : slides optionnelles si du temps reste, ou pour références
========================================================================
-->

# Annexe A : Pourquoi pas Operational Transform ?

| Critère | OT | CRDT |
|---|---|---|
| Convergence | Repose sur fonction de transformation | Algébrique (semi-treillis) |
| Preuve formelle | Connue pour être très difficile | Triviale (commut/assoc/idemp) |
| Coût d'une op | O(n) (transforme contre toutes les ops concurrentes) | O(1) |
| Offline | Complexe (rejeu d'historique) | Natif (deltas se mergent) |
| Implémenté par | Google Docs (ancien) | Notion, Linear, Figma, iCloud |

Notes:
- À ne montrer que si la question vient ou si on a du temps.

---

# Annexe B : Le format JSON sur le fil

```json
// PushDelta
{
  "type": "push_delta",
  "delta": {
    "itemIds": { "entries": { "uuid-123": [{"nodeId":"A","counter":1}] } },
    "items": { "uuid-123": { "title": {...}, "note": {...} } },
    "clock": { "entries": { "A": 1 } }
  },
  "nodeId": "A"
}
```

- Le delta est un `TodoListCrdt` partiel : mêmes champs que l'état
- Lisible, debuggable, versionnable
- `kotlinx.serialization` + discriminant `@SerialName`
- Trade-off : ~30 % plus volumineux qu'un Protobuf, acceptable au format texte

Notes:
- Tests de round-trip dans `SerializationRoundTripTest` : on garde le wire format stable.

---

# Annexe C : Resilience disque

```kotlin
override suspend fun load(): TodoListCrdt? {
    val text = readTextFile(filePath) ?: return null
    return try {
        json.decodeFromString<TodoListCrdt>(text)
    } catch (e: SerializationException) {
        quarantineCorruptFile(text, e)   // déplace en .corrupt-<ts>
        null                              // → fallback initial state
    }
}
```

- Fichier corrompu → mis en quarantaine, pas écrasé
- Écriture atomique (tmp + move sur JVM, atomically sur iOS)
- L'app redémarre toujours, même après crash pendant écriture

Notes:
- Détail souvent oublié : un crash pendant l'écriture peut produire un fichier tronqué.
- La quarantaine permet de récupérer manuellement les données si jamais.

---

# Annexe D : Tests de propriétés algébriques

```kotlin
@Test fun `merge is commutative`() {
    val a = OrSet<String>().add("x", UniqueTag("A", 1))
    val b = OrSet<String>().add("y", UniqueTag("B", 1))
    assertEquals(a.merge(b), b.merge(a))
}

@Test fun `merge is idempotent`() {
    val a = OrSet<String>().add("x", UniqueTag("A", 1))
    assertEquals(a, a.merge(a))
}

@Test fun `concurrent add and remove - add wins`() {
    val initial = OrSet<String>().add("x", UniqueTag("A", 1))
    val afterRemove = initial.remove("x")
    val afterConcurrentAdd = initial.add("x", UniqueTag("B", 1))
    val merged = afterRemove.merge(afterConcurrentAdd)
    assertTrue(merged.contains("x"))     // ← la garantie observed-remove
}
```

Notes:
- C'est le test critique du module `:crdt`. Si ça casse, tout le système casse.

---

<!--
========================================================================
FIN DU FICHIER
========================================================================
Conseils export PowerPoint :
  1. VS Code + extension "Marp for VS Code" → export PPTX direct
  2. Marp CLI : `marp PRESENTATION.md --pptx -o PRESENTATION.pptx`
  3. Pandoc : `pandoc PRESENTATION.md -o PRESENTATION.pptx`
  4. Reveal.js : `reveal-md PRESENTATION.md` (HTML interactif)

Les notes de présentateur (blocs "Notes:") sont reconnues par Marp et
Pandoc et placées en notes de slide PPT automatiquement.
========================================================================
-->
