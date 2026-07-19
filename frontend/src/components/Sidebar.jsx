import { useState, useEffect } from 'react'
import { getStatus, getPeers, minerar, getSpriteUrl } from '../services/api'

export default function Sidebar({ onSelecionarUsuario, usuarioSelecionado, onPokemonMinerado }) {
  const [peers,  setPeers]  = useState([])
  const [status, setStatus] = useState(null)

  useEffect(() => {
    function atualizar() {
      getStatus().then(setStatus).catch(() => {})
      getPeers().then(data => {
        const lista = (data.peers || [])
          .filter(p => p.online)
          .map(p => ({
            nome:      p.nome     || p.endereco,
            // chave = endereço P2P (IP:porta) usado para buscar inventário
            chave:     p.endereco,
            // chavePublica RSA guardada para envio de TX
            chavePublica: p.chavePublica || '',
          }))
        setPeers(lista)
      }).catch(() => {})
    }
    atualizar()
    const id = setInterval(atualizar, 5000)
    return () => clearInterval(id)
  }, [])

  return (
    <aside style={{
      width: '160px', minWidth: '160px',
      background: '#C8181C',
      display: 'flex', flexDirection: 'column',
      borderRight: '4px solid #8B0000',
      height: '100vh', overflow: 'hidden',
    }}>
      {/* Logo */}
      <div style={{ padding: '12px 8px 8px', borderBottom: '4px solid #8B0000', textAlign: 'center' }}>
        <span style={{
          fontFamily: 'var(--font)', fontSize: '18px', color: '#F8C800',
          textShadow: '2px 2px 0 #8B0000, -1px -1px 0 #FF6600', letterSpacing: '-1px',
        }}>
          Poke<span style={{ color: '#F8F8F8' }}>Each</span>
        </span>
        {status && (
          <p style={{ fontFamily: 'var(--font)', fontSize: '5px', color: 'rgba(255,255,255,0.6)', marginTop: '4px' }}>
            bloco #{status.height} · {status.nomeTreinador}
          </p>
        )}
      </div>

      {/* Online */}
      <div style={{ padding: '10px 10px 4px' }}>
        <span style={{ fontFamily: 'var(--font)', fontSize: '7px', color: '#90EE90', letterSpacing: '1px' }}>
          ● Online ({peers.length})
        </span>
        <hr style={{ border: '1px solid #8B0000', marginTop: '6px' }} />
      </div>

      {/* Lista */}
      <ul style={{ listStyle: 'none', padding: '4px 0', overflowY: 'auto', flex: 1 }}>
        {peers.length === 0 && (
          <li style={{ padding: '8px 10px', fontFamily: 'var(--font)', fontSize: '6px', color: 'rgba(255,255,255,0.3)', lineHeight: 1.8 }}>
            Nenhum peer<br />conectado
          </li>
        )}
        {peers.map(u => (
          <li key={u.chave} onClick={() => onSelecionarUsuario(usuarioSelecionado?.chave === u.chave ? null : u)} style={{
            padding: '8px 10px',
            fontFamily: 'var(--font)', fontSize: '6.5px',
            color: usuarioSelecionado?.chave === u.chave ? '#F8C800' : '#FFB3B3',
            cursor: 'pointer',
            background: usuarioSelecionado?.chave === u.chave ? '#8B0000' : 'transparent',
            borderLeft: usuarioSelecionado?.chave === u.chave ? '3px solid #F8C800' : '3px solid transparent',
            wordBreak: 'break-all', lineHeight: '1.6',
          }}>
            {u.nome}
          </li>
        ))}
      </ul>

      {/* Botão minerar */}
      <div style={{ padding: '12px 10px', borderTop: '4px solid #8B0000' }}>
        <MinerarButton onPokemonMinerado={onPokemonMinerado} />
      </div>
    </aside>
  )
}

function MinerarButton({ onPokemonMinerado }) {
  const [minerando, setMinerando] = useState(false)
  const [resultado, setResultado] = useState(null)
  const [erro,      setErro]      = useState(null)

  async function handleMinerar() {
    setMinerando(true)
    setResultado(null)
    setErro(null)
    try {
      const data = await minerar()
      setResultado(data)
      // Bug 3 fix: avisa o Inventario para recarregar
      if (onPokemonMinerado) onPokemonMinerado(data)
    } catch (e) {
      setErro(e.message)
    } finally {
      setMinerando(false)
    }
  }

  return (
    <div>
      <button onClick={handleMinerar} disabled={minerando} title="Minerar um Pokémon" style={{
        width: '100%', height: '44px',
        background: minerando
          ? '#2E7D32'
          : 'repeating-linear-gradient(45deg,#4CAF50 0,#4CAF50 4px,#3a8f3e 4px,#3a8f3e 8px)',
        border: '3px solid #2E7D32', borderBottom: '5px solid #1B5E20',
        cursor: minerando ? 'wait' : 'pointer',
        fontFamily: 'var(--font)', fontSize: '6px',
        color: '#F8F8F8', textShadow: '1px 1px 0 #1B5E20',
        display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '4px',
        transform: minerando ? 'translateY(2px)' : 'none', transition: 'transform 0.1s',
      }}>
        {minerando ? '⛏ Minerando...' : '🌿 Minerar'}
      </button>

      {resultado && resultado.rewardId > 0 && (
        <div style={{ marginTop: '8px', textAlign: 'center' }}>
          <img src={getSpriteUrl(resultado.rewardId)} alt={resultado.rewardPokemon}
            style={{ width: '48px', height: '48px', imageRendering: 'pixelated' }} />
          <p style={{ fontFamily: 'var(--font)', fontSize: '6px', color: '#F8C800', lineHeight: 1.8, marginTop: '4px' }}>
            Você capturou<br />{resultado.rewardPokemon}!
          </p>
        </div>
      )}

      {erro && (
        <p style={{ fontFamily: 'var(--font)', fontSize: '5.5px', color: '#FFB3B3', marginTop: '6px', textAlign: 'center', lineHeight: 1.8 }}>
          {erro}
        </p>
      )}
    </div>
  )
}