import { useState, useCallback, useEffect } from 'react'
import Sidebar from './components/Sidebar'
import Inventario from './pages/Inventario'
import Troca from './pages/Troca'
import { getNotificacaoTroca, responderTroca, enviarSolicitacaoTroca } from './services/api'

export default function App() {
  const [tela,               setTela]               = useState('inventario')
  const [pokemonParaTrocar,  setPokemonParaTrocar]  = useState(null)
  const [usuarioSelecionado, setUsuarioSelecionado] = useState(null)
  const [refreshKey,         setRefreshKey]         = useState(0)
  const [notificacao,        setNotificacao]        = useState(null)

  // Polling de notificações de troca a cada 3s
  useEffect(() => {
    const id = setInterval(() => {
      getNotificacaoTroca()
        .then(data => {
          if (data.pendente) setNotificacao(data)
          else setNotificacao(null)
        })
        .catch(() => {})
    }, 3000)
    return () => clearInterval(id)
  }, [])

  function irParaTroca(pokemon) {
    setPokemonParaTrocar(pokemon)
    setTela('troca')
  }

  function voltarParaInventario() {
    setTela('inventario')
    setPokemonParaTrocar(null)
    setRefreshKey(k => k + 1)
  }

  const handlePokemonMinerado = useCallback(() => {
    setRefreshKey(k => k + 1)
  }, [])

  async function handleAceitarTroca() {
    try {
      await responderTroca(true)
      setNotificacao(null)
      setRefreshKey(k => k + 1)
    } catch (e) {
      console.error('Erro ao aceitar troca:', e)
    }
  }

  async function handleRecusarTroca() {
    try {
      await responderTroca(false)
      setNotificacao(null)
    } catch (e) {
      console.error('Erro ao recusar troca:', e)
    }
  }

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
          notificacaoTroca={notificacao}
          onAceitarTroca={handleAceitarTroca}
          onRecusarTroca={handleRecusarTroca}
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