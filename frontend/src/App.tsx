import './App.css'

// payload sent to the backend — mirrors com.khmori.kagura.dto.SyncRequest / IncomingNote
interface SyncRequest {
    provider: string
    providerUserId: string
    notes: IncomingNote[]
}

type FieldMap = Record<string, { value: string; order: number }>

interface IncomingNote {
    ankiNoteId: number
    ankiModelName: string
    fields: FieldMap
    cards: CardInfo[]
}

// raw shapes returned by AnkiConnect — partial; only fields we use
interface NoteInfo {
    noteId: number
    modelName: string
    tags: string[]
    fields: FieldMap
    cards: number[]   // card IDs, not card objects
}

interface CardInfo {
    cardId: number
    modelName: string
    interval: number
    lapses: number
    reps: number
    factor: number
    queue: number
    type: number
    due: number
}

async function anki<T>(action: string, params: object = {}): Promise<T> {
    const response = await fetch("http://localhost:8765", {
        method: "POST",
        body: JSON.stringify({ action, version: 6, params })
    })
    const { result, error } = await response.json()
    if (error) throw new Error(error)
    return result as T
}

async function probe() {
    // fetch deck names
    const decks = await anki<string[]>("deckNames")
    console.log("decks: ", decks)

    // fetch notes
    const notes = await anki<number[]>("findNotes", { query: 'deck:"reading mine"' })
    console.log("note ids: ", notes)

    const notesInfo = await anki<NoteInfo[]>("notesInfo", { notes: notes.slice(0, 10) })
    console.log("first 10 notes: ", notesInfo)
    console.log(Object.keys(notesInfo[0].fields))

    console.log("fronts: ", notesInfo.map(note => note.fields.front.value))
    console.log("readings: ", notesInfo.map(note => note.fields.Reading.value))
    console.log("meanings: ", notesInfo.map(note => note.fields.MainDefinition.value))
    console.log("sentences: ", notesInfo.map(note => note.fields.Sentence.value))

    // extract card IDs from first 10 notes
    const cards = notesInfo.map(note => note.cards[0])
    console.log("card ids: ", cards)

    const cardsInfo = await anki<CardInfo[]>("cardsInfo", { cards: cards.slice(0, 10) })
    console.log("first 10 cards: ", cardsInfo)

    console.log(Object.keys(cardsInfo[0]))
    console.log("model names: ", cardsInfo.map(card => card.modelName))
    console.log("factors: ", cardsInfo.map(card => card.factor))
    console.log("intervals: ", cardsInfo.map(card => card.interval))
    console.log("types: ", cardsInfo.map(card => card.type))
    console.log("due: ", cardsInfo.map(card => card.due))
    console.log("reps: ", cardsInfo.map(card => card.reps))
    console.log("lapses: ", cardsInfo.map(card => card.lapses))
}

// AnkiConnect returns lots of extra data per card (rendered HTML, CSS, templates)
// and per note (Yomitan HTML soup, media). Drop everything backend doesn't read.
const KEPT_NOTE_FIELDS = ["front", "Sentence", "ExpressionAudio", "SentenceAudio"]

function extractCardScheduling(c: CardInfo): CardInfo {
    const { cardId, modelName, interval, lapses, reps, factor, queue, type, due } = c
    return  { cardId, modelName, interval, lapses, reps, factor, queue, type, due }
}

function extractNoteFields(fields: FieldMap): FieldMap {
    const out: FieldMap = {}
    for (const key of KEPT_NOTE_FIELDS) {
        if (fields[key]) out[key] = fields[key]
    }
    return out
}

async function buildSyncRequest(
    deckName: string,
    provider: string,
    providerUserId: string,
): Promise<SyncRequest> {
    // 1. find note IDs in the deck
    const noteIds = await anki<number[]>("findNotes", { query: `deck:"${deckName}"` })

    // 2. fetch full note info (fields, modelName, card IDs)
    const notesInfo = await anki<NoteInfo[]>("notesInfo", { notes: noteIds })

    // 3. fetch all cards in one batch, then index by cardId for fast lookup
    const allCardIds = notesInfo.flatMap(n => n.cards)
    const cardsInfo = await anki<CardInfo[]>("cardsInfo", { cards: allCardIds })
    const cardsById = new Map<number, CardInfo>(cardsInfo.map(c => [c.cardId, c]))

    // 4. shape into IncomingNote[] — backend's expected format
    const notes: IncomingNote[] = notesInfo.map(n => ({
        ankiNoteId: n.noteId,
        ankiModelName: n.modelName,
        fields: extractNoteFields(n.fields),
        // fields: n.fields,
        cards: n.cards
            .map(id => cardsById.get(id))
            .filter((c): c is CardInfo => c !== undefined)
            .map(extractCardScheduling),
    }))

    return { provider, providerUserId, notes }
}

async function sync() {
    const payload = await buildSyncRequest("reading mine", "manual", "test-1")
    const body = JSON.stringify(payload)
    console.log(`posting payload: ${payload.notes.length} notes, ${(body.length / 1024 / 1024).toFixed(2)} MB`)

    const response = await fetch("http://localhost:8080/api/sync", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body,
    })
    if (!response.ok) {
        throw new Error(`sync failed: ${response.status} ${await response.text()}`)
    }
    console.log("sync ok:", await response.json())
}

function App() {
  return (
    <>
        <button onClick={probe}>
            probe
        </button>

        <button onClick={sync}>
            sync
        </button>
    </>
  )
}

export default App