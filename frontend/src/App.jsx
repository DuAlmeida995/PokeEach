import { useState, useCallback } from 'react'
import Sidebar from './components/Sidebar'
import Inventario from './pages/Inventario'
import Troca from './pages/Troca'

export default function App() {
  const [tela,               setTela]               = useState('inventario')
  const [pokemonParaTrocar,  setPokemonParaTrocar]  = useState(null)
  const [usuarioSelecionado, setUsuarioSelecionado] = useState(null)
  // Bug 3 fix: contador de refresh — Inventario recarrega quando muda
  const [refreshKey,         setRefreshKey]         = useState(0)

  function irParaTroca(pokemon) {
    setPokemonParaTrocar(pokemon)
    setTela('troca')
  }

  function voltarParaInventario() {
    setTela('inventario')
    setPokemonParaTrocar(null)
    setRefreshKey(k => k + 1) // força reload do inventário ao voltar
  }

  const handlePokemonMinerado = useCallback(() => {
    setRefreshKey(k => k + 1) // força reload imediato após mineração
  }, [])

  return (
    <div style={{ display: 'flex', height: '100vh', width: '100vw', overflow: 'hidden' }}>
      <Sidebar
        onSelecionarUsuario={setUsuarioSelecionado}
        usuarioSelecionado={usuarioSelecionado}
        onPokemonMinerado={handlePokemonMinerado}
      />

      {tela === 'inventario' && (
        <Inventario
          key={refreshKey}
          onIrParaTroca={irParaTroca}
        />
      )}

      {tela === 'troca' && (
        <Troca
          meuPokemon={pokemonParaTrocar}
          usuarioInicial={usuarioSelecionado}
          usuarioAtivo={usuarioSelecionado}
          onVoltar={voltarParaInventario}
        />
      )}
    </div>
  )
}