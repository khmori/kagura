import './App.css'

async function anki(action: string, params: object = {}) {
    const response = await fetch("http://localhost:8765", {
        method: "POST",
        body: JSON.stringify({ action, version: 6, params })
    })
    const { result, error } = await response.json()
    if (error) throw new Error(error)
    return result
}

async function probe() {
    // fetch deck names
    const decks = await anki("deckNames")
    console.log("decks: ", decks)

    // fetch notes
    const notes = await anki("findNotes", { query: 'deck:"reading mine"' })
    console.log("note ids: ", notes)

    const notesInfo = await anki("notesInfo", { notes: notes.slice(0, 10) })
    console.log("first 10 notes: ", notesInfo)
    console.log(Object.keys(notesInfo[0].fields))

    console.log("fronts: ", notesInfo.map((note: any) => note.fields.front.value))
    console.log("readings: ", notesInfo.map((note: any) => note.fields.Reading.value))
    console.log("meanings: ", notesInfo.map((note: any) => note.fields.MainDefinition.value))
    console.log("sentences: ", notesInfo.map((note: any) => note.fields.Sentence.value))

    // extract card IDs from first 10 notes
    const cards = notesInfo.map((note: any) => note.cards[0])
    console.log("card ids: ", cards)

    const cardsInfo = await anki("cardsInfo", { cards: cards.slice(0, 10) })
    console.log("first 10 cards: ", cardsInfo)

    console.log(Object.keys(cardsInfo[0]))
    console.log("model names: ", cardsInfo.map((card: any) => card.modelName))
    console.log("factors: ", cardsInfo.map((card: any) => card.factor))
    console.log("intervals: ", cardsInfo.map((card: any) => card.interval))
    console.log("types: ", cardsInfo.map((card: any) => card.type))
    console.log("due: ", cardsInfo.map((card: any) => card.due))
    console.log("reps: ", cardsInfo.map((card: any) => card.reps))
    console.log("lapses: ", cardsInfo.map((card: any) => card.lapses))
}

function App() {
  return (
    <>
        <button onClick={probe}>
            sync
        </button>
    </>
  )
}

export default App