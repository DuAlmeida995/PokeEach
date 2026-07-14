import { useState } from 'react'
import Sidebar from './components/Sidebar'
import Inventario from './pages/Inventario'
import Troca from './pages/Troca'
import { USUARIOS_ONLINE } from './services/mockData'

export default function App() {
  const [tela, setTela]                     = useState('inventario') // 'inventario' | 'troca'
  const [pokemonParaTrocar, setPokemonParaTrocar] = useState(null)
  const [usuarioSelecionado, setUsuarioSelecionado] = useState(null)

  function irParaTroca(pokemon) {
    setPokemonParaTrocar(pokemon)
    setTela('troca')
  }

  function voltarParaInventario() {
    setTela('inventario')
    setPokemonParaTrocar(null)
  }

  return (
    <div style={{ display: 'flex', height: '100vh', width: '100vw', overflow: 'hidden' }}>
      <Sidebar
        usuarios={USUARIOS_ONLINE}
        onSelecionarUsuario={setUsuarioSelecionado}
        usuarioSelecionado={usuarioSelecionado}
      />

      {tela === 'inventario' && (
        <Inventario onIrParaTroca={irParaTroca} />
      )}

      {tela === 'troca' && (
        <Troca
          meuPokemon={pokemonParaTrocar}
          usuarioInicial={usuarioSelecionado}
          onVoltar={voltarParaInventario}
        />
      )}
    </div>
  )
}