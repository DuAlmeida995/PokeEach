import { useState, useEffect } from 'react'
import PokemonCard from '../components/PokemonCard'
import { getSpriteUrl, fetchPokemonStats, getMeuInventario } from '../services/api'

// Fallback mock enquanto backend não estiver rodando
const MOCK_INVENTARIO = [
  { id: 1,   nome: 'Bulbasaur'  },
  { id: 151, nome: 'Mew'        },
  { id: 4,   nome: 'Charmander' },
  { id: 28,  nome: 'Sandslash'  },
  { id: 65,  nome: 'Alakazam'   },
  { id: 39,  nome: 'Jigglypuff' },
  { id: 12,  nome: 'Butterfree' },
  { id: 7,   nome: 'Squirtle'   },
]

export default function Inventario({ onIrParaTroca }) {
  const [inventario, setInventario] = useState(MOCK_INVENTARIO)
  const [selecionado, setSelecionado] = useState(MOCK_INVENTARIO[0])
  const [stats, setStats]             = useState(null)
  const [caixa, setCaixa]             = useState(1)
  const [carregando, setCarregando]   = useState(false)

  // Carrega inventário real do backend
  useEffect(() => {
    getMeuInventario()
      .then(data => {
        if (data.pokemon?.length > 0) {
          setInventario(data.pokemon)
          setSelecionado(data.pokemon[0])
        }
      })
      .catch(() => { /* usa mock silenciosamente */ })
  }, [])

  // Carrega stats do Pokémon selecionado via PokeAPI
  useEffect(() => {
    setStats(null)
    if (!selecionado?.id) return
    setCarregando(true)
    fetchPokemonStats(selecionado.id)
      .then(setStats)
      .finally(() => setCarregando(false))
  }, [selecionado])

  return (
    <div style={{ display: 'flex', flex: 1, height: '100vh', overflow: 'hidden' }}>

      {/* ── Painel esquerdo ── */}
      <div style={{
        width: '220px', minWidth: '220px',
        background: '#B8B8B8',
        borderRight: '4px solid #888888',
        display: 'flex', flexDirection: 'column',
        height: '100vh',
      }}>
        {/* Nome */}
        <div style={{
          background: '#D8D8D8', border: '3px solid #888888',
          margin: '12px 12px 0', padding: '8px 10px',
          fontFamily: 'var(--font)', fontSize: '9px',
          color: '#181818', textAlign: 'center',
        }}>
          {selecionado ? selecionado.nome : '—'}
        </div>

        {/* Sprite */}
        <div style={{
          background: '#C8C8C8', border: '3px solid #888888',
          margin: '8px 12px', flex: '0 0 140px',
          display: 'flex', alignItems: 'center', justifyContent: 'center',
        }}>
          {selecionado ? (
            <img src={getSpriteUrl(selecionado.id)} alt={selecionado.nome}
              style={{ width: '100px', height: '100px', imageRendering: 'pixelated' }} />
          ) : (
            <span style={{ fontFamily: 'var(--font)', fontSize: '8px', color: '#888' }}>?</span>
          )}
        </div>

        {/* Stats */}
        <div style={{
          margin: '0 12px 8px', background: '#D0D0D0',
          border: '3px solid #888888', padding: '8px',
          flex: 1, display: 'flex', flexDirection: 'column', gap: '6px',
        }}>
          {carregando && (
            <p style={{ fontFamily: 'var(--font)', fontSize: '7px', color: '#555', textAlign: 'center', marginTop: '20px' }}>
              Carregando...
            </p>
          )}
          {stats && !carregando && (
            <>
              <StatRow label="Tipo"  value={stats.tipo} />
              <StatBar label="HP"    value={stats.hp}     max={255} color="#58C858" />
              <StatBar label="ATK"   value={stats.ataque} max={255} color="#F85840" />
              <StatBar label="DEF"   value={stats.defesa} max={255} color="#F8A800" />
              <StatBar label="VEL"   value={stats.veloc}  max={255} color="#6890F0" />
            </>
          )}
          {!stats && !carregando && (
            <p style={{ fontFamily: 'var(--font)', fontSize: '7px', color: '#555', textAlign: 'center', marginTop: '20px' }}>
              Selecione um Pokémon
            </p>
          )}
        </div>

        {/* Botão Trocar */}
        <button
          onClick={() => selecionado && onIrParaTroca(selecionado)}
          disabled={!selecionado}
          style={{
            margin: '0 12px 12px', padding: '10px',
            background: selecionado ? '#D8D8D8' : '#A8A8A8',
            border: '3px solid #888888', borderBottom: '5px solid #555555',
            fontFamily: 'var(--font)', fontSize: '7px', color: '#181818',
            cursor: selecionado ? 'pointer' : 'not-allowed', textAlign: 'center',
          }}
        >
          Trocar Pokémon
        </button>
      </div>

      {/* ── Grade de Pokémon ── */}
      <div style={{
        flex: 1,
        background: `repeating-linear-gradient(0deg,transparent,transparent 15px,rgba(0,0,0,0.06) 15px,rgba(0,0,0,0.06) 16px),
                     repeating-linear-gradient(90deg,transparent,transparent 15px,rgba(0,0,0,0.06) 15px,rgba(0,0,0,0.06) 16px),
                     #5C8A3C`,
        display: 'flex', flexDirection: 'column', overflow: 'hidden',
      }}>
        {/* Header caixa */}
        <div style={{
          display: 'flex', alignItems: 'center', justifyContent: 'center',
          gap: '12px', padding: '10px 16px',
          background: 'rgba(0,0,0,0.25)',
        }}>
          <NavBtn onClick={() => setCaixa(c => Math.max(1, c - 1))}>◀</NavBtn>
          <span style={{
            fontFamily: 'var(--font)', fontSize: '10px',
            color: '#F8F8F8', textShadow: '2px 2px 0 #181818',
            minWidth: '80px', textAlign: 'center',
          }}>
            CAIXA {caixa}
          </span>
          <NavBtn onClick={() => setCaixa(c => c + 1)}>▶</NavBtn>
        </div>

        {/* Grid */}
        <div style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(4, 80px)',
          gap: '12px', padding: '20px',
          justifyContent: 'center', alignContent: 'start',
          flex: 1, overflowY: 'auto',
        }}>
          {inventario.map((p, i) => (
            <PokemonCard
              key={`${p.id}-${i}`}
              pokemon={p}
              selecionado={selecionado?.id === p.id && selecionado?.nome === p.nome}
              onClick={setSelecionado}
            />
          ))}
          {Array.from({ length: Math.max(0, 8 - inventario.length) }).map((_, i) => (
            <div key={`vazio-${i}`} style={{
              width: '80px', height: '80px',
              background: 'rgba(0,0,0,0.15)',
              border: '3px solid rgba(0,0,0,0.2)',
            }} />
          ))}
        </div>
      </div>
    </div>
  )
}

function StatRow({ label, value }) {
  return (
    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
      <span style={{ fontFamily: 'var(--font)', fontSize: '6px', color: '#444' }}>{label}</span>
      <span style={{ fontFamily: 'var(--font)', fontSize: '6px', color: '#181818', textTransform: 'capitalize' }}>{value}</span>
    </div>
  )
}

function StatBar({ label, value, max, color }) {
  const pct = Math.round((value / max) * 100)
  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '2px' }}>
        <span style={{ fontFamily: 'var(--font)', fontSize: '6px', color: '#444' }}>{label}</span>
        <span style={{ fontFamily: 'var(--font)', fontSize: '6px', color: '#181818' }}>{value}</span>
      </div>
      <div style={{ background: '#A8A8A8', height: '6px', border: '1px solid #888' }}>
        <div style={{ width: `${pct}%`, height: '100%', background: color }} />
      </div>
    </div>
  )
}

function NavBtn({ children, onClick }) {
  return (
    <button onClick={onClick} style={{
      background: '#D8D8D8', border: '3px solid #888888',
      borderBottom: '4px solid #555555',
      fontFamily: 'var(--font)', fontSize: '10px', color: '#181818',
      width: '32px', height: '28px', cursor: 'pointer',
      display: 'flex', alignItems: 'center', justifyContent: 'center', padding: 0,
    }}>
      {children}
    </button>
  )
}