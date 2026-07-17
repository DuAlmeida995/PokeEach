import { useState, useEffect } from 'react'
import PokemonCard from '../components/PokemonCard'
import { getSpriteUrl, fetchPokemonStats, getMeuInventario } from '../services/api'

export default function Inventario({ onIrParaTroca, notificacaoTroca, onAceitarTroca, onRecusarTroca }) {
  const [inventario,  setInventario]  = useState([])
  const [selecionado, setSelecionado] = useState(null)
  const [stats,       setStats]       = useState(null)
  const [caixa,       setCaixa]       = useState(1)
  const [carregando,  setCarregando]  = useState(false)

  useEffect(() => {
    getMeuInventario()
      .then(data => {
        const lista = data.pokemon || []
        setInventario(lista)
        if (lista.length > 0) setSelecionado(lista[0])
      })
      .catch(e => console.error('Erro ao carregar inventário:', e))
  }, [])

  useEffect(() => {
    setStats(null)
    if (!selecionado?.id) return
    setCarregando(true)
    fetchPokemonStats(selecionado.id)
      .then(setStats)
      .finally(() => setCarregando(false))
  }, [selecionado])

  return (
    <div style={{ display: 'flex', flex: 1, height: '100vh', overflow: 'hidden', position: 'relative' }}>

      {/* Notificação de solicitação de troca */}
      {notificacaoTroca && (
        <div style={{
          position: 'absolute', top: 0, left: 0, right: 0, bottom: 0,
          background: 'rgba(0,0,0,0.75)',
          display: 'flex', alignItems: 'center', justifyContent: 'center',
          zIndex: 100,
        }}>
          <div style={{
            background: '#D8D8D8',
            border: '4px solid #888', borderBottom: '6px solid #555',
            padding: '24px', maxWidth: '320px', width: '90%',
            display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '16px',
          }}>
            <p style={{ fontFamily: 'var(--font)', fontSize: '8px', color: '#181818', textAlign: 'center', lineHeight: 2 }}>
              Solicitação de Troca!
            </p>
            <p style={{ fontFamily: 'var(--font)', fontSize: '7px', color: '#444', textAlign: 'center', lineHeight: 2 }}>
              <strong>{notificacaoTroca.remetente}</strong><br />
              quer trocar<br />
              <strong>{notificacaoTroca.pokemonOferecido}</strong><br />
              pelo seu<br />
              <strong>{notificacaoTroca.pokemonSolicitado}</strong>
            </p>
            <div style={{ display: 'flex', gap: '12px' }}>
              <button onClick={onAceitarTroca} style={{
                background: '#4CAF50', border: '3px solid #2E7D32', borderBottom: '5px solid #1B5E20',
                fontFamily: 'var(--font)', fontSize: '7px', color: '#F8F8F8',
                padding: '10px 16px', cursor: 'pointer',
              }}>
                ✓ Aceitar
              </button>
              <button onClick={onRecusarTroca} style={{
                background: '#C8181C', border: '3px solid #8B0000', borderBottom: '5px solid #5B0000',
                fontFamily: 'var(--font)', fontSize: '7px', color: '#F8F8F8',
                padding: '10px 16px', cursor: 'pointer',
              }}>
                ✗ Recusar
              </button>
            </div>
          </div>
        </div>
      )}

      {/* ── Painel esquerdo ── */}
      <div style={{
        width: '220px', minWidth: '220px',
        background: '#B8B8B8', borderRight: '4px solid #888888',
        display: 'flex', flexDirection: 'column', height: '100vh',
      }}>
        <div style={{
          background: '#D8D8D8', border: '3px solid #888888',
          margin: '12px 12px 0', padding: '8px 10px',
          fontFamily: 'var(--font)', fontSize: '9px', color: '#181818', textAlign: 'center',
        }}>
          {selecionado ? selecionado.nome : '—'}
        </div>

        <div style={{
          background: '#C8C8C8', border: '3px solid #888888',
          margin: '8px 12px', flex: '0 0 140px',
          display: 'flex', alignItems: 'center', justifyContent: 'center',
        }}>
          {selecionado ? (
            <img src={getSpriteUrl(selecionado.id)} alt={selecionado.nome}
              style={{ width: '100px', height: '100px', imageRendering: 'pixelated' }} />
          ) : (
            <span style={{ fontFamily: 'var(--font)', fontSize: '7px', color: '#888', textAlign: 'center', lineHeight: 2 }}>
              Nenhum<br />Pokémon
            </span>
          )}
        </div>

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
              {inventario.length === 0 ? 'Mine um Pokémon!' : 'Selecione um Pokémon'}
            </p>
          )}
        </div>

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
        <div style={{
          display: 'flex', alignItems: 'center', justifyContent: 'center',
          gap: '12px', padding: '10px 16px', background: 'rgba(0,0,0,0.25)',
        }}>
          <NavBtn onClick={() => setCaixa(c => Math.max(1, c - 1))}>◀</NavBtn>
          <span style={{ fontFamily: 'var(--font)', fontSize: '10px', color: '#F8F8F8', textShadow: '2px 2px 0 #181818', minWidth: '80px', textAlign: 'center' }}>
            CAIXA {caixa}
          </span>
          <NavBtn onClick={() => setCaixa(c => c + 1)}>▶</NavBtn>
        </div>

        <div style={{
          display: 'grid', gridTemplateColumns: 'repeat(4, 80px)',
          gap: '12px', padding: '20px',
          justifyContent: 'center', alignContent: 'start',
          flex: 1, overflowY: 'auto',
        }}>
          {inventario.length === 0 ? (
            <div style={{ gridColumn: '1/-1', textAlign: 'center', marginTop: '40px' }}>
              <p style={{ fontFamily: 'var(--font)', fontSize: '8px', color: 'rgba(255,255,255,0.7)', lineHeight: 2, textShadow: '1px 1px 0 #181818' }}>
                Nenhum Pokémon ainda.<br />Clique em Minerar!
              </p>
            </div>
          ) : (
            inventario.map((p, i) => (
              <PokemonCard
                key={`${p.id}-${i}`}
                pokemon={p}
                selecionado={selecionado?.id === p.id && selecionado?.nome === p.nome}
                onClick={setSelecionado}
              />
            ))
          )}
          {inventario.length > 0 && Array.from({ length: Math.max(0, 8 - inventario.length) }).map((_, i) => (
            <div key={`vazio-${i}`} style={{
              width: '80px', height: '80px',
              background: 'rgba(0,0,0,0.15)', border: '3px solid rgba(0,0,0,0.2)',
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
      background: '#D8D8D8', border: '3px solid #888888', borderBottom: '4px solid #555555',
      fontFamily: 'var(--font)', fontSize: '10px', color: '#181818',
      width: '32px', height: '28px', cursor: 'pointer',
      display: 'flex', alignItems: 'center', justifyContent: 'center', padding: 0,
    }}>
      {children}
    </button>
  )
}