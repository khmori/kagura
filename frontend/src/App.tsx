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
    const decks = await anki("deckNames")
    console.log("decks: ", decks)
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