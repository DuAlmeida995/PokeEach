import { useState, useEffect } from 'react'
import PokemonCard from '../components/PokemonCard'
import IVDisplay from '../components/IVDisplay'
import { getSpriteUrl, getMeuInventario } from '../services/api'

export default function Inventario({ onIrParaTroca, notificacaoTroca, onAceitarTroca, onRecusarTroca }) {
  const [inventario, setInventario] = useState([])
  const [selecionado, setSelecionado] = useState(null)
  
  const [caixa, setCaixa] = useState(() => {
    const caixaSalva = sessionStorage.getItem('pokeEach_caixaAtual')
    return caixaSalva ? parseInt(caixaSalva, 10) : 1
  })

  useEffect(() => {
    sessionStorage.setItem('pokeEach_caixaAtual', caixa.toString())
  }, [caixa])

  useEffect(() => {
    getMeuInventario()
      .then(data => {
        const lista = data.pokemon || []
        setInventario(lista)
        if (lista.length > 0) {
          const indexInicial = (caixa - 1) * 30;
          setSelecionado(lista[indexInicial] || lista[0])
        }
      })
      .catch(e => console.error('Erro ao carregar inventário:', e))
  }, []) 

  const POKEMONS_POR_CAIXA = 30
  const totalCaixas = Math.max(1, Math.ceil(inventario.length / POKEMONS_POR_CAIXA))
  
  useEffect(() => {
    if (inventario.length > 0 && caixa > totalCaixas) {
      setCaixa(totalCaixas)
    }
  }, [inventario.length, caixa, totalCaixas])

  const pokemonsDaCaixa = inventario.slice(
    (caixa - 1) * POKEMONS_POR_CAIXA,
    caixa * POKEMONS_POR_CAIXA
  )

  return (
    <div style={{ display: 'flex', flex: 1, height: '100vh', overflow: 'hidden', position: 'relative' }}>

      {/* Notificacao de troca */}
      {notificacaoTroca && (
        <div style={{
          position: 'absolute', inset: 0,
          background: 'rgba(0,0,0,0.8)',
          display: 'flex', alignItems: 'center', justifyContent: 'center',
          zIndex: 100,
        }}>
          <div style={{
            background: '#D8D8D8', border: '4px solid #888', borderBottom: '6px solid #555',
            padding: '24px', maxWidth: '340px', width: '90%',
            display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '14px',
          }}>
            <p style={{ fontFamily: 'var(--font)', fontSize: '8px', color: '#181818', textAlign: 'center', lineHeight: 2 }}>
              Solicitação de Troca!
            </p>
            <div style={{ display: 'flex', gap: '16px', alignItems: 'center' }}>
              {/* Pokemon oferecido */}
              <PokemonMiniCard nome={notificacaoTroca.pokemonOferecido} label="receber" />
              <span style={{ fontFamily: 'var(--font)', fontSize: '12px', color: '#555' }}>⇄</span>
              {/* Pokemon solicitado */}
              <PokemonMiniCard nome={notificacaoTroca.pokemonSolicitado} label="entregar" />
            </div>
            <p style={{ fontFamily: 'var(--font)', fontSize: '6px', color: '#666', textAlign: 'center', lineHeight: 2 }}>
              de <strong>{notificacaoTroca.remetente}</strong>
            </p>
            <div style={{ display: 'flex', gap: '12px' }}>
              <button onClick={onAceitarTroca} style={{
                background: '#4CAF50', border: '3px solid #2E7D32', borderBottom: '5px solid #1B5E20',
                fontFamily: 'var(--font)', fontSize: '7px', color: '#F8F8F8',
                padding: '10px 16px', cursor: 'pointer',
              }}>✓ Aceitar</button>
              <button onClick={onRecusarTroca} style={{
                background: '#C8181C', border: '3px solid #8B0000', borderBottom: '5px solid #5B0000',
                fontFamily: 'var(--font)', fontSize: '7px', color: '#F8F8F8',
                padding: '10px 16px', cursor: 'pointer',
              }}>✗ Recusar</button>
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
        {/* Nome */}
        <div style={{
          background: '#D8D8D8', border: '3px solid #888888',
          margin: '12px 12px 0', padding: '8px 10px',
          fontFamily: 'var(--font)', fontSize: '9px', color: '#181818', textAlign: 'center',
        }}>
          {selecionado ? selecionado.nome : '—'}
        </div>

        {/* Sprite */}
        <div style={{
          background: '#C8C8C8', border: '3px solid #888888',
          margin: '8px 12px 4px', flex: '0 0 110px',
          display: 'flex', alignItems: 'center', justifyContent: 'center',
        }}>
          {selecionado ? (
            <img src={getSpriteUrl(selecionado.id)} alt={selecionado.nome}
              style={{ width: '90px', height: '90px', imageRendering: 'pixelated' }} />
          ) : (
            <span style={{ fontFamily: 'var(--font)', fontSize: '11px', color: '#888', textAlign: 'center', lineHeight: 2 }}>
              Nenhum<br />Pokémon
            </span>
          )}
        </div>

        {/* IVs */}
        <div style={{
          margin: '0 12px 8px', background: '#D0D0D0',
          border: '3px solid #888888', padding: '8px',
          flex: 1, overflowY: 'auto',
          display: 'flex', flexDirection: 'column', gap: '4px',
        }}>
          {!selecionado ? (
            <p style={{ fontFamily: 'var(--font)', fontSize: '10px', color: '#555', textAlign: 'center', marginTop: '16px' }}>
              {inventario.length === 0 ? 'Mine um Pokémon!' : 'Selecione um Pokémon'}
            </p>
          ) : selecionado.hp !== undefined ? (
            <IVDisplay pokemon={selecionado} />
          ) : (
            <p style={{ fontFamily: 'var(--font)', fontSize: '9px', color: '#888', textAlign: 'center', marginTop: '16px', lineHeight: 2 }}>
              Mine um novo Pokémon<br />para ver os IVs
            </p>
          )}
        </div>

        {/* Botao Trocar */}
        <button
          onClick={() => selecionado && onIrParaTroca(selecionado)}
          disabled={!selecionado}
          style={{
            margin: '0 12px 12px', padding: '10px',
            background: selecionado ? '#D8D8D8' : '#A8A8A8',
            border: '3px solid #888888', borderBottom: '5px solid #555555',
            fontFamily: 'var(--font)', fontSize: '10px', color: '#181818',
            cursor: selecionado ? 'pointer' : 'not-allowed', textAlign: 'center',
          }}
        >
          Trocar Pokémon
        </button>
      </div>

      {/* ── Grade ── */}
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
          <NavBtn onClick={() => setCaixa(c => Math.max(1, c - 1))} disabled={caixa === 1}>◀</NavBtn>
          <span style={{ fontFamily: 'var(--font)', fontSize: '11px', color: '#F8F8F8', textShadow: '2px 2px 0 #181818', minWidth: '80px', textAlign: 'center' }}>
            CAIXA {caixa}
          </span>
          <NavBtn onClick={() => setCaixa(c => Math.min(totalCaixas, c + 1))} disabled={caixa === totalCaixas}>▶</NavBtn>
        </div>

        <div style={{
          display: 'grid', gridTemplateColumns: 'repeat(6, 100px)',
          gap: '12px', padding: '20px',
          justifyContent: 'center', alignContent: 'start',
          flex: 1, overflowY: 'auto',
        }}>
          {inventario.length === 0 ? (
            <div style={{ gridColumn: '1/-1', textAlign: 'center', marginTop: '40px' }}>
              <p style={{ fontFamily: 'var(--font)', fontSize: '11px', color: 'rgba(255,255,255,0.7)', lineHeight: 2, textShadow: '1px 1px 0 #181818' }}>
                Nenhum Pokémon ainda.<br />Clique em Minerar!
              </p>
            </div>
          ) : (
            pokemonsDaCaixa.map((p, i) => (
              <PokemonCard
                key={`${p.id}-${i}`}
                pokemon={p}
                selecionado={
                  selecionado?.nomeCompleto
                    ? selecionado.nomeCompleto === p.nomeCompleto
                    : selecionado?.nome === p.nome && selecionado?.id === p.id
                }
                onClick={setSelecionado}
              />
            ))
          )}
          {/* AQUI FOI ALTERADO DE 36 PARA 30 */}
          {pokemonsDaCaixa.length > 0 && Array.from({ length: Math.max(0, 30 - pokemonsDaCaixa.length) }).map((_, i) => (
            <div key={`vazio-${i}`} style={{
              width: '100px', height: '100px',
              background: 'rgba(0,0,0,0.15)', border: '3px solid rgba(0,0,0,0.2)',
            }} />
          ))}
        </div>
      </div>
    </div>
  )
}

function PokemonMiniCard({ nome, label }) {
  const nomeBase = nome?.split('|')[0] || nome
  return (
    <div style={{
      display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '4px',
    }}>
      <span style={{ fontFamily: 'var(--font)', fontSize: '5px', color: '#666', textTransform: 'uppercase' }}>{label}</span>
      <span style={{ fontFamily: 'var(--font)', fontSize: '7px', color: '#181818' }}>{nomeBase}</span>
    </div>
  )
}

function NavBtn({ children, onClick, disabled }) {
  return (
    <button onClick={onClick} disabled={disabled} style={{
      background: disabled ? '#A8A8A8' : '#D8D8D8', 
      border: '3px solid #888888', borderBottom: '4px solid #555555',
      fontFamily: 'var(--font)', fontSize: '10px', 
      color: disabled ? '#888888' : '#181818',
      width: '32px', height: '28px', 
      cursor: disabled ? 'not-allowed' : 'pointer',
      display: 'flex', alignItems: 'center', justifyContent: 'center', padding: 0,
      opacity: disabled ? 0.6 : 1,
    }}>{children}</button>
  )
}