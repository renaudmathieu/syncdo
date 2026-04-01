# Pourquoi des CRDTs si on fait du Last-Write-Wins ?

## La question

> "Dans une to-do list, si deux utilisateurs modifient le même champ, on bascule sur
> last-write-wins. Quel est l'intérêt des CRDTs ?"

L'intuition est bonne — et partiellement juste. `LwwRegister` **est** un CRDT. Mais
l'intérêt des CRDTs dans ce projet n'est pas de remplacer LWW : c'est de définir
*à quelle granularité* et *sur quels problèmes* LWW s'applique, et de garantir la
convergence même quand plusieurs structures sont composées ensemble.

---

## 1. LWW sur l'objet entier vs LWW par champ

Imaginons un `TodoItem` simple sans CRDT :

```
TodoItem { id, title, note, completed, position, updatedAt }
```

Avec un LWW sur l'**objet entier**, la règle est : le `updatedAt` le plus récent gagne.

**Scénario :**
- Alice est hors-ligne. Elle modifie le `title` à 10h00.
- Bob est hors-ligne. Il coche `completed = true` à 10h01.
- Les deux se reconnectent.

Résultat avec LWW sur l'objet entier : **la modification de Bob (10h01) écrase celle
d'Alice (10h00)**. Le nouveau titre est perdu.

Résultat avec `TodoItemCrdt` (LWW **par champ**) :

```kotlin
// TodoItemCrdt.merge()
return TodoItemCrdt(
    title     = title.merge(other.title),       // Alice gagne sur son champ
    completed = completed.merge(other.completed) // Bob gagne sur son champ
)
```

Les deux modifications sont préservées. C'est la première valeur des CRDTs dans ce
projet : **décomposer l'objet en champs indépendants** pour ne pas perdre les
modifications non-conflictuelles.

---

## 2. Là où LWW échoue : l'appartenance à une liste

LWW fonctionne bien pour modifier un champ scalaire (texte, booléen). Il échoue
pour les **opérations d'appartenance** (ajouter ou supprimer un élément d'un
ensemble).

**Scénario :**

```
État initial : liste = [A, B, C]

Alice (hors-ligne) : supprime B  →  liste = [A, C],  timestamp = 10h00
Bob   (hors-ligne) : supprime C  →  liste = [A, B],  timestamp = 10h01
```

Avec LWW sur la liste entière (dernier `updatedAt` gagne) :
- Bob a le timestamp le plus récent → sa liste `[A, B]` écrase celle d'Alice.
- **La suppression de B par Alice est perdue.**

Avec l'`OrSet` de ce projet :
- Chaque suppression tombstone les tags *observés au moment de la suppression*.
- Les deux opérations sont indépendantes et les deux se retrouvent dans l'état mergé.
- Résultat : `[A]` — les deux suppressions sont honorées.

```kotlin
// OrSet.merge() : union des tags actifs, union des tombstones
val mergedTombstones = tombstones ∪ other.tombstones
val mergedEntries    = entries    ∪ other.entries  (tags actifs - tombstones)
```

C'est le problème classique des ensembles distribués : **une opération de suppression
concurrente à une opération d'ajout** ne peut pas être résolue par LWW seul, car les
deux opérations n'ont pas le même objet. L'`OrSet` encode la sémantique "add-wins" :
si Alice ajoute X et Bob supprime X simultanément (sans avoir observé l'ajout), X
survit.

---

## 3. La propriété mathématique : convergence garantie

La vraie valeur des CRDTs est une **propriété formelle** : toute structure qui
implémente `CrdtState<T>` avec une fonction `merge()` commutative, associative et
idempotente *converge*. Peu importe l'ordre dans lequel les deltas arrivent.

```kotlin
interface CrdtState<T> {
    fun merge(other: T): T
    // Invariants attendus :
    // Commutativité  : a.merge(b) == b.merge(a)
    // Associativité  : a.merge(b.merge(c)) == (a.merge(b)).merge(c)
    // Idempotence    : a.merge(a) == a
}
```

Ce n'est pas une propriété qu'on obtient "gratuitement" avec un LWW ad hoc. Un LWW
sur la liste entière n'est **ni commutatif** (l'ordre d'arrivée des messages change le
résultat) **ni idempotent** (recevoir le même message deux fois peut écraser un état
plus récent si les horloges ne sont pas parfaitement synchronisées).

Les CRDTs permettent de **composer** des structures sans casser la convergence :
`TodoListCrdt` (OrSet + Map de TodoItemCrdt) est un CRDT parce que chacun de ses
composants l'est. C'est ce que le commentaire dans le code exprime :

```kotlin
// Composition of semi-lattices = semi-lattice (course ch.6).
```

---

## 4. Le protocole delta

Un bénéfice pratique découlant de la structure CRDT est le **delta sync**. Parce que
`merge()` est idempotent et commutatif, le `DeltaBuffer` peut accumuler uniquement
les *changements locaux* depuis le dernier sync et les envoyer :

```
PushDelta { addedItems, updatedItems, addedTags, removedTags, clock }
```

Le serveur merge ce delta dans son état complet. Chaque client fait de même à la
réception. Personne n'a besoin d'envoyer ou de stocker l'état complet à chaque
changement.

Avec un LWW naïf sur la liste entière, il faudrait systématiquement transmettre
l'état entier (ou implémenter une logique de diff non-garantie).

---

## Récapitulatif

| Problème                                      | LWW objet entier | LWW par champ (LwwRegister) | OrSet         |
|-----------------------------------------------|------------------|-----------------------------|---------------|
| Alice modifie title, Bob modifie note          | ❌ une modif perdue | ✅ les deux préservées     | —             |
| Alice supprime B, Bob supprime C               | ❌ une suppression perdue | —                  | ✅ les deux honorées |
| Add + delete concurrent sur le même élément   | ❌ non déterministe | —                          | ✅ add-wins   |
| Recevoir le même delta deux fois              | ❌ peut écraser   | ✅ idempotent               | ✅ idempotent |
| Convergence garantie formellement             | ❌                | ✅                          | ✅            |

**En résumé :** ton intuition est correcte pour les *champs scalaires* — LWW est
exactement la bonne politique, et c'est ce que `LwwRegister` implémente. Les CRDTs
apportent leur valeur principale à deux endroits : (1) la **granularité** (par champ
plutôt que par objet), et (2) la **gestion de l'appartenance** à l'ensemble via
l'`OrSet`, qui est un problème que LWW seul ne peut pas résoudre de manière
déterministe.
